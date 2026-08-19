const { default: makeWASocket, useMultiFileAuthState, DisconnectReason } = require("@whiskeysockets/baileys");
const express = require('express');
const http = require('http');
const { Server } = require("socket.io");
const pino = require('pino');
const nodemailer = require('nodemailer');
const AWS = require("aws-sdk");
const cors = require("cors");
const bodyParser = require("body-parser");
const readline = require('readline');

const app = express();
app.use(cors());
app.use(bodyParser.json());

const server = http.createServer(app);
const io = new Server(server, { cors: { origin: "*" } });

// ===== AWS CONFIG (Dating Logic) =====
AWS.config.update({ region: "ap-south-1" });
const dynamoDb = new AWS.DynamoDB.DocumentClient();

const TABLES = {
  USERS: "Profiles", // Aapki table ka naam 'Profiles' hai
  LIKES: "Likes",
  MATCHES: "Matches"
};

// --- GMAIL SETUP (Email OTP) ---
const transporter = nodemailer.createTransport({
    service: 'gmail',
    auth: {
        user: 'sm231219933@gmail.com',
        pass: 'oblk qafc qrmk lwbt'
    }
});

const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
const question = (text) => new Promise((resolve) => rl.question(text, resolve));

async function startBot() {
    const { state, saveCreds } = await useMultiFileAuthState('auth_info');
    const sock = makeWASocket({ 
        auth: state, 
        logger: pino({ level: 'silent' }),
        printQRInTerminal: false 
    });
    
    sock.ev.on('creds.update', saveCreds);

    if (!sock.authState.creds.registered) {
        console.log("\n⚠️ Bot not connected. Requesting Pairing Code...");
        const phoneNumber = await question('👉 Enter your WhatsApp Number (91XXXXXXXXXX): ');
        const code = await sock.requestPairingCode(phoneNumber);
        console.log(`\n🔥 YOUR PAIRING CODE: ${code}\n`);
    }

    sock.ev.on('connection.update', (update) => {
        const { connection, lastDisconnect } = update;
        if (connection === 'close') {
            const shouldReconnect = lastDisconnect?.error?.output?.statusCode !== DisconnectReason.loggedOut;
            if (shouldReconnect) startBot();
        } else if (connection === 'open') {
            console.log('\n✅ Master Server Connected to WhatsApp!\n');
        }
    });

    // =========================================================
    // ENDPOINTS: OTP (WhatsApp & Email)
    // =========================================================
    app.post('/send-otp', async (req, res) => {
        let { phoneNumber, otp } = req.body;
        try {
            let cleanNumber = phoneNumber.replace(/\D/g, ''); 
            if (cleanNumber.length === 10) cleanNumber = '91' + cleanNumber;
            const jid = `${cleanNumber}@s.whatsapp.net`;
            await sock.sendMessage(jid, { 
                text: `*Tinklet - Verification*\n\nYour OTP is: *${otp}*\n\nDo not share this code.` 
            });
            res.sendStatus(200);
        } catch (e) { res.status(500).send(e.message); }
    });

    app.post('/send-email-otp', (req, res) => {
        const { email, otp } = req.body;
        const mailOptions = {
            from: '"Tinklet Team" <sm231219933@gmail.com>',
            to: email,
            subject: 'Tinklet Verification Code',
            text: `Welcome to Tinklet! Your verification code is ${otp}.`
        };
        transporter.sendMail(mailOptions, (error) => {
            if (error) return res.status(500).send(error.toString());
            res.sendStatus(200);
        });
    });

    // =========================================================
    // ENDPOINTS: DATING CORE (Profiles & Interactions)
    // =========================================================
    
    // Get Discoverable Profiles
    app.get("/profiles", async (req, res) => {
        const { userId } = req.query; // userId = email
        try {
            const scanParams = { TableName: TABLES.USERS };
            const allUsers = await dynamoDb.scan(scanParams).promise();
            // Simple filter: hide 'me'
            const filtered = allUsers.Items.filter(u => u.email !== userId && u.email);
            res.json(filtered);
        } catch (err) { res.status(500).json({ error: err.message }); }
    });

    // Send Like / SuperLike
    app.post("/like", async (req, res) => {
        const { fromUserId, toUserId, type } = req.body;
        try {
            // Update recipient's interactions map in DynamoDB
            const targetUser = await dynamoDb.get({ TableName: TABLES.USERS, Key: { email: toUserId } }).promise();
            if (targetUser.Item) {
                const interactions = targetUser.Item.interactions || {};
                interactions[fromUserId] = (type === "superlike") ? "SUPERLIKE" : "PENDING";
                
                await dynamoDb.put({
                    TableName: TABLES.USERS,
                    Item: { ...targetUser.Item, interactions }
                }).promise();
            }
            res.json({ success: true, isMatch: false });
        } catch (err) { res.status(500).json({ error: err.message }); }
    });

    // Accept / Respond to Like
    app.post("/likes/respond", async (req, res) => {
        const { currentUserId, otherUserId, action } = req.body; // action: "accept"
        try {
            const me = await dynamoDb.get({ TableName: TABLES.USERS, Key: { email: currentUserId } }).promise();
            const other = await dynamoDb.get({ TableName: TABLES.USERS, Key: { email: otherUserId } }).promise();
            
            if (action === "accept" && me.Item && other.Item) {
                // Update interactions for both to ACCEPTED
                const myInt = me.Item.interactions || {};
                myInt[otherUserId] = "ACCEPTED";
                await dynamoDb.put({ TableName: TABLES.USERS, Item: { ...me.Item, interactions: myInt } }).promise();
                
                const otherInt = other.Item.interactions || {};
                otherInt[currentUserId] = "ACCEPTED";
                await dynamoDb.put({ TableName: TABLES.USERS, Item: { ...other.Item, interactions: otherInt } }).promise();
            }
            res.json({ success: true });
        } catch (err) { res.status(500).json({ error: err.message }); }
    });
}

// --- CHAT & CALLING SIGNALLING ---
io.on('connection', (socket) => {
    socket.on('join', (userId) => socket.join(userId));
    socket.on('chat_message', (data) => io.to(data.receiverId).emit('chat_message', data));
    socket.on('call_signal', (data) => io.to(data.receiverId).emit('call_signal', data));
});

startBot();
server.listen(4000, () => console.log('🚀 MASTER SERVER LIVE ON PORT 4000'));
