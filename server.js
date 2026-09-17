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

async function setInteractionStatus(email, targetEmail, status) {
    const user = await ddb.send(new GetCommand({
        TableName: TABLES.USERS,
        Key: { email }
    }));
    const interactions = user.Item?.interactions;

    if (interactions && typeof interactions === "object") {
        await ddb.send(new UpdateCommand({
            TableName: TABLES.USERS,
            Key: { email },
            UpdateExpression: "SET interactions.#target = :status",
            ExpressionAttributeNames: { "#target": targetEmail },
            ExpressionAttributeValues: { ":status": status }
        }));
    } else {
        await ddb.send(new UpdateCommand({
            TableName: TABLES.USERS,
            Key: { email },
            UpdateExpression: "SET interactions = :interactions",
            ExpressionAttributeValues: {
                ":interactions": { [targetEmail]: status }
            }
        }));
    }
}

app.post('/upload-image-secure', async (req, res) => {
    try {
        if (!IMGBB_KEY) {
            return res.status(500).json({ error: "Image upload is not configured" });
        }

        const base64 = String(req.body.base64 || "").trim();

        if (!base64) {
            return res.status(400).json({ error: "Image data missing" });
        }

        const cleanBase64 = base64.replace(/^data:image\/\w+;base64,/, "");

        const formData = new URLSearchParams();
        formData.append("key", IMGBB_KEY);
        formData.append("image", cleanBase64);

        const response = await axios.post(
            "https://api.imgbb.com/1/upload",
            formData.toString(),
            {
                headers: {
                    "Content-Type": "application/x-www-form-urlencoded"
                },
                timeout: 120000
            }
        );

        const imageUrl = response.data?.data?.url;

        if (!imageUrl) {
            return res.status(502).json({ error: "Image URL missing" });
        }

        return res.json({ url: imageUrl });

    } catch (e) {
        console.error("[IMAGE UPLOAD]", e.response?.data || e.message);
        return res.status(500).json({ error: "Image upload failed" });
    }
});

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
        if (result.Item.coinInitializedV2 !== true) {
            await ddb.send(new UpdateCommand({ TableName: TABLES.USERS, Key: { email }, UpdateExpression: "SET coins = :coins, coinInitializedV2 = :v", ExpressionAttributeValues: { ":coins": 25, ":v": true } }));
            result.Item.coins = 25; result.Item.coinInitializedV2 = true;
        } else if (result.Item.coins === undefined || result.Item.coins === null || Number.isNaN(Number(result.Item.coins))) {
            await ddb.send(new UpdateCommand({ TableName: TABLES.USERS, Key: { email }, UpdateExpression: "SET coins = :coins", ExpressionAttributeValues: { ":coins": 25 } }));
            result.Item.coins = 25;
        }
        res.json({ token: jwt.sign({ email: result.Item.email }, JWT_SECRET), user: result.Item });
    } catch (e) { res.status(500).json({ error: e.message }); }
});

app.get('/api/sync/all', authenticateToken, async (req, res) => {
    try {
        const email = req.user.email;
        const allUsers = await ddb.send(new ScanCommand({ TableName: TABLES.USERS }));
        const profileMap = {};
        (allUsers.Items || []).forEach(u => { if (u.email) profileMap[u.email.toLowerCase()] = { ...u, photoUri: u.photoUri || u.photoUrl || "" }; });
        const sent = await ddb.send(new QueryCommand({ TableName: TABLES.LIKES, KeyConditionExpression: "fromUserId = :me", ExpressionAttributeValues: { ":me": email } }));

        let incomingItems = [];
        try {
            const incomingGsi = await ddb.send(new QueryCommand({ TableName: TABLES.LIKES, IndexName: "toUserId-index", KeyConditionExpression: "toUserId = :me", ExpressionAttributeValues: { ":me": email } }));
            incomingItems = incomingGsi.Items || [];
        } catch (e) { console.error("[SYNC] Incoming GSI query failed:", e.message); }
        const allLikes = await ddb.send(new ScanCommand({ TableName: TABLES.LIKES }));
        const scannedIncoming = (allLikes.Items || []).filter(l => String(l.toUserId || "").trim().toLowerCase() === email);
        const incomingMap = new Map();
        [...incomingItems, ...scannedIncoming].forEach(l => incomingMap.set(`${String(l.fromUserId || "").toLowerCase()}::${String(l.toUserId || "").toLowerCase()}`, l));
        const incoming = [...incomingMap.values()].filter(l => ["LIKE", "SUPERLIKE"].includes(String(l.action || "").toUpperCase()));

        const matches = await ddb.send(new ScanCommand({ TableName: TABLES.MATCHES, FilterExpression: "contains(#u, :me)", ExpressionAttributeNames: { "#u": "users" }, ExpressionAttributeValues: { ":me": email } }));
        res.json({
            user: profileMap[email],
            sent: (sent.Items || []).map(l => ({ fromUserId: l.fromUserId, toUserId: l.toUserId, action: l.action, timestamp: l.timestamp || 0 })),
            incoming: incoming.map(l => ({ fromUserId: l.fromUserId, toUserId: l.toUserId, action: l.action, timestamp: l.timestamp || 0 })),
            matches: (matches.Items || [])
        });
    } catch (e) { res.status(500).json({ error: e.message }); }
});

app.get('/api/swipe/feed', authenticateToken, async (req, res) => {
    try {
        const myEmail = req.user.email;
        const [allUsers, meRes] = await Promise.all([ddb.send(new ScanCommand({ TableName: TABLES.USERS })), ddb.send(new GetCommand({ TableName: TABLES.USERS, Key: { email: myEmail } }))]);
        const me = meRes.Item || {};
        const swiped = Object.keys(me.interactions || {}).map(e => e.toLowerCase().trim());
        const feed = (allUsers.Items || []).filter(u => {
            if (!u.email) return false;
            const target = u.email.toLowerCase().trim();
            return target !== myEmail && !swiped.includes(target) && u.isDeactivated !== true;
        }).map(u => ({ ...u, photoUri: u.photoUri || u.photoUrl || "" }));
        res.json({ feed });
    } catch (e) { res.status(500).json({ error: e.message }); }
});

app.post('/api/swipe/action', authenticateToken, async (req, res) => {
    const action = String(req.body.action || "").trim().toUpperCase();
    try {
        const fromEmail = req.user.email.trim().toLowerCase();
        const toUserId = String(req.body.toUserId || "").trim().toLowerCase();
        if (!toUserId || toUserId === fromEmail) return res.status(400).json({ error: "Invalid target" });

        // ACCEPT never costs coins. It accepts only a real incoming LIKE/SUPERLIKE.
        if (action === "ACCEPT") {
            const incoming = await ddb.send(new GetCommand({
                TableName: TABLES.LIKES,
                Key: { fromUserId: toUserId, toUserId: fromEmail }
            }));
            const incomingAction = String(incoming.Item?.action || "").toUpperCase();

            if (!["LIKE", "SUPERLIKE"].includes(incomingAction)) {
                return res.status(400).json({ error: "Request not found" });
            }

            const matchId = createMatchId(fromEmail, toUserId);
            const now = Date.now();

            await ddb.send(new PutCommand({
                TableName: TABLES.MATCHES,
                Item: { matchId, users: [fromEmail, toUserId], timestamp: now }
            }));

            await setInteractionStatus(fromEmail, toUserId, "ACCEPTED");
            await setInteractionStatus(toUserId, fromEmail, "ACCEPTED");

            await ddb.send(new PutCommand({
                TableName: TABLES.LIKES,
                Item: {
                    fromUserId: toUserId,
                    toUserId: fromEmail,
                    action: "ACCEPTED",
                    previousAction: incomingAction,
                    timestamp: now
                }
            }));

            await ddb.send(new PutCommand({
                TableName: TABLES.LIKES,
                Item: {
                    fromUserId: fromEmail,
                    toUserId,
                    action: "ACCEPTED",
                    previousAction: incomingAction,
                    timestamp: now
                }
            }));

            const meAfterAccept = await ddb.send(new GetCommand({
                TableName: TABLES.USERS,
                Key: { email: fromEmail }
            }));

            return res.json({
                success: true,
                matched: true,
                matchId,
                coins: Number(meAfterAccept.Item?.coins || 0)
            });
        }

        // LIKE / SUPERLIKE: if the other user already liked us, create a match immediately.
        if (action === "LIKE" || action === "SUPERLIKE") {
            const reverse = await ddb.send(new GetCommand({
                TableName: TABLES.LIKES,
                Key: { fromUserId: toUserId, toUserId: fromEmail }
            }));
            const reverseAction = String(reverse.Item?.action || "").toUpperCase();

            if (["LIKE", "SUPERLIKE"].includes(reverseAction)) {
                const matchId = createMatchId(fromEmail, toUserId);
                const now = Date.now();

                await ddb.send(new PutCommand({
                    TableName: TABLES.MATCHES,
                    Item: { matchId, users: [fromEmail, toUserId], timestamp: now }
                }));

                await setInteractionStatus(fromEmail, toUserId, "ACCEPTED");
                await setInteractionStatus(toUserId, fromEmail, "ACCEPTED");

                await ddb.send(new PutCommand({
                    TableName: TABLES.LIKES,
                    Item: {
                        fromUserId: fromEmail,
                        toUserId,
                        action: "ACCEPTED",
                        timestamp: now,
                        previousAction: action
                    }
                }));

                await ddb.send(new PutCommand({
                    TableName: TABLES.LIKES,
                    Item: {
                        fromUserId: toUserId,
                        toUserId: fromEmail,
                        action: "ACCEPTED",
                        timestamp: now,
                        previousAction: reverseAction
                    }
                }));

                const meAfterMatch = await ddb.send(new GetCommand({
                    TableName: TABLES.USERS,
                    Key: { email: fromEmail }
                }));

                return res.json({
                    success: true,
                    matched: true,
                    matchId,
                    coins: Number(meAfterMatch.Item?.coins || 0)
                });
            }
        }

        // LIKE = 1, SUPERLIKE = 10, REJECTED = 1. ACCEPT = 0 (handled above).
        const cost = action === "SUPERLIKE" ? 10 : (["REJECTED", "LIKE"].includes(action) ? 1 : 0);

        let coinUpdate;
        if (cost > 0) {
            coinUpdate = await ddb.send(new UpdateCommand({
                TableName: TABLES.USERS,
                Key: { email: fromEmail },
                UpdateExpression: "SET coins = if_not_exists(coins, :start) - :cost",
                ConditionExpression: "attribute_not_exists(coins) OR coins >= :cost",
                ExpressionAttributeValues: { ":start": 25, ":cost": cost },
                ReturnValues: "ALL_NEW"
            }));
        } else {
            const currentUser = await ddb.send(new GetCommand({
                TableName: TABLES.USERS,
                Key: { email: fromEmail }
            }));
            coinUpdate = { Attributes: { ...(currentUser.Item || {}), coins: Number(currentUser.Item?.coins || 0) } };
        }

        await ddb.send(new UpdateCommand({
            TableName: TABLES.USERS,
            Key: { email: fromEmail },
            UpdateExpression: "SET interactions = if_not_exists(interactions, :empty)",
            ExpressionAttributeValues: { ":empty": {} }
        }));

        if (action === "CANCEL") {
            const current = await ddb.send(new GetCommand({ TableName: TABLES.LIKES, Key: { fromUserId: fromEmail, toUserId } }));
            const currentAction = String(current.Item?.action || "").toUpperCase();
            if (currentAction === "REJECTED") {
                const reverse = await ddb.send(new GetCommand({ TableName: TABLES.LIKES, Key: { fromUserId: toUserId, toUserId: fromEmail } }));
                if (String(reverse.Item?.action || "").toUpperCase() === "REJECTED_BY_RECEIVER") {
                    const restored = reverse.Item?.previousAction || "LIKE";
                    await ddb.send(new PutCommand({ TableName: TABLES.LIKES, Item: { fromUserId: toUserId, toUserId: fromEmail, action: restored, timestamp: Date.now() } }));
                }
            }
            await ddb.send(new UpdateCommand({ TableName: TABLES.USERS, Key: { email: fromEmail }, UpdateExpression: "REMOVE interactions.#target", ExpressionAttributeNames: { "#target": toUserId } }));
            await ddb.send(new DeleteCommand({ TableName: TABLES.LIKES, Key: { fromUserId: fromEmail, toUserId } }));
            return res.json({ success: true, matched: false, coins: coinUpdate.Attributes.coins });
        }

        if (action === "REJECTED") {
            const reverse = await ddb.send(new GetCommand({ TableName: TABLES.LIKES, Key: { fromUserId: toUserId, toUserId: fromEmail } }));
            const reverseAction = String(reverse.Item?.action || "").toUpperCase();
            if (["LIKE", "SUPERLIKE"].includes(reverseAction)) {
                await ddb.send(new PutCommand({ TableName: TABLES.LIKES, Item: { fromUserId: toUserId, toUserId: fromEmail, action: "REJECTED_BY_RECEIVER", previousAction: reverseAction, timestamp: Date.now() } }));
            }
            await setInteractionStatus(fromEmail, toUserId, "REJECTED");
            await ddb.send(new PutCommand({ TableName: TABLES.LIKES, Item: { fromUserId: fromEmail, toUserId, action: "REJECTED", timestamp: Date.now() } }));
            return res.json({ success: true, matched: false, coins: coinUpdate.Attributes.coins });
        }

        await setInteractionStatus(fromEmail, toUserId, action);
        await ddb.send(new PutCommand({ TableName: TABLES.LIKES, Item: { fromUserId: fromEmail, toUserId, action, timestamp: Date.now() } }));
        return res.json({ success: true, matched: false, coins: coinUpdate.Attributes.coins });
    } catch (e) {
        console.error("[SWIPE FAIL]:", e.message);
        if (e.name === "ConditionalCheckFailedException") return res.status(400).json({ error: "Insufficient coins" });
        res.status(500).json({ error: "Server technical error" });
    }
});

app.post('/api/coins/reward', authenticateToken, async (req, res) => {
    try {
        const update = await ddb.send(new UpdateCommand({ TableName: TABLES.USERS, Key: { email: req.user.email }, UpdateExpression: "SET coins = if_not_exists(coins, :start) + :r", ExpressionAttributeValues: { ":r": 5, ":start": 25 }, ReturnValues: "ALL_NEW" }));
        res.json({ success: true, coins: Number(update.Attributes.coins) });
    } catch (e) { res.status(500).json({ error: e.message }); }
});
app.post('/api/referral/credit', authenticateToken, async (req, res) => {
    try { const { code } = req.body; const all = await ddb.send(new ScanCommand({ TableName: TABLES.USERS })); const referrer = all.Items.find(u => u.referralCode === code); if (referrer) { await ddb.send(new UpdateCommand({ TableName: TABLES.USERS, Key: { email: referrer.email }, UpdateExpression: "SET coins = if_not_exists(coins, :start) + :b", ExpressionAttributeValues: { ":b": 50, ":start": 25 } })); res.json({ success: true }); } else { res.status(404).json({ error: "Invalid code" }); } }
    catch (e) { res.status(500).json({ error: e.message }); }
});
app.get('/api/swipe/leaderboard', async (req, res) => {
    try { const result = await ddb.send(new ScanCommand({ TableName: TABLES.USERS, ProjectionExpression: "email, username, coins" })); res.json({ leaderboard: (result.Items || []).sort((a,b) => (b.coins || 0) - (a.coins || 0)) }); }
    catch (e) { res.status(500).json({ error: e.message }); }
});

app.post('/api/account/deactivate', authenticateToken, async (req, res) => {
    try { await ddb.send(new UpdateCommand({ TableName: TABLES.USERS, Key: { email: req.user.email }, UpdateExpression: "SET isDeactivated = :t", ExpressionAttributeValues: { ":t": true } })); res.json({ success: true }); }
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

app.post('/profile/save', authenticateToken, async (req, res) => {
    try { const data = { ...req.body, email: req.user.email }; await ddb.send(new UpdateCommand({ TableName: TABLES.USERS, Key: { email: req.user.email }, UpdateExpression: "SET #u = :u, age = :age, gender = :gender, country = :country, state = :state, photoUri = :photoUri, bio = :bio", ExpressionAttributeNames: { "#u": "username" }, ExpressionAttributeValues: { ":u": data.username, ":age": data.age, ":gender": data.gender, ":country": data.country, ":state": data.state, ":photoUri": data.photoUri, ":bio": data.bio } })); res.json({ success: true }); }
    catch (e) { res.status(500).json({ error: e.message }); }
});

app.get('/profile/me', authenticateToken, async (req, res) => {
    try { const result = await ddb.send(new GetCommand({ TableName: TABLES.USERS, Key: { email: req.user.email } })); res.json({ user: result.Item }); }
    catch (e) { res.status(500).json({ error: e.message }); }
});

app.get('/profile/:email', authenticateToken, async (req, res) => {
    try { const result = await ddb.send(new GetCommand({ TableName: TABLES.USERS, Key: { email: req.params.email.trim().toLowerCase() } })); res.json({ user: result.Item }); }
    catch (e) { res.status(500).json({ error: e.message }); }
});

app.get('/api/matches', authenticateToken, async (req, res) => {
    try { const result = await ddb.send(new ScanCommand({ TableName: TABLES.MATCHES, FilterExpression: "contains(#u, :me)", ExpressionAttributeNames: { "#u": "users" }, ExpressionAttributeValues: { ":me": req.user.email } })); res.json({ matches: result.Items || [] }); }
    catch (e) { res.status(500).json({ error: e.message }); }
});

app.get('/api/chat/:matchId', authenticateToken, async (req, res) => {
    try { const result = await ddb.send(new QueryCommand({ TableName: "Messages", KeyConditionExpression: "matchId = :m", ExpressionAttributeValues: { ":m": req.params.matchId } })); res.json({ messages: result.Items || [] }); }
    catch (e) { res.status(500).json({ error: e.message }); }
});

app.post('/api/chat/send', authenticateToken, async (req, res) => {
    try { const item = { matchId: req.body.matchId, messageId: uuidv4(), sender: req.user.email, text: req.body.text || "", imageUrl: req.body.imageUrl || "", timestamp: Date.now() }; await ddb.send(new PutCommand({ TableName: "Messages", Item: item })); res.json({ success: true, message: item }); }
    catch (e) { res.status(500).json({ error: e.message }); }
});

app.get('/api/notifications', authenticateToken, async (req, res) => {
    try { const result = await ddb.send(new QueryCommand({ TableName: "Notifications", KeyConditionExpression: "userEmail = :e", ExpressionAttributeValues: { ":e": req.user.email } })); res.json({ notifications: result.Items || [] }); }
    catch (e) { res.status(500).json({ error: e.message }); }
});

app.post('/api/notifications/read', authenticateToken, async (req, res) => {
    try { await ddb.send(new UpdateCommand({ TableName: "Notifications", Key: { userEmail: req.user.email, notificationId: req.body.notificationId }, UpdateExpression: "SET #r = :t", ExpressionAttributeNames: { "#r": "read" }, ExpressionAttributeValues: { ":t": true } })); res.json({ success: true }); }
    catch (e) { res.status(500).json({ error: e.message }); }
});

app.post('/api/account/logout', authenticateToken, async (req, res) => {
    res.json({ success: true });
});

app.get('/health', (req, res) => res.json({ ok: true }));

io.on('connection', (socket) => {
    socket.on('join', (email) => socket.join(String(email || '').toLowerCase()));
    socket.on('send_message', async (data) => {
        try {
            const item = { matchId: data.matchId, messageId: uuidv4(), sender: String(data.sender || '').toLowerCase(), text: data.text || '', imageUrl: data.imageUrl || '', timestamp: Date.now() };
            await ddb.send(new PutCommand({ TableName: "Messages", Item: item }));
            io.to(String(data.receiver || '').toLowerCase()).emit('new_message', item);
            io.to(String(data.sender || '').toLowerCase()).emit('new_message', item);
        } catch (e) { console.error('socket send_message error:', e.message); }
    });
});

const PORT = process.env.PORT || 4000;
masterServer.listen(PORT, '0.0.0.0', () => console.log(`Tinklet API running on port ${PORT}`));
