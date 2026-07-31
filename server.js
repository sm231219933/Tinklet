const { default: makeWASocket, useMultiFileAuthState, DisconnectReason } = require("@whiskeysockets/baileys");
const express = require('express');
const http = require('http');
const { Server } = require("socket.io");
const pino = require('pino');
const qrcode = require('qrcode-terminal');
const readline = require('readline');
const nodemailer = require('nodemailer');

const app = express();
const server = http.createServer(app);
const io = new Server(server, { cors: { origin: "*" } });

app.use(express.json());

// --- GMAIL SETUP (Email OTP Jugad) ---
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
            console.log('\n✅ WhatsApp Bot Connected & Ready!\n');
        }
    });

    // --- WHATSAPP OTP ENDPOINT ---
    app.post('/send-otp', async (req, res) => {
        let { phoneNumber, otp } = req.body;
        console.log(`📩 Incoming WhatsApp OTP Request: ${phoneNumber}`);
        try {
            let cleanNumber = phoneNumber.replace(/\D/g, ''); 
            if (cleanNumber.length === 10) cleanNumber = '91' + cleanNumber;
            const jid = `${cleanNumber}@s.whatsapp.net`;
            await sock.sendMessage(jid, { 
                text: `*Tinklet - Verification*\n\nYour OTP is: *${otp}*\n\nDo not share this code.` 
            });
            console.log(`✅ WhatsApp OTP sent to: ${jid}`);
            res.sendStatus(200);
        } catch (e) {
            console.error(`❌ WhatsApp Error: ${e.message}`);
            res.status(500).send(e.message);
        }
    });

    // --- EMAIL OTP ENDPOINT ---
    app.post('/send-email-otp', (req, res) => {
        const { email, otp } = req.body;
        console.log(`📩 Incoming Email OTP Request: ${email}`);
        const mailOptions = {
            from: '"Tinklet Team" <sm231219933@gmail.com>',
            to: email,
            subject: 'Tinklet Verification Code',
            text: `Welcome to Tinklet! Your verification code is ${otp}. Happy Dating!`
        };
        transporter.sendMail(mailOptions, (error) => {
            if (error) {
                console.error(`❌ Email Error: ${error.message}`);
                return res.status(500).send(error.toString());
            }
            console.log(`✅ Email OTP sent to: ${email}`);
            res.sendStatus(200);
        });
    });
}

// --- CHAT & SIGNALLING ---
io.on('connection', (socket) => {
    socket.on('join', (userId) => {
        socket.join(userId);
        console.log(`👤 User joined: ${userId}`);
    });
    socket.on('chat_message', (data) => io.to(data.receiverId).emit('chat_message', data));
    socket.on('call_signal', (data) => io.to(data.receiverId).emit('call_signal', data));
});

startBot();
server.listen(4000, () => console.log('🚀 Tinklet Master Server running on Port 4000'));
