const { default: makeWASocket, useMultiFileAuthState, DisconnectReason } = require("@whiskeysockets/baileys");
const express = require('express');
const http = require('http');
const { Server } = require("socket.io");
const pino = require('pino');
const qrcode = require('qrcode-terminal');
const readline = require('readline');

const app = express();
const server = http.createServer(app);
const io = new Server(server, { cors: { origin: "*" } });

app.use(express.json());

const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
const question = (text) => new Promise((resolve) => rl.question(text, resolve));

async function startBot() {
    const { state, saveCreds } = await useMultiFileAuthState('auth_info');
    
    // Pino logger ko silent rakha hai taaki sirf kaam ke logs dikhein
    const sock = makeWASocket({ 
        auth: state, 
        logger: pino({ level: 'silent' }),
        printQRInTerminal: false 
    });
    
    sock.ev.on('creds.update', saveCreds);

    // Pairing Code logic (Bina QR scan ke connect karne ke liye)
    if (!sock.authState.creds.registered) {
        console.log("\n⚠️ Bot not connected. Requesting Pairing Code...");
        const phoneNumber = await question('👉 Enter your WhatsApp Number (91XXXXXXXXXX): ');
        const code = await sock.requestPairingCode(phoneNumber);
        console.log(`\n🔥 YOUR PAIRING CODE: ${code}\n`);
        console.log("Steps: Open WhatsApp -> Linked Devices -> Link a Device -> Link with phone number instead -> Enter this code.");
    }

    sock.ev.on('connection.update', (update) => {
        const { connection, lastDisconnect } = update;
        if (connection === 'close') {
            const shouldReconnect = lastDisconnect?.error?.output?.statusCode !== DisconnectReason.loggedOut;
            console.log('🔄 Connection closed. Reconnecting...', shouldReconnect);
            if (shouldReconnect) startBot();
        } else if (connection === 'open') {
            console.log('\n✅ WhatsApp Bot Connected & Ready!\n');
        }
    });

    // --- OTP SENDING ENDPOINT ---
    app.post('/send-otp', async (req, res) => {
        let { phoneNumber, otp } = req.body;
        console.log(`📩 Incoming OTP Request for: ${phoneNumber}`);
        
        try {
            // JUGAD: Number ko clean karna (Sirf digits rakho)
            let cleanNumber = phoneNumber.replace(/\D/g, ''); 
            
            // Agar 10 digit hai toh 91 jodo, agar already 12 digit (91...) hai toh rehne do
            if (cleanNumber.length === 10) {
                cleanNumber = '91' + cleanNumber;
            }

            const jid = `${cleanNumber}@s.whatsapp.net`;
            
            await sock.sendMessage(jid, { 
                text: `*Tinklet - Bharat Dating App*\n\nYour OTP is: *${otp}*\n\nDo not share this with anyone for security.` 
            });

            console.log(`✅ OTP sent successfully to: ${jid}`);
            res.sendStatus(200);
        } catch (e) {
            console.error(`❌ Error sending OTP: ${e.message}`);
            res.status(500).send(e.message);
        }
    });
}

// --- CHAT & WEBRTC SIGNALLING ---
io.on('connection', (socket) => {
    socket.on('join', (userId) => {
        socket.join(userId);
        console.log(`👤 User joined room: ${userId}`);
    });
    
    socket.on('chat_message', (data) => {
        io.to(data.receiverId).emit('chat_message', data);
    });

    socket.on('call_signal', (data) => {
        io.to(data.receiverId).emit('call_signal', data);
    });
});

startBot();

// Port 4000 par chala rahe hain (Port 3000 par purani app safe rahegi)
server.listen(4000, () => {
    console.log('🚀 Tinklet Server running on Port 4000');
});
