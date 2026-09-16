cat << 'EOF' > ~/Tinklet/server.js
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

// ==========================================
// BLOCK 0: CORE SETUP & CONFIG
// ==========================================
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

// ==========================================
// BLOCK 1: SECURITY & HELPERS
// ==========================================
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

// ==========================================
// BLOCK 2: AUTHENTICATION SUBDIVISIONS
// ==========================================

// 2A: SIGNUP API (Full Mirroring)
app.post('/api/auth/signup', async (req, res) => {
    try {
        const email = req.body.email.trim().toLowerCase();
        const newUser = { ...req.body, email, userId: uuidv4(), coins: 25, interactions: {}, createdAt: new Date().toISOString() };
        await ddb.send(new PutCommand({ TableName: TABLES.USERS, Item: newUser }));
        res.json({ token: jwt.sign({ email }, JWT_SECRET), user: newUser });
    } catch (e) { res.status(500).json({ error: e.message }); }
});

// 2B: LOGIN API (Auto-Recovery)
app.post('/api/auth/login', async (req, res) => {
    try {
        const email = req.body.email.trim().toLowerCase();
        const result = await ddb.send(new GetCommand({ TableName: TABLES.USERS, Key: { email } }));
        if (!result.Item || req.body.password.trim() !== result.Item.password) return res.status(401).json({ error: "Fail" });
        if (result.Item.isDeactivated || result.Item.deletionRequestedAt) {
            await ddb.send(new UpdateCommand({ TableName: TABLES.USERS, Key: { email }, UpdateExpression: "SET isDeactivated = :f REMOVE deletionRequestedAt", ExpressionAttributeValues: { ":f": false } }));
            try { await ddb.send(new DeleteCommand({ TableName: TABLES.DELETION, Key: { email } })); } catch(err) {}
        }
        res.json({ token: jwt.sign({ email: result.Item.email }, JWT_SECRET), user: result.Item });
    } catch (e) { res.status(500).json({ error: e.message }); }
});

// ==========================================
// BLOCK 3: DISCOVERY & SYNC SUBDIVISIONS
// ==========================================

// 3A: DISCOVERY FEED API
app.get('/api/swipe/feed', authenticateToken, async (req, res) => {
    try {
        const myEmail = req.user.email;
        const [allUsers, me] = await Promise.all([ ddb.send(new ScanCommand({ TableName: TABLES.USERS })), ddb.send(new GetCommand({ TableName: TABLES.USERS, Key: { email: myEmail } })) ]);
        const swiped = Object.keys(me.Item?.interactions || {}).map(e => e.toLowerCase().trim());
        const feed = (allUsers.Items || []).filter(u => u.email && u.email.toLowerCase().trim() !== myEmail && !swiped.includes(u.email.toLowerCase().trim()) && u.isDeactivated !== true).map(u => ({ ...u, photoUri: u.photoUri || u.photoUrl || "" }));
        res.json({ feed });
    } catch (e) { res.status(500).json({ error: e.message }); }
});

// 3B: SYNC ALL API (Full Inbox Sync)
app.get('/api/sync/all', authenticateToken, async (req, res) => {
    try {
        const email = req.user.email;
        const allUsers = await ddb.send(new ScanCommand({ TableName: TABLES.USERS }));
        const profileMap = {}; allUsers.Items.forEach(u => { if(u.email) profileMap[u.email.toLowerCase()] = { ...u, photoUri: u.photoUri || u.photoUrl || "" }; });
        const sent = await ddb.send(new QueryCommand({ TableName: TABLES.LIKES, KeyConditionExpression: "fromUserId = :me", ExpressionAttributeValues: { ":me": email } }));
        const incoming = await ddb.send(new QueryCommand({ TableName: TABLES.LIKES, IndexName: "toUserId-index", KeyConditionExpression: "toUserId = :me", ExpressionAttributeValues: { ":me": email } }));
        const matches = await ddb.send(new ScanCommand({ TableName: TABLES.MATCHES, FilterExpression: "contains(#u, :me)", ExpressionAttributeNames: { "#u": "users" }, ExpressionAttributeValues: { ":me": email } }));
        res.json({ user: profileMap[email], sent: (sent.Items || []).map(l => ({ ...profileMap[l.toUserId.toLowerCase()], connectionStatus: l.action })).filter(p => p.email), incoming: (incoming.Items || []).map(l => ({ ...profileMap[l.fromUserId.toLowerCase()], connectionStatus: "PENDING" })).filter(p => p.email), matches: (matches.Items || []).map(m => ({ ...profileMap[m.users.find(u => u.toLowerCase() !== email).toLowerCase()], connectionStatus: "ACCEPTED" })).filter(p => p.email) });
    } catch (e) { res.status(500).json({ error: e.message }); }
});

// ==========================================
// BLOCK 4: ACTION SUBDIVISIONS
// ==========================================

// 4A: DISCOVERY FEED API (STABLE)
app.get('/api/swipe/feed', authenticateToken, async (req, res) => {
    try {
        const myEmail = req.user.email;

        const [allUsers, meRes] = await Promise.all([
            ddb.send(new ScanCommand({
                TableName: TABLES.USERS
            })),
            ddb.send(new GetCommand({
                TableName: TABLES.USERS,
                Key: { email: myEmail }
            }))
        ]);

        const me = meRes.Item || {};
        const myInteractions = me.interactions || {};

        const swiped = Object.keys(myInteractions)
            .map(e => e.toLowerCase().trim());

        const feed = (allUsers.Items || [])
            .filter(u => {
                if (!u.email) return false;

                const target = u.email.toLowerCase().trim();

                return (
                    target !== myEmail &&
                    !swiped.includes(target) &&
                    u.isDeactivated !== true
                );
            })
            .map(u => ({
                ...u,
                photoUri: u.photoUri || u.photoUrl || ""
            }));

        res.json({ feed });

    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

// ==========================================
// BLOCK 4B: SWIPE ACTIONS (ROBUST COINS)
// ==========================================
app.post('/api/swipe/action', authenticateToken, async (req, res, next) => {
    const action = (req.body.action || "").toUpperCase();
    if (action === 'ACCEPT') return next(); 
    try {
        const fromEmail = req.user.email;
        const toUserId = (req.body.toUserId || "").trim().toLowerCase();
        const cost = action === 'SUPERLIKE' ? 10 : (['REJECTED', 'LIKE'].includes(action) ? 1 : 0);

        // 1. DEDUCT COINS (Atomic)
        const coinUpdate = await ddb.send(new UpdateCommand({
            TableName: TABLES.USERS, Key: { email: fromEmail },
            UpdateExpression: "SET coins = if_not_exists(coins, :start) - :cost",
            ConditionExpression: "if_not_exists(coins, :start) >= :cost",
            ExpressionAttributeValues: { ":cost": cost, ":start": 25 },
            ReturnValues: "ALL_NEW"
        }));

        // 2. UPDATE INTERACTIONS (Separate for safety)
        const updateExpr = action !== 'CANCEL' ? "SET interactions = if_not_exists(interactions, :empty), interactions.#target = :act" : "REMOVE interactions.#target";
        await ddb.send(new UpdateCommand({
            TableName: TABLES.USERS, Key: { email: fromEmail },
            UpdateExpression: updateExpr,
            ExpressionAttributeNames: { "#target": toUserId },
            ExpressionAttributeValues: action !== 'CANCEL' ? { ":empty": {}, ":act": action } : undefined
        }));

        // 3. LOG TO LIKES TABLE
        if (action !== 'CANCEL') await ddb.send(new PutCommand({ TableName: TABLES.LIKES, Item: { fromUserId: fromEmail, toUserId, action, timestamp: Date.now() } }));
        else await ddb.send(new DeleteCommand({ TableName: TABLES.LIKES, Key: { fromUserId: fromEmail, toUserId } }));

        res.json({ success: true, coins: coinUpdate.Attributes.coins });
    } catch (e) {
        console.error("[SWIPE FAIL]:", e.message);
        if (e.name === "ConditionalCheckFailedException") return res.status(400).json({ error: "Insufficient coins" });
        res.status(500).json({ error: "Server technical error" });
    }
});

// ==========================================
// BLOCK 5: ECONOMY SUBDIVISIONS
// ==========================================

// 5A: AD REWARD API (+5)
app.post('/api/coins/reward', authenticateToken, async (req, res) => {
    try {
        const update = await ddb.send(new UpdateCommand({ TableName: TABLES.USERS, Key: { email: req.user.email }, UpdateExpression: "SET coins = if_not_exists(coins, :start) + :r", ExpressionAttributeValues: { ":r": 5, ":start": 25 }, ReturnValues: "ALL_NEW" }));
        res.json({ success: true, coins: update.Attributes.coins });
    } catch (e) { res.status(500).json({ error: e.message }); }
});

// 5B: REFERRAL API (+50)
app.post('/api/referral/credit', authenticateToken, async (req, res) => {
    try {
        const { code } = req.body;
        const all = await ddb.send(new ScanCommand({ TableName: TABLES.USERS }));
        const referrer = all.Items.find(u => u.referralCode === code);
        if (referrer) { await ddb.send(new UpdateCommand({ TableName: TABLES.USERS, Key: { email: referrer.email }, UpdateExpression: "SET coins = if_not_exists(coins, :start) + :b", ExpressionAttributeValues: { ":b": 50, ":start": 25 } })); res.json({ success: true }); }
        else { res.status(404).json({ error: "Invalid code" }); }
    } catch (e) { res.status(500).json({ error: e.message }); }
});

// 5C: LEADERBOARD API (RICH LIST)
app.get('/api/swipe/leaderboard', async (req, res) => {
    try {
        const result = await ddb.send(new ScanCommand({ TableName: TABLES.USERS }));
        const list = (result.Items || []).sort((a, b) => (b.coins || 0) - (a.coins || 0)).map((u, i) => ({ rank: i + 1, email: u.email, name: u.name, coins: u.coins || 0, photoUri: u.photoUri || u.photoUrl || "", badgeType: u.badgeType || "NONE" }));
        res.json({ leaderboard: list });
    } catch (e) { res.status(500).json({ error: e.message }); }
});

// ==========================================
// BLOCK 6: ACCOUNT SAFETY SUBDIVISIONS
// ==========================================
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

// ==========================================
// BLOCK 7: MEDIA & PROFILE SUBDIVISIONS
// ==========================================
app.post('/profile/save', authenticateToken, async (req, res) => {
    try { await ddb.send(new PutCommand({ TableName: TABLES.USERS, Item: req.body })); res.json({ success: true }); }
    catch (e) { res.status(500).json({ error: e.message }); }
});
app.post('/image/upload', async (req, res) => {
    try {
        const data = require('qs').stringify({ image: req.body.base64Image.split(',')[1] || req.body.base64Image });
        const resp = await require('axios').post(`https://api.imgbb.com/1/upload?key=${IMGBB_KEY}`, data);
        res.json({ url: resp.data.data.url });
    } catch (e) { res.status(500).json({ error: "Upload fail" }); }
});

// ==========================================
// BLOCK 8: WHATSAPP BOT (PRESERVED)
// ==========================================
async function startBot() {
    try {
        const { state, saveCreds } = await useMultiFileAuthState(__dirname + '/auth_info');
        const sock = makeWASocket({ auth: state, logger: pino({ level: 'silent' }), printQRInTerminal: false });
        sock.ev.on('creds.update', saveCreds);
        sock.ev.on('connection.update', (u) => { if (u.connection === 'open') console.log('✅ Bot Alive!'); });
    } catch (e) { console.error("WA Bot Error:", e.message); }
}

// ==========================================
// BLOCK 9: SYSTEM START
// ==========================================
startBot();
masterServer.listen(4000, () => console.log('🚀 ULTIMATE SUBDIVIDED SERVER READY ON PORT 4000'));
EOF
