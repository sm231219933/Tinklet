const express = require('express');
const cors = require('cors');
const http = require('http');
const { Server } = require('socket.io');

const app = express();
app.use(cors());
app.use(express.json());

const masterServer = http.createServer(app);
const io = new Server(masterServer, {
    cors: { origin: '*', methods: ['GET', 'POST'] },
    transports: ['websocket', 'polling'],
    pingTimeout: 60000,
    pingInterval: 25000
});

// App Settings & Config
let onlineUsers = new Map();
let activeCalls = new Map();
let userActivity = new Map();
let liveUsersCount = 0;

// Storage structures for Tinklet Messaging
let userSessions = new Map();
let activeConnections = new Map();

// Global EnglishTrackingAI variables
const BANNED_USERS_FILE = 'banned_users.json';
let bannedUsers = new Set();
let onlinePool = new Map();
let callerQueue = [];
let audioMatchingPool = new Map();
let chatMatchingPool = new Map();

// Dating Specific
let activeDatingCalls = new Map();
let recentChatHistory = [];
let activeP2PCalls = new Map();

// EnglishTrackingAI Calling Specific
let activeMatches = new Map();
let currentCallPairs = new Map();
let blindDateQueue = [];
let blindDatePairs = new Map();


io.on('connection', (socket) => {
    liveUsersCount++;
    io.emit('live_count_update', { count: liveUsersCount });

    // 1. Tinklet / BharatDatingApp Chat Signaling
    socket.on('authenticate', (data) => {
        if (!data || !data.userId) return;
        const uId = String(data.userId).toLowerCase();
        userSessions.set(uId, socket.id);
        activeConnections.set(socket.id, uId);
        socket.join(uId);
    });

    socket.on('send_message', (data) => {
        if (!data || !data.targetUserId) return;
        const targetId = String(data.targetUserId).toLowerCase();

        io.to(targetId).emit('receive_message', {
            id: data.id,
            text: data.text,
            senderId: data.senderId,
            targetUserId: targetId,
            timestamp: data.timestamp,
            isMe: false,
            replyToId: data.replyToId,
            replyToText: data.replyToText
        });

        io.to(String(data.senderId).toLowerCase()).emit('message_delivered', {
            id: data.id,
            targetUserId: targetId
        });
    });

    socket.on('mark_read', (data) => {
        if (!data || !data.senderId) return;
        io.to(String(data.senderId).toLowerCase()).emit('message_read', {
            id: data.id,
            readBy: data.readerId
        });
    });

    socket.on('delete_message', (data) => {
        if (!data || !data.targetUserId) return;
        const targetId = String(data.targetUserId).toLowerCase();
        io.to(targetId).emit('message_deleted', {
            id: data.id,
            deleterId: data.deleterId
        });
    });

    socket.on('edit_message', (data) => {
        if (!data || !data.targetUserId) return;
        const targetId = String(data.targetUserId).toLowerCase();
        io.to(targetId).emit('message_edited', {
            id: data.id,
            newText: data.newText,
            editorId: data.editorId
        });
    });

    socket.on('block_user', (data) => {
        if (!data || !data.targetUserId) return;
        const targetId = String(data.targetUserId).toLowerCase();
        io.to(targetId).emit('user_blocked', {
            blockedBy: data.blockerId
        });
    });

    // 2. Original App Audio/Video Call System
    socket.on('register', (userId) => {
        const uId = String(userId).toLowerCase();
        onlineUsers.set(uId, socket.id);
        socket.userId = uId;
        userActivity.set(uId, Date.now());

        const count = onlineUsers.size;
        console.log(`[Dating] User registered: ${uId}, Online: ${count}`);

        socket.join(uId);
        io.emit('online_users_update', { count });

        const usersList = Array.from(onlineUsers.keys()).map(id => ({ userId: id }));
        io.emit('user_list', usersList);
    });

    socket.on('keep_alive', (userId) => {
        if(userId) userActivity.set(String(userId).toLowerCase(), Date.now());
    });

    socket.on('call:offer', (data) => {
        const toId = String(data.toUserId).toLowerCase();
        const fromId = String(data.fromUserId).toLowerCase();

        activeDatingCalls.set(fromId, toId);
        activeDatingCalls.set(toId, fromId);

        io.to(toId).emit('call:incoming', data);
    });

    socket.on('call:answer', (data) => {
        const toId = String(data.toUserId).toLowerCase();
        io.to(toId).emit('call:answered', data);
    });

    socket.on('call:reject', (data) => {
        const toId = String(data.toUserId).toLowerCase();
        activeDatingCalls.delete(toId);
        activeDatingCalls.delete(String(data.fromUserId).toLowerCase());
        io.to(toId).emit('call:rejected', data);
    });

    socket.on('call:ice-candidate', (data) => {
        const toId = String(data.toUserId).toLowerCase();
        io.to(toId).emit('call:ice-candidate', data);
    });

    socket.on('call:end', (data) => {
        const toId = String(data.toUserId).toLowerCase();
        activeDatingCalls.delete(toId);
        if (data.fromUserId) activeDatingCalls.delete(String(data.fromUserId).toLowerCase());
        io.to(toId).emit('call:ended', data);
    });


    // 3. Structured Calling Handlers (SignalingClient.kt ke liye)
    socket.on('user:join', (data) => {
        socket.join(String(data.userId).toLowerCase());
    });

    socket.on('call:start', (data) => {
        io.to(String(data.toUserId).toLowerCase()).emit('call:incoming', data);
    });

    socket.on('call:cancel', (data) => {
        io.to(String(data.toUserId).toLowerCase()).emit('call:cancelled', data);
    });

    // --- BLIND DATE MATCHING (Tinklet Specific) ---
    socket.on('blinddate:join', (data) => {
        const userId = String(data.fromUserId).toLowerCase();
        const callType = data.callType || 'audio';

        blindDateQueue = blindDateQueue.filter(u => u.userId !== userId && u.socketId !== socket.id);

        const partnerIndex = blindDateQueue.findIndex(u => u.callType === callType && u.userId !== userId);

        if (partnerIndex !== -1) {
            const partner = blindDateQueue.splice(partnerIndex, 1)[0];

            blindDatePairs.set(socket.id, partner.socketId);
            blindDatePairs.set(partner.socketId, socket.id);

            io.to(socket.id).emit('blinddate:matched', {
                partnerId: partner.userId,
                isCaller: true,
                callType: callType
            });

            io.to(partner.socketId).emit('blinddate:matched', {
                partnerId: userId,
                isCaller: false,
                callType: callType
            });
        } else {
            blindDateQueue.push({
                userId,
                socketId: socket.id,
                callType
            });
        }
    });

    socket.on('blinddate:signal', (data) => {
        const partnerSocketId = blindDatePairs.get(socket.id);
        if (partnerSocketId) {
            io.to(partnerSocketId).emit('blinddate:signal', data);
        } else if (data.targetUserId) {
            io.to(String(data.targetUserId).toLowerCase()).emit('blinddate:signal', data);
        }
    });


    // EnglishTrackingAI Features
    socket.on('join_pool', (data) => {
        if(bannedUsers.has(socket.id)) return;
        const userName = data.userName || 'Learner';
        const targetType = data.type || 'audio_match';

        onlinePool.set(socket.id, { id: socket.id, name: userName, type: targetType, isBusy: false });

        if(targetType === 'speaking_partner') {
            const availableHost = Array.from(onlinePool.values()).find(u => u.type === 'speaking_host' && !u.isBusy);
            if(availableHost) {
                onlinePool.get(socket.id).isBusy = true;
                onlinePool.get(availableHost.id).isBusy = true;

                io.to(socket.id).emit('match_found', { hostId: availableHost.id, hostName: availableHost.name });
                io.to(availableHost.id).emit('learner_joined', { learnerId: socket.id, learnerName: userName });
            }
        }
    });

    socket.on('leave_pool', () => onlinePool.delete(socket.id));
    socket.on('webrtc_offer', (data) => io.to(data.targetId).emit('webrtc_offer', { sdp: data.sdp, senderId: socket.id }));
    socket.on('webrtc_answer', (data) => io.to(data.targetId).emit('webrtc_answer', { sdp: data.sdp, senderId: socket.id }));
    socket.on('ice_candidate', (data) => io.to(data.targetId).emit('ice_candidate', { candidate: data.candidate, senderId: socket.id }));

    socket.on('send_chat', (data) => {
        const msg = { senderName: data.senderName, text: data.text };
        recentChatHistory.push(msg);
        if (recentChatHistory.length > 50) recentChatHistory.shift();
        io.to(data.targetId).emit('receive_chat', msg);
    });

    socket.on('end_call', (data) => {
        if(onlinePool.has(socket.id)) onlinePool.get(socket.id).isBusy = false;
        if(data.targetId) {
            if(onlinePool.has(data.targetId)) onlinePool.get(data.targetId).isBusy = false;
            io.to(data.targetId).emit('call_ended', { senderId: socket.id });
        }
    });

    // EnglishTrackingAI Calling Features
    socket.on('register_caller', (data) => {
        socket.userId = data.userId;
        socket.userName = data.userName;
        activeMatches.set(socket.id, { userId: data.userId, name: data.userName, mode: 'idle' });
    });

    socket.on('join_random_call', () => {
        if(!socket.userId) return;
        const availablePartner = callerQueue.find(p => p.socketId !== socket.id);
        if(availablePartner) {
            callerQueue = callerQueue.filter(p => p.socketId !== availablePartner.socketId);
            currentCallPairs.set(socket.id, availablePartner.socketId);
            currentCallPairs.set(availablePartner.socketId, socket.id);

            io.to(socket.id).emit('call_matched', { partnerId: availablePartner.userId, partnerName: availablePartner.userName, isInitiator: true });
            io.to(availablePartner.socketId).emit('call_matched', { partnerId: socket.userId, partnerName: socket.userName, isInitiator: false });
        } else {
            callerQueue.push({ socketId: socket.id, userId: socket.userId, userName: socket.userName });
        }
    });

    socket.on('leave_random_call', () => {
        callerQueue = callerQueue.filter(p => p.socketId !== socket.id);
    });

    socket.on('direct_call_offer', (data) => {
        const targetSocket = Array.from(activeMatches.entries()).find(([sid, user]) => user.userId === data.targetUserId);
        if(targetSocket) {
            io.to(targetSocket[0]).emit('incoming_direct_call', { callerId: socket.userId, callerName: socket.userName, sdp: data.sdp });
        }
    });

    socket.on('relay_sdp', (data) => {
        const targetSid = currentCallPairs.get(socket.id) || Array.from(activeMatches.entries()).find(([sid, user]) => user.userId === data.targetId)?.[0];
        if(targetSid) io.to(targetSid).emit('receive_sdp', { sdp: data.sdp });
    });

    socket.on('relay_ice', (data) => {
        const targetSid = currentCallPairs.get(socket.id) || Array.from(activeMatches.entries()).find(([sid, user]) => user.userId === data.targetId)?.[0];
        if(targetSid) io.to(targetSid).emit('receive_ice', { candidate: data.candidate });
    });


    // EnglishTrackingAI Audio Match
    socket.on('join_audio_match', (data) => {
        const uInfo = { id: socket.id, gender: data.gender, pref: data.prefGender };

        const partner = Array.from(audioMatchingPool.values()).find(p =>
            p.id !== socket.id &&
            (uInfo.pref === 'Any' || uInfo.pref === p.gender) &&
            (p.pref === 'Any' || p.pref === uInfo.gender)
        );

        if(partner) {
            audioMatchingPool.delete(partner.id);
            activeP2PCalls.set(socket.id, partner.id);
            activeP2PCalls.set(partner.id, socket.id);

            io.to(socket.id).emit('audio_matched', { initiator: true });
            io.to(partner.id).emit('audio_matched', { initiator: false });
        } else {
            audioMatchingPool.set(socket.id, uInfo);
        }
    });

    socket.on('leave_audio_match', () => audioMatchingPool.delete(socket.id));

    socket.on('webrtc_signal', (data) => {
        const target = activeP2PCalls.get(socket.id);
        if(target) io.to(target).emit('webrtc_signal', data);
    });

    // EnglishTrackingAI Chat Match
    socket.on('join_chat_match', (data) => {
        const uInfo = { id: socket.id, gender: data.gender, pref: data.prefGender };

        const partner = Array.from(chatMatchingPool.values()).find(p =>
            p.id !== socket.id &&
            (uInfo.pref === 'Any' || uInfo.pref === p.gender) &&
            (p.pref === 'Any' || p.pref === uInfo.gender)
        );

        if(partner) {
            chatMatchingPool.delete(partner.id);
            activeP2PCalls.set(socket.id, partner.id);
            activeP2PCalls.set(partner.id, socket.id);

            io.to(socket.id).emit('chat_matched', { partnerId: partner.id });
            io.to(partner.id).emit('chat_matched', { partnerId: socket.id });
        } else {
            chatMatchingPool.set(socket.id, uInfo);
        }
    });

    socket.on('leave_chat_match', () => chatMatchingPool.delete(socket.id));

    socket.on('chat_message', (data) => {
        const target = activeP2PCalls.get(socket.id);
        if(target) io.to(target).emit('chat_message', data);
    });

    socket.on('typing_status', (data) => {
        const target = activeP2PCalls.get(socket.id);
        if(target) io.to(target).emit('typing_status', data);
    });

    socket.on('chat_left', () => {
        const target = activeP2PCalls.get(socket.id);
        if(target) {
            io.to(target).emit('chat_left');
            activeP2PCalls.delete(target);
        }
        activeP2PCalls.delete(socket.id);
    });

    socket.on('like_profile', (data) => io.to(String(data.toUserId).toLowerCase()).emit('new_like', data));
    socket.on('match_created', (data) => {
        io.to(String(data.user1).toLowerCase()).emit('match_created', data);
        io.to(String(data.user2).toLowerCase()).emit('match_created', data);
    });
    socket.on('unmatch_user', (data) => io.to(String(data.toUserId).toLowerCase()).emit('unmatch_user', data));

    // EnglishTrackingAI & Blind Date Matching Pool
    let activeMatchingPool = [];
    let activePeerPairs = new Map();

    socket.on('register_user', (data) => {
        socket.userId = String(data.userId || '').toLowerCase();
        socket.gender = String(data.gender || 'Any');
        socket.mode = String(data.mode || 'audio');
        socket.join(socket.userId);
        console.log(`User registered: ${socket.userId} (${socket.gender}) mode: ${socket.mode}`);
    });

    socket.on('find_buddy', (data) => {
        const userId = String(data.userId || data.fromUserId || socket.userId || '').toLowerCase();
        const mode = String(data.mode || data.callType || 'audio').toLowerCase();
        const prefGender = String(data.prefGender || 'Any');

        activeMatchingPool = activeMatchingPool.filter(u => u.userId !== userId && u.socketId !== socket.id);

        const partnerIndex = activeMatchingPool.findIndex(u => {
            if (u.userId === userId) return false;
            return true;
        });

        if (partnerIndex !== -1) {
            const partner = activeMatchingPool.splice(partnerIndex, 1)[0];

            activePeerPairs.set(socket.id, partner.socketId);
            activePeerPairs.set(partner.socketId, socket.id);

            console.log(`P2P MATCH SUCCESS: ${userId} <--> ${partner.userId}`);

            io.to(socket.id).emit('matched', {
                initiator: true,
                partnerId: partner.userId,
                isCaller: true
            });

            io.to(partner.socketId).emit('matched', {
                initiator: false,
                partnerId: userId,
                isCaller: false
            });

            io.to(socket.id).emit('blinddate:matched', {
                partnerId: partner.userId,
                isCaller: true,
                callType: mode
            });

            io.to(partner.socketId).emit('blinddate:matched', {
                partnerId: userId,
                isCaller: false,
                callType: mode
            });

        } else {
            activeMatchingPool.push({ userId, gender: socket.gender, mode, socketId: socket.id });
            console.log(`User ${userId} waiting in pool (${mode}). Total waiting: ${activeMatchingPool.length}`);
        }
    });

    socket.on('webrtc_signal', (data) => {
        const partnerSocketId = activePeerPairs.get(socket.id);
        if (partnerSocketId) {
            io.to(partnerSocketId).emit('webrtc_signal', data);
            io.to(partnerSocketId).emit('blinddate:signal', data);
        } else if (data.targetUserId) {
            io.to(String(data.targetUserId).toLowerCase()).emit('webrtc_signal', data);
        }
    });

    socket.on('blinddate:leave', (data) => {
        const userId = String(data.fromUserId || socket.userId || '').toLowerCase();
        // Fixed: Use activeMatchingPool instead of old blindDateQueue
        activeMatchingPool = activeMatchingPool.filter(u => u.userId !== userId && u.socketId !== socket.id);

        // Fixed: Use activePeerPairs instead of old blindDatePairs
        const partnerSocketId = activePeerPairs.get(socket.id);
        if (partnerSocketId) {
            io.to(partnerSocketId).emit('blinddate:leave');
            io.to(partnerSocketId).emit('buddy_left');
            activePeerPairs.delete(partnerSocketId);
        }
        activePeerPairs.delete(socket.id);
    });

    socket.on('call:offer', (data) => io.to(String(data.toUserId).toLowerCase()).emit('call:incoming', data));
    socket.on('call:answer', (data) => io.to(String(data.toUserId).toLowerCase()).emit('call:answered', data));
    socket.on('call:reject', (data) => io.to(String(data.toUserId).toLowerCase()).emit('call:rejected', data));
    socket.on('call:ice-candidate', (data) => io.to(String(data.toUserId).toLowerCase()).emit('call:ice-candidate', data));
    socket.on('call:end', (data) => io.to(String(data.toUserId).toLowerCase()).emit('call:ended', data));

    socket.on('disconnect', () => {
        liveUsersCount = Math.max(0, liveUsersCount - 1);
        io.emit('live_count_update', { count: liveUsersCount });

        const uId = activeConnections.get(socket.id);
        if (uId) {
            userSessions.delete(uId);
            activeConnections.delete(socket.id);
        }

        if (socket.userId) {
            onlineUsers.delete(socket.userId);
            const count = onlineUsers.size;
            io.emit('online_users_update', { count });
        }

        onlinePool.delete(socket.id);
        callerQueue = callerQueue.filter(p => p.socketId !== socket.id);
        audioMatchingPool.delete(socket.id);
        chatMatchingPool.delete(socket.id);

        const peerPartner = activeP2PCalls.get(socket.id);
        if(peerPartner) {
            io.to(peerPartner).emit('buddy_left');
            activeP2PCalls.delete(peerPartner);
            activeP2PCalls.delete(socket.id);
        }

        // Cleanup Tinklet / BharatDatingApp active pools on disconnect
        activeMatchingPool = activeMatchingPool.filter(u => u.socketId !== socket.id);
        const matchPartnerId = activePeerPairs.get(socket.id);
        if (matchPartnerId) {
            io.to(matchPartnerId).emit('buddy_left');
            io.to(matchPartnerId).emit('blinddate:leave');
            activePeerPairs.delete(matchPartnerId);
        }
        activePeerPairs.delete(socket.id);
    });
});

const PORT = process.env.PORT || 4000;
masterServer.listen(PORT, '0.0.0.0', () => console.log(`Tinklet API running on port ${PORT}`));
