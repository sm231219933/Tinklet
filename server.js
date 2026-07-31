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
    const sock = makeWASocket({ auth: state, logger: pino({ level: 'silent' }) });
    
    sock.ev.on('creds.update', saveCreds);

    if (!sock.authState.creds.registered) {
        const phoneNumber = await question('Enter WhatsApp Number (91...): ');
        const code = await sock.requestPairingCode(phoneNumber);
        console.log(`\n🔥 YOUR PAIRING CODE: ${code}\n`);
    }

    sock.ev.on('connection.update', (update) => {
        const { connection, lastDisconnect } = update;
        if (connection === 'close') {
            const shouldReconnect = lastDisconnect?.error?.output?.statusCode !== DisconnectReason.loggedOut;
            if (shouldReconnect) startBot();
        } else if (connection === 'open') {
            console.log('✅ WhatsApp Bot Connected!');
        }
    });

    app.post('/send-otp', async (req, res) => {
        const { phoneNumber, otp } = req.body;
        try {
            await sock.sendMessage(`91${phoneNumber}@s.whatsapp.net`, { text: `Tinklet OTP: ${otp}` });
            res.sendStatus(200);
        } catch (e) { res.status(500).send(e.message); }
    });
}

io.on('connection', (socket) => {
    socket.on('join', (userId) => socket.join(userId));
    socket.on('chat_message', (data) => io.to(data.receiverId).emit('chat_message', data));
    socket.on('call_signal', (data) => io.to(data.receiverId).emit('call_signal', data));
});

startBot();
server.listen(4000, () => console.log('🚀 Tinklet Server on Port 4000'));
