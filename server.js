require('dotenv').config({ path: __dirname + '/.env' });
const express = require('express');
const http = require('http');
const { Server } = require("socket.io");
const { DynamoDBClient } = require("@aws-sdk/client-dynamodb");
const { DynamoDBDocumentClient, PutCommand, GetCommand, ScanCommand, QueryCommand, DeleteCommand, UpdateCommand } = require("@aws-sdk/lib-dynamodb");
const cors = require("cors");
const bodyParser = require("body-parser");
const jwt = require('jsonwebtoken');
const crypto = require("crypto");
const { v4: uuidv4 } = require('uuid');
const axios = require('axios');
const qs = require('qs');
const { default: makeWASocket, useMultiFileAuthState } = require("@whiskeysockets/baileys");
const pino = require('pino');

const app = express();
app.use(cors());
app.use(bodyParser.json({ limit: '50mb' }));
const masterServer = http.createServer(app);
const io = new Server(masterServer, { cors: { origin: "*" } });
const client = new DynamoDBClient({ region: "ap-south-1", credentials: { accessKeyId: process.env.AWS_ACCESS_KEY_ID, secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY } });
const ddb = DynamoDBDocumentClient.from(client);
const JWT_SECRET = process.env.JWT_SECRET || 'tinklet_secret_2026';
const IMGBB_KEY = process.env.IMGBB_KEY;
const TABLES = { USERS: "Profiles", LIKES: "Likes", MATCHES: "Matches", REPORTS: "Reports", DELETION: "DeletionRequests" };

function authenticateToken(req, res, next) {
    const authHeader = req.headers['authorization'];
    const token = authHeader && authHeader.split(' ')[1];
    if (!token) return res.status(401).json({ error: "Fail" });
    jwt.verify(token, JWT_SECRET, (err, user) => {
        if (err) return res.status(403).json({ error: "Fail" });
        req.user = { email: (user.email || user).trim().toLowerCase() };
        next();
    });
}
function createMatchId(u1, u2) { return crypto.createHash("sha256").update([u1.toLowerCase(), u2.toLowerCase()].sort().join(":")).digest("hex").substring(0, 32); }

app.post('/api/auth/signup', async (req, res) => {
    try {
        const email = req.body.email.trim().toLowerCase();
        const newUser = { ...req.body, email, userId: uuidv4(), coins: 25, coinInitializedV2: true, interactions: {}, createdAt: new Date().toISOString() };
        await ddb.send(new PutCommand({ TableName: TABLES.USERS, Item: newUser }));
        res.json({ token: jwt.sign({ email }, JWT_SECRET), user: newUser });
    } catch (e) { res.status(500).json({ error: e.message }); }
});

app.post('/api/auth/login', async (req, res) => {
    try {
        const email = req.body.email.trim().toLowerCase();
        const result = await ddb.send(new GetCommand({ TableName: TABLES.USERS, Key: { email } }));
        if (!result.Item || req.body.password.trim() !== result.Item.password) return res.status(401).json({ error: "Fail" });
        if (result.Item.isDeactivated || result.Item.deletionRequestedAt) {
            await ddb.send(new UpdateCommand({ TableName: TABLES.USERS, Key: { email }, UpdateExpression: "SET isDeactivated = :f REMOVE deletionRequestedAt", ExpressionAttributeValues: { ":f": false } }));
            try { await ddb.send(new DeleteCommand({ TableName: TABLES.DELETION, Key: { email } })); } catch(err) {}
        }

        // One-time migration for older accounts created before the 25-coin rule.
        // New accounts are marked at signup, so their legitimate zero balance is never reset.
        if (result.Item.coinInitializedV2 !== true) {
            await ddb.send(new UpdateCommand({
                TableName: TABLES.USERS,
                Key: { email },
                UpdateExpression: "SET coins = :coins, coinInitializedV2 = :v",
                ExpressionAttributeValues: { ":coins": 25, ":v": true }
            }));
            result.Item.coins = 25;
            result.Item.coinInitializedV2 = true;
        } else if (result.Item.coins === undefined || result.Item.coins === null || Number.isNaN(Number(result.Item.coins))) {
            await ddb.send(new UpdateCommand({
                TableName: TABLES.USERS,
                Key: { email },
                UpdateExpression: "SET coins = :coins",
                ExpressionAttributeValues: { ":coins": 25 }
            }));
            result.Item.coins = 25;
        }
        res.json({ token: jwt.sign({ email: result.Item.email }, JWT_SECRET), user: result.Item });
    } catch (e) { res.status(500).json({ error: e.message }); }
});

app.get('/api/sync/all', authenticateToken, async (req, res) => {
    try {
        const email = req.user.email;
        const allUsers = await ddb.send(new ScanCommand({ TableName: TABLES.USERS }));
        const profileMap = {}; allUsers.Items.forEach(u => { if(u.email) profileMap[u.email.toLowerCase()] = { ...u, photoUri: u.photoUri || u.photoUrl || "" }; });
        const sent = await ddb.send(new QueryCommand({ TableName: TABLES.LIKES, KeyConditionExpression: "fromUserId = :me", ExpressionAttributeValues: { ":me": email } }));
        const incoming = await ddb.send(new QueryCommand({ TableName: TABLES.LIKES, IndexName: "toUserId-index", KeyConditionExpression: "toUserId = :me", ExpressionAttributeValues: { ":me": email } }));
        const matches = await ddb.send(new ScanCommand({ TableName: TABLES.MATCHES, FilterExpression: "contains(#u, :me)", ExpressionAttributeNames: { "#u": "users" }, ExpressionAttributeValues: { ":me": email } }));
        res.json({
    user: profileMap[email],
    sent: (sent.Items || []).map(l => ({ fromUserId: l.fromUserId, toUserId: l.toUserId, action: l.action, timestamp: l.timestamp || 0 })),
    incoming: (incoming.Items || []).map(l => ({ fromUserId: l.fromUserId, toUserId: l.toUserId, action: l.action, timestamp: l.timestamp || 0 })),
    matches: (matches.Items || [])
});
    } catch (e) { res.status(500).json({ error: e.message }); }
});

app.get('/api/swipe/feed', authenticateToken, async (req, res) => {
    try {
        const myEmail = req.user.email;
        const [allUsers, meRes] = await Promise.all([
            ddb.send(new ScanCommand({ TableName: TABLES.USERS })),
            ddb.send(new GetCommand({ TableName: TABLES.USERS, Key: { email: myEmail } }))
        ]);
        const me = meRes.Item || {};
        const myInteractions = me.interactions || {};
        const swiped = Object.keys(myInteractions).map(e => e.toLowerCase().trim());
        const feed = (allUsers.Items || []).filter(u => {
            if (!u.email) return false;
            const target = u.email.toLowerCase().trim();
            return target !== myEmail && !swiped.includes(target) && u.isDeactivated !== true;
        }).map(u => ({ ...u, photoUri: u.photoUri || u.photoUrl || "" }));
        res.json({ feed });
    } catch (e) { res.status(500).json({ error: e.message }); }
});

app.post('/api/swipe/action', authenticateToken, async (req, res, next) => {
    const action = (req.body.action || "").toUpperCase();
    if (action === 'ACCEPT') return next();
    try {
        const fromEmail = req.user.email;
        const toUserId = (req.body.toUserId || "").trim().toLowerCase();
        const cost = action === 'SUPERLIKE' ? 10 : (['REJECTED', 'LIKE'].includes(action) ? 1 : 0);
        if (cost > 0) {
            await ddb.send(new UpdateCommand({ TableName: TABLES.USERS, Key: { email: fromEmail }, UpdateExpression: "SET coins = if_not_exists(coins, :start)", ExpressionAttributeValues: { ":start": 25 } }));
        }
        const coinUpdate = await ddb.send(new UpdateCommand({ TableName: TABLES.USERS, Key: { email: fromEmail }, UpdateExpression: "SET coins = coins - :cost", ConditionExpression: "coins >= :cost", ExpressionAttributeValues: { ":cost": cost }, ReturnValues: "ALL_NEW" }));
        const updateExpr = action !== 'CANCEL' ? "SET interactions = if_not_exists(interactions, :empty), interactions.#target = :act" : "REMOVE interactions.#target";
        await ddb.send(new UpdateCommand({ TableName: TABLES.USERS, Key: { email: fromEmail }, UpdateExpression: updateExpr, ExpressionAttributeNames: { "#target": toUserId }, ExpressionAttributeValues: action !== 'CANCEL' ? { ":empty": {}, ":act": action } : undefined }));
        if (action !== 'CANCEL') await ddb.send(new PutCommand({ TableName: TABLES.LIKES, Item: { fromUserId: fromEmail, toUserId, action, timestamp: Date.now() } }));
        else await ddb.send(new DeleteCommand({ TableName: TABLES.LIKES, Key: { fromUserId: fromEmail, toUserId } }));
        res.json({ success: true, coins: coinUpdate.Attributes.coins });
    } catch (e) {
        console.error("[SWIPE FAIL]:", e.message);
        if (e.name === "ConditionalCheckFailedException") return res.status(400).json({ error: "Insufficient coins" });
        res.status(500).json({ error: "Server technical error" });
    }
});

app.post('/api/coins/reward', authenticateToken, async (req, res) => {
    try { const update = await ddb.send(new UpdateCommand({ TableName: TABLES.USERS, Key: { email: req.user.email }, UpdateExpression: "SET coins = if_not_exists(coins, :start) + :r", ExpressionAttributeValues: { ":r": 5, ":start": 25 }, ReturnValues: "ALL_NEW" })); res.json({ success: true, coins: update.Attributes.coins }); }
    catch (e) { res.status(500).json({ error: e.message }); }
});
app.post('/api/referral/credit', authenticateToken, async (req, res) => {
    try { const { code } = req.body; const all = await ddb.send(new ScanCommand({ TableName: TABLES.USERS })); const referrer = all.Items.find(u => u.referralCode === code); if (referrer) { await ddb.send(new UpdateCommand({ TableName: TABLES.USERS, Key: { email: referrer.email }, UpdateExpression: "SET coins = if_not_exists(coins, :start) + :b", ExpressionAttributeValues: { ":b": 50, ":start": 25 } })); res.json({ success: true }); } else { res.status(404).json({ error: "Invalid code" }); } }
    catch (e) { res.status(500).json({ error: e.message }); }
});
app.get('/api/swipe/leaderboard', async (req, res) => {
    try { const result = await ddb.send(new ScanCommand({ TableName: TABLES.USERS })); const list = (result.Items || []).sort((a, b) => (b.coins || 0) - (a.coins || 0)).map((u, i) => ({ rank: i + 1, email: u.email, name: u.name, coins: u.coins || 0, photoUri: u.photoUri || u.photoUrl || "", badgeType: u.badgeType || "NONE" })); res.json({ leaderboard: list }); }
    catch (e) { res.status(500).json({ error: e.message }); }
});
app.post('/api/account/deactivate', authenticateToken, async (req, res) => {
    try { await ddb.send(new UpdateCommand({ TableName: TABLES.USERS, Key: { email: req.user.email }, UpdateExpression: "SET isDeactivated = :v", ExpressionAttributeValues: { ":v": true } })); res.json({ success: true }); }
    catch (e) { res.status(500).json({ error: e.message }); }
});
app.post('/api/account/delete-request', authenticateToken, async (req, res) => {
    try { await ddb.send(new PutCommand({ TableName: TABLES.DELETION, Item: { email: req.user.email, requestedAt: Date.now(), status: "PENDING" } })); await ddb.send(new UpdateCommand({ TableName: TABLES.USERS, Key: { email: req.user.email }, UpdateExpression: "SET deletionRequestedAt = :t", ExpressionAttributeValues: { ":t": Date.now() } })); res.json({ success: true }); }
    catch (e) { res.status(500).json({ error: e.message }); }
});
app.post('/api/report', authenticateToken, async (req, res) => {
    try { await ddb.send(new PutCommand({ TableName: TABLES.REPORTS, Item: { reportId: uuidv4(), reporter: req.user.email, target: req.body.targetEmail, reason: req.body.reason, timestamp: Date.now() } })); res.json({ success: true }); }
    catch (e) { res.status(500).json({ error: e.message }); }
});

// Save profile fields without replacing the whole DynamoDB user record.
// This preserves coins, password, interactions and other server-owned fields.
app.post('/profile/save', authenticateToken, async (req, res) => {
    try {
        const email = req.user.email;
        const allowed = { ...req.body };
        delete allowed.email;
        delete allowed.coins;
        delete allowed.password;
        delete allowed.userId;
        delete allowed.interactions;
        delete allowed.coinInitializedV2;
        const names = {};
        const values = {};
        const sets = [];
        Object.entries(allowed).forEach(([key, value], index) => {
            if (key === 'email' || value === undefined) return;
            const nk = `#p${index}`;
            const vk = `:p${index}`;
            names[nk] = key;
            values[vk] = value;
            sets.push(`${nk} = ${vk}`);
        });
        if (sets.length === 0) return res.json({ success: true });
        await ddb.send(new UpdateCommand({
            TableName: TABLES.USERS,
            Key: { email },
            UpdateExpression: `SET ${sets.join(', ')}`,
            ExpressionAttributeNames: names,
            ExpressionAttributeValues: values
        }));
        res.json({ success: true });
    } catch (e) { res.status(500).json({ error: e.message }); }
});
app.post('/image/upload', async (req, res) => {
    try { const data = require('qs').stringify({ image: req.body.base64Image.split(',')[1] || req.body.base64Image }); const resp = await require('axios').post(`https://api.imgbb.com/1/upload?key=${IMGBB_KEY}`, data); res.json({ url: resp.data.data.url }); }
    catch (e) { res.status(500).json({ error: "Upload fail" }); }
});
async function startBot() {
    try { const { state, saveCreds } = await useMultiFileAuthState(__dirname + '/auth_info'); const sock = makeWASocket({ auth: state, logger: pino({ level: 'silent' }), printQRInTerminal: false }); sock.ev.on('creds.update', saveCreds); sock.ev.on('connection.update', (u) => { if (u.connection === 'open') console.log('✅ Bot Alive!'); }); }
    catch (e) { console.error("WA Bot Error:", e.message); }
}
startBot();
masterServer.listen(4000, () => console.log('🚀 ULTIMATE SUBDIVIDED SERVER READY ON PORT 4000'));
