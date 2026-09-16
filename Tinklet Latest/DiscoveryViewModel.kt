package com.tinklet.bharatdatingapp.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tinklet.bharatdatingapp.data.local.*
import com.tinklet.bharatdatingapp.data.remote.*
import com.tinklet.bharatdatingapp.calling.*
import com.tinklet.bharatdatingapp.utils.*
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import java.util.*

data class RegistrationData(
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val password: String = "",
    val age: Int = 0,
    val gender: String = "",
    val country: String = "",
    val state: String = "",
    val referredBy: String = ""
)

class DiscoveryViewModel(private val app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getDatabase(app)
    private val profileDao = db.profileDao()
    private val chatMessageDao = db.chatMessageDao()
    private val preferenceManager = PreferenceManager(app)
    
    // MOVE CORE STATES TO THE TOP
    private val _currentUser = MutableStateFlow<UserProfile?>(null)
    val currentUser: StateFlow<UserProfile?> = _currentUser
    private val _remoteProfiles = MutableStateFlow<List<UserProfile>>(emptyList())
    private val _leaderboard = MutableStateFlow<List<UserProfile>>(emptyList())
    
    private val _regData = MutableStateFlow<RegistrationData?>(null)
    val regData: StateFlow<RegistrationData?> = _regData
    private val _isCheckingUser = MutableStateFlow(false)
    val isCheckingUser: StateFlow<Boolean> = _isCheckingUser
    
    private var signaling: SignalingClient? = null
    private var myDeviceId: String = ""

    val themeMode: StateFlow<String> = preferenceManager.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, "System")
    fun setThemeMode(mode: String) { viewModelScope.launch { preferenceManager.setThemeMode(mode) } }

    val appLanguage: StateFlow<String> = preferenceManager.appLanguage.stateIn(viewModelScope, SharingStarted.Eagerly, "en")
    fun setAppLanguage(lang: String) { 
        viewModelScope.launch { 
            preferenceManager.setAppLanguage(lang)
        } 
    }

    fun fetchLeaderboard() {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.getProfiles(userId = "")
                if (response.isSuccessful) {
                    val all = response.body() ?: emptyList()
                    _leaderboard.value = all.sortedByDescending { it.coins }.take(20)
                }
            } catch (e: Exception) { Log.e("Leaderboard", "Fetch fail", e) }
        }
    }

    fun buyBadge(type: String, cost: Int) {
        viewModelScope.launch {
            val me = _currentUser.value ?: return@launch
            if (me.coins >= cost) {
                // Calculate expiry (1 month)
                val expiry = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000)
                val updatedMe = me.copy(
                    coins = me.coins - cost,
                    badgeType = type,
                    badgeExpiry = expiry
                )
                profileDao.updateProfile(updatedMe)
                try {
                    RetrofitClient.apiService.saveProfileSecure(profile = updatedMe)
                } catch (e: Exception) {}
                Toast.makeText(app, "$type Badge Activated! 🏆", Toast.LENGTH_LONG).show()
                loadCurrentUser()
            } else {
                Toast.makeText(app, "Need $cost coins for $type badge.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val interactiveSounds: StateFlow<Boolean> = preferenceManager.interactiveSounds.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    fun setInteractiveSounds(enabled: Boolean) {
        viewModelScope.launch {
            preferenceManager.setInteractiveSounds(enabled)
            SoundManager.isSoundEnabled = enabled
        }
    }
    // 1. DISCOVERY FLOW (BACKUP STYLE FILTERING)
   private val _remoteProfiles = MutableStateFlow<List<UserProfile>>(emptyList())

val profiles: StateFlow<List<UserProfile>> = combine(
    _remoteProfiles,
    _currentUser
) { remote, me ->

    val myEmail = me?.email
        ?.trim()
        ?.lowercase()
        ?: ""

    remote
        .filter {
            it.email.trim().lowercase() != myEmail
        }
        .distinctBy {
            it.email.trim().lowercase()
        }
        .sortedWith(
            compareByDescending<UserProfile> {
                when (it.badgeType) {
                    "GOLDEN" -> 3
                    "SILVER" -> 2
                    "BRONZE" -> 1
                    else -> 0
                }
            }.thenByDescending {
                it.lastActive
            }
        )

}.stateIn(
    viewModelScope,
    SharingStarted.WhileSubscribed(5000),
    emptyList()
)
    // DEBUG VERSION: Showing remote profiles directly to find the bug


    val leaderboard: StateFlow<List<UserProfile>> = _leaderboard

    val isUserLoggedIn: StateFlow<Boolean?> = preferenceManager.isLoggedIn.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _isFetchingIdentity = MutableStateFlow(false)
    val isFetchingIdentity: StateFlow<Boolean> = _isFetchingIdentity

    private val _isSignUpMode = MutableStateFlow(true)
    val isSignUpMode: StateFlow<Boolean> = _isSignUpMode

    private val _isAuthLoading = MutableStateFlow(false)
    val isAuthLoading: StateFlow<Boolean> = _isAuthLoading

    private val _isDetectingLocation = MutableStateFlow(false)
    val isDetectingLocation: StateFlow<Boolean> = _isDetectingLocation

    private val _showWelcomeMessage = MutableStateFlow(false)
    val showWelcomeMessage: StateFlow<Boolean> = _showWelcomeMessage

    // NEW: Identity binding states
    private val _fetchedEmail = MutableStateFlow("")
    val fetchedEmail: StateFlow<String> = _fetchedEmail
    private val _fetchedPhone = MutableStateFlow("")
    val fetchedPhone: StateFlow<String> = _fetchedPhone

    private val _fetchedCountry = MutableStateFlow("")
    val fetchedCountry: StateFlow<String> = _fetchedCountry
    private val _fetchedState = MutableStateFlow("")
    val fetchedState: StateFlow<String> = _fetchedState

    private val _uploadProgress = MutableStateFlow<Int?>(null)
    val uploadProgress: StateFlow<Int?> = _uploadProgress

    private val _uploadError = MutableStateFlow<String?>(null)
    val uploadError: StateFlow<String?> = _uploadError

    val matches: StateFlow<List<UserProfile>> = profileDao.getMatches().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    // STRICT STATUS SYNC (LIKE, REJECTED, SUPERLIKE, PENDING, ACCEPTED)
    val sentRequests: StateFlow<List<UserProfile>> = profileDao.getAllProfilesFlow().map { list ->
        list.filter { it.connectionStatus == "LIKE" && !it.isMe }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val rejectedProfiles: StateFlow<List<UserProfile>> = profileDao.getAllProfilesFlow().map { list ->
        list.filter { it.connectionStatus == "REJECTED" && !it.isMe }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val superLikedProfiles: StateFlow<List<UserProfile>> = profileDao.getAllProfilesFlow().map { list ->
        list.filter { it.connectionStatus == "SUPERLIKE" && !it.isMe } 
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val incomingRequests: StateFlow<List<UserProfile>> = profileDao.getAllProfilesFlow().map { list ->
        list.filter { it.connectionStatus == "PENDING" && !it.isMe }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Keep existing 3x3 Inbox flows for RequestsScreen compatibility
    val incomingSuperlikes: StateFlow<List<UserProfile>> = incomingRequests.map { list ->
        list.filter { it.connectionStatus == "SUPERLIKE" }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val incomingNormalLikes: StateFlow<List<UserProfile>> = incomingRequests.map { list ->
        list.filter { it.connectionStatus != "SUPERLIKE" }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val incomingRejected: StateFlow<List<UserProfile>> = profileDao.getAllProfilesFlow().map { list ->
        list.filter { it.connectionStatus == "OTHER_REJECTED_ME" && !it.isMe }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val sentNormalLikes: StateFlow<List<UserProfile>> = sentRequests
    val sentSuperlikes: StateFlow<List<UserProfile>> = superLikedProfiles
    val sentRejected: StateFlow<List<UserProfile>> = rejectedProfiles

    init {
        myDeviceId = android.provider.Settings.Secure.getString(app.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: ""
        
        NotificationHelper.createNotificationChannels(app)

        // INITIALIZE NETWORK TOKEN
        viewModelScope.launch {
            preferenceManager.jwtToken.collect { token ->
                RetrofitClient.setToken(token)
            }
        }

        // LAZY INITIALIZATION: Prevent startup hang by delaying heavy background tasks
        viewModelScope.launch {
            delay(1000)
            startBackendSyncLoop()
            loadCurrentUser()
        }

        seedMockData() 
        
        onResetViewed()

        viewModelScope.launch {
            preferenceManager.interactiveSounds.collect { enabled ->
                SoundManager.isSoundEnabled = enabled
            }
        }
    }

    private fun initSignaling(userId: String) {
        if (userId.isBlank()) return
        
        Log.d("Signaling_Trace", "Initializing Socket.io Signaling for $userId")
        signaling = SignalingClient("http://15.206.14.213:3000", userId, object : SignalingClient.SignalingListener {
            override fun onIncomingCall(fromUserId: String, fromUserName: String, offer: String, callType: String) {
                Log.d("Signaling_Trace", "INCOMING CALL EVENT from $fromUserId")
                
                // Show notification bar alert
                NotificationHelper.showCallNotification(app, fromUserName, callType, fromUserId, offer)

                val intent = Intent(app, IncomingCallActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(IncomingCallActivity.EXTRA_FROM_USER_ID, fromUserId)
                    putExtra(IncomingCallActivity.EXTRA_FROM_USER_NAME, fromUserName)
                    putExtra(IncomingCallActivity.EXTRA_OFFER_SDP, offer)
                    putExtra(IncomingCallActivity.EXTRA_CALL_TYPE, callType)
                }
                app.startActivity(intent)
            }
            override fun onCallAnswered(answer: String) {
                NotificationHelper.cancelCallNotification(app)
            }
            override fun onCallRejected() {
                NotificationHelper.cancelCallNotification(app)
            }
            override fun onCallEnded() {
                NotificationHelper.cancelCallNotification(app)
            }
            override fun onIceCandidateReceived(candidate: String) {}
            override fun onChatMessageReceived(fromUserId: String, text: String, messageId: String?) {
                viewModelScope.launch {
                    val msg = ChatMessage(
                        matchId = fromUserId, 
                        senderId = "OTHER", 
                        text = text, 
                        messageId = messageId ?: java.util.UUID.randomUUID().toString(),
                        status = "DELIVERED"
                    )
                    chatMessageDao.insertMessage(msg)
                    
                    // Send Delivery Receipt
                    messageId?.let { signaling?.sendDeliveryReceipt(fromUserId, it) }

                    // Show notification
                    val senderName = profileDao.getProfileByEmail(fromUserId)?.name ?: "New Message"
                    NotificationHelper.showMessageNotification(app, senderName, text, fromUserId)
                }
            }
            override fun onMessageDelivered(fromUserId: String, messageId: String) {
                viewModelScope.launch {
                    chatMessageDao.updateMessageStatus(messageId, "DELIVERED")
                }
            }
            override fun onMessageRead(fromUserId: String, messageId: String) {
                viewModelScope.launch {
                    chatMessageDao.updateMessageStatus(messageId, "READ")
                }
            }
            override fun onMessageDeleted(fromUserId: String, msgTimestamp: Long) {
                viewModelScope.launch {
                    chatMessageDao.getMessagesForMatch(fromUserId).first().find { it.timestamp == msgTimestamp }?.let {
                        chatMessageDao.updateMessage(it.copy(isDeletedForEveryone = true, text = "This message was deleted"))
                    }
                }
            }
            override fun onMessageEdited(fromUserId: String, msgTimestamp: Long, newText: String) {
                viewModelScope.launch {
                    chatMessageDao.getMessagesForMatch(fromUserId).first().find { it.timestamp == msgTimestamp }?.let {
                        chatMessageDao.updateMessage(it.copy(text = newText, isEdited = true))
                    }
                }
            }
            override fun onUserBlocked(fromUserId: String) {
                viewModelScope.launch {
                    profileDao.getProfileByEmail(fromUserId)?.let {
                        profileDao.updateProfile(it.copy(isBlocked = true))
                    }
                }
            }
            override fun onCallFailed(reason: String) {
                Log.e("Signaling_Trace", "Call Failed: $reason")
            }
            override fun onConnected() {
                Log.d("Signaling_Trace", "Signaling Socket CONNECTED successfully")
            }
        })
        signaling?.connect()
        CallSignalingHolder.signalingClient = signaling
    }

    // 2. REFRESH FEED (CLEAN VERSION)
    fun refreshFeed() {
        viewModelScope.launch {
            try {
                Log.d("FeedDebug", "Refreshing Discovery Feed...")
                val response = withTimeoutOrNull(10000) { RetrofitClient.apiService.getFeed() }
                if (response?.isSuccessful == true) {
                    val remote = response.body()?.feed ?: emptyList()
                    val myEmail = (_currentUser.value?.email ?: "").lowercase().trim()
                    
                    // Strictly filter self from remote list
                    val filtered = remote.filter { it.email.lowercase().trim() != myEmail }
                    _remoteProfiles.value = filtered
                    Log.d("FeedDebug", "Success: Received ${filtered.size} profiles")
                } else {
                    Log.e("FeedDebug", "Server Error: ${response?.code()}")
                }
            } catch (e: Exception) { 
                Log.e("FeedDebug", "Refresh fail", e)
            }
        }
    }

    // 3. BACKEND SYNC LOOP (MIRRORING SERVER DATA)
    private fun startBackendSyncLoop() {
        viewModelScope.launch {
            while (true) {
                try {
                    val myId = _currentUser.value?.email ?: ""
                    if (myId.isNotBlank()) {
                        if (signaling == null) initSignaling(myId)
                        
                        refreshFeed()

                        // DEEP CLOUD SYNC: Mirror Sent, Incoming, and Matches
                        val syncRes = RetrofitClient.apiService.syncAll()
                        if (syncRes.isSuccessful && syncRes.body() != null) {
                            val data = syncRes.body()!!
                            Log.d("CloudSync", "Syncing lists: Sent=${data.sent.size}, Received=${data.incoming.size}")

                            if (data.sent.isNotEmpty()) profileDao.insertProfiles(data.sent)
                            if (data.incoming.isNotEmpty()) profileDao.insertProfiles(data.incoming)
                            if (data.matches.isNotEmpty()) profileDao.insertProfiles(data.matches)
                        }
                    }
                } catch (e: Exception) { Log.e("CloudSync", "Sync loop fail") }
                delay(10000)
            }
        }
    }

    private fun loadCurrentUser() {
        viewModelScope.launch {
            val isLoggedIn = preferenceManager.isLoggedIn.first()
            if (!isLoggedIn) {
                _currentUser.value = null
                return@launch
            }

            // 0. Try cache first for instant UI availability
            preferenceManager.cachedProfile.first()?.let { json ->
                try {
                    val cached = Gson().fromJson(json, UserProfile::class.java)
                    if (_currentUser.value == null) {
                        _currentUser.value = cached
                        initSignaling(cached.email)
                    }
                } catch (e: Exception) {
                    Log.e("Cache", "Failed to load cache")
                }
            }

            // 1. Try local DB
            val directMe = profileDao.getMyProfile()
            if (directMe != null) {
                _currentUser.value = directMe
                initSignaling(directMe.email)
                preferenceManager.saveProfileCache(Gson().toJson(directMe))
                refreshFeed() // TRIGGER FEED REFRESH ON STARTUP
                
                // DATA INTEGRITY GUARD: If matches are empty but logged in, trigger deep sync
                val matchesCount = profileDao.getProfileCount() 
                if (matchesCount <= 1) { 
                    Log.w("Stability", "Data mismatch detected. Triggering deep sync loop.")
                }
            } else {
                // 2. Aggressive Recovery: DataStore OR Firebase Auth
                val savedEmail = preferenceManager.userEmail.first() ?: FirebaseAuth.getInstance().currentUser?.email
                if (savedEmail != null) {
                    try {
                        val response = RetrofitClient.apiService.getProfileSecure(email = savedEmail)
                        
                        if (response.isSuccessful && response.body() != null) {
                            val me = response.body()!!.copy(isMe = true)
                            profileDao.insertProfiles(listOf(me))
                            _currentUser.value = me
                            initSignaling(me.email)
                            preferenceManager.setLoggedIn(true, me.email)
                        } else {
                            onLogout()
                        }
                    } catch (e: Exception) {
                        Log.e("Recovery", "Failed to recover user", e)
                    }
                } else {
                    onLogout()
                }
            }

            // Keep observing for any changes
            profileDao.getAllProfilesFlow().collect { list ->
                list.find { it.isMe }?.let { me ->
                    if (_currentUser.value != me) {
                        _currentUser.value = me
                        initSignaling(me.email)
                        preferenceManager.saveProfileCache(Gson().toJson(me))
                        try { 
                            val meWithDevice = me.copy(deviceId = myDeviceId)
                            RetrofitClient.apiService.saveProfileSecure(profile = meWithDevice)
                        } catch(e: Exception) {
                            Log.e("DiscoveryViewModel", "Auto-sync failed", e)
                        }
                    }
                }
            }
        }
    }

    private fun triggerCloudSync() {
        viewModelScope.launch {
            _currentUser.value?.let { me ->
                try { RetrofitClient.apiService.saveProfileSecure(profile = me.copy(deviceId = myDeviceId)) } catch(e: Exception) {}
            }
        }
    }

    fun onLike(p: UserProfile) { 
        viewModelScope.launch { 
            val me = _currentUser.value ?: return@launch
            
            // 1. SILENT DISCOVERY HIDE (Temporary UI hide)
            _remoteProfiles.value = _remoteProfiles.value.filter { it.email != p.email }

            try { 
                // 2. ATOMIC SERVER ACTION (Deducts coins, saves like, checks match)
                val response = RetrofitClient.apiService.swipeAction(SwipeActionRequest(p.email.trim().lowercase(), "LIKE"))
                
                if (response.isSuccessful && response.body() != null) {
                    val res = response.body()!!
                    
                    // 3. UPDATE LIVE STATE FROM SERVER RESPONSE
                    val newCoins = res.coins ?: (me.coins - 1)
                    val newMe = me.copy(coins = newCoins)
                    _currentUser.value = newMe
                    profileDao.updateProfile(newMe)

                    val status = if (res.matched == true) "ACCEPTED" else "LIKE"
                    profileDao.insertProfiles(listOf(p.copy(email = p.email.trim().lowercase(), connectionStatus = status)))

                    if (res.matched == true) {
                        withContext(Dispatchers.Main) { Toast.makeText(app, "It's a Match! ❤️", Toast.LENGTH_LONG).show() }
                    }
                } else {
                    Toast.makeText(app, "Insufficient coins or Error", Toast.LENGTH_SHORT).show()
                    refreshFeed() // Profile wapas dikhao discovery mein
                }
            } catch (e: Exception) { Log.e("Action", "Like sync failed", e) }
        } 
    }

    fun onSuperLike(p: UserProfile, onFail: (String) -> Unit) {
        viewModelScope.launch {
            val me = _currentUser.value ?: return@launch
            
            _remoteProfiles.value = _remoteProfiles.value.filter { it.email != p.email }

            try {
                val response = RetrofitClient.apiService.swipeAction(SwipeActionRequest(p.email.trim().lowercase(), "SUPERLIKE"))
                if (response.isSuccessful && response.body() != null) {
                    val res = response.body()!!
                    
                    val newCoins = res.coins ?: (me.coins - 10)
                    val newMe = me.copy(coins = newCoins)
                    _currentUser.value = newMe
                    profileDao.updateProfile(newMe)

                    val status = if (res.matched == true) "ACCEPTED" else "SUPERLIKE"
                    profileDao.insertProfiles(listOf(p.copy(email = p.email.trim().lowercase(), connectionStatus = status)))

                    if (res.matched == true) {
                        withContext(Dispatchers.Main) { Toast.makeText(app, "It's a Match! 🌟", Toast.LENGTH_LONG).show() }
                    }
                } else {
                    onFail("Insufficient coins.")
                    refreshFeed()
                }
            } catch (e: Exception) { Log.e("Action", "Superlike sync failed", e) }
        }
    }
    fun onAccept(p: UserProfile) { 
        viewModelScope.launch { 
            val me = _currentUser.value?.email ?: ""
            if (me.isBlank()) return@launch
            try { 
                // 1. Local Update (Mirroring Server Expectation)
                profileDao.insertProfiles(listOf(p.copy(connectionStatus = "ACCEPTED")))
                
                // 2. Cloud Update (Atomic Accept/Match on Server)
                val response = RetrofitClient.apiService.swipeAction(SwipeActionRequest(p.email.trim().lowercase(), "ACCEPT"))
                
                if (response.isSuccessful) {
                    Log.d("MatchLogic", "Successfully matched with ${p.email}")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(app, "It's a Match! ❤️", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) { Log.e("API", "Accept fail", e) } 
        } 
    }

    fun deactivateAccount() {
        viewModelScope.launch {
            try {
                val res = RetrofitClient.apiService.deactivateAccount()
                if (res.isSuccessful) {
                    onLogout()
                }
            } catch (e: Exception) { Log.e("Account", "Deactivate fail") }
        }
    }

    fun requestDeletion() {
        viewModelScope.launch {
            try {
                val res = RetrofitClient.apiService.requestDeletion()
                if (res.isSuccessful) {
                    onLogout()
                }
            } catch (e: Exception) { Log.e("Account", "Delete request fail") }
        }
    }

    fun reportUser(targetEmail: String, reason: String) {
        viewModelScope.launch {
            try {
                RetrofitClient.apiService.reportUser(ReportRequest(reporterEmail = _currentUser.value?.email ?: "", targetEmail = targetEmail, reason = reason))
                Toast.makeText(app, "Report submitted successfully.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Log.e("Report", "Fail") }
        }
    }
    fun onReject(p: UserProfile) { 
        viewModelScope.launch { 
            try { 
                profileDao.insertProfiles(listOf(p.copy(connectionStatus = "REJECTED")))
                RetrofitClient.apiService.swipeAction(SwipeActionRequest(p.email, "REJECTED"))
            } catch (e: Exception) {} 
        } 
    }
    fun onDislike(p: UserProfile) { 
        viewModelScope.launch { 
            // Optimistic Reject
            profileDao.insertProfiles(listOf(p.copy(connectionStatus = "REJECTED")))
            _remoteProfiles.value = _remoteProfiles.value.filter { it.email != p.email }
            
            try {
                RetrofitClient.apiService.swipeAction(SwipeActionRequest(p.email, "REJECTED"))
            } catch (e: Exception) { Log.e("Action", "Reject sync fail") }
        } 
    }
    fun onUndoDislike(p: UserProfile) {
        viewModelScope.launch {
            profileDao.insertProfiles(listOf(p.copy(connectionStatus = "NONE")))
            try {
                RetrofitClient.apiService.swipeAction(SwipeActionRequest(p.email, "CANCEL"))
            } catch (e: Exception) {}
        }
    }
    fun onCancelRequest(p: UserProfile) { 
        viewModelScope.launch { 
            profileDao.insertProfiles(listOf(p.copy(connectionStatus = "NONE")))
            try { 
                RetrofitClient.apiService.swipeAction(SwipeActionRequest(p.email, "CANCEL"))
            } catch (e: Exception) {} 
        } 
    }
    suspend fun checkEmailExists(email: String): Boolean { 
        if (email.isBlank()) return false
        return try { 
            val res = RetrofitClient.apiService.getProfileSecure(email = email)
            res.isSuccessful && res.body() != null
        } catch (e: Exception) { false } 
    }
    suspend fun verifyPassword(email: String, pass: String): Boolean { 
        if (email.isBlank()) return false
        _isAuthLoading.value = true
        return try { 
            val response = RetrofitClient.apiService.login(LoginRequest(email, pass))
            if (response.isSuccessful && response.body() != null) {
                val auth = response.body()!!
                RetrofitClient.saveToken(auth.token)
                
                // NEW: Save the full user data from login response into local DB
                val fullUser = auth.user.copy(isMe = true)
                profileDao.insertProfiles(listOf(fullUser))
                _currentUser.value = fullUser
                preferenceManager.setLoggedIn(true, fullUser.email)
                refreshFeed()
                
                true
            } else if (response.code() == 401) {
                Toast.makeText(app, "Incorrect password", Toast.LENGTH_SHORT).show()
                false
            } else if (response.code() == 404) {
                Toast.makeText(app, "User not found", Toast.LENGTH_SHORT).show()
                false
            } else {
                Toast.makeText(app, "Server Error: ${response.code()}", Toast.LENGTH_SHORT).show()
                false
            }
        } catch(e: Exception) { 
            Log.e("Auth", "Verify fail", e)
            Toast.makeText(app, "Connection Error. Check Server.", Toast.LENGTH_SHORT).show()
            false 
        }
        finally { _isAuthLoading.value = false }
    }
    fun setRegistrationData(n: String, a: Int, e: String, p: String, c: String, s: String, g: String, pass: String, ref: String = "") { 
        _regData.value = RegistrationData(name = n, age = a, email = e, phone = p, country = c, state = s, gender = g, password = pass, referredBy = ref) 
    }
    
    fun onAdRewarded() {
        viewModelScope.launch {
            _currentUser.value?.let { me ->
                val updated = me.copy(coins = me.coins + 5, deviceId = myDeviceId)
                profileDao.updateProfile(updated)
                try {
                    RetrofitClient.apiService.saveProfileSecure(profile = updated)
                } catch (e: Exception) {}
                Toast.makeText(app, "5 Coins Earned! 🪙", Toast.LENGTH_LONG).show()
                loadCurrentUser()
            }
        }
    }

    suspend fun buyAdFreeSubscription() {
        val me = _currentUser.value ?: return
        if (me.coins >= 250) {
            val updated = me.copy(coins = me.coins - 250, isAdFree = true, deviceId = myDeviceId)
            profileDao.updateProfile(updated)
            try {
                RetrofitClient.apiService.saveProfileSecure(profile = updated)
            } catch (e: Exception) {}
            Toast.makeText(app, "Ad-Free Activated! 🚀", Toast.LENGTH_LONG).show()
            loadCurrentUser()
        } else {
            Toast.makeText(app, "Need 250 coins.", Toast.LENGTH_SHORT).show()
        }
    }

    fun setFetchedLocation(country: String, state: String) {
        _fetchedCountry.value = country
        _fetchedState.value = state
    }

    fun setSignUpMode(isSignUp: Boolean) {
        _isSignUpMode.value = isSignUp
    }

    fun detectUserLocation(onResolutionRequired: (com.google.android.gms.common.api.ResolvableApiException) -> Unit, onFail: (String) -> Unit) {
        viewModelScope.launch {
            _isDetectingLocation.value = true
            val helper = LocationHelper(app)
            val result = helper.fetchLocation()
            
            result.fold(
                onSuccess = { loc ->
                    _fetchedCountry.value = loc.country
                    _fetchedState.value = loc.state
                    Log.d("Location", "Detected: ${loc.country}, ${loc.state}")
                },
                onFailure = { error ->
                    if (error is com.google.android.gms.common.api.ResolvableApiException) {
                        onResolutionRequired(error)
                    } else {
                        onFail(error.message ?: "Unknown location error")
                    }
                }
            )
            _isDetectingLocation.value = false
        }
    }

    fun setFetchingIdentity(fetching: Boolean) {
        _isFetchingIdentity.value = fetching
    }

    suspend fun checkRegistrationValidity(email: String, phone: String): String? {
        _isAuthLoading.value = true
        return try {
            val response = withTimeout(60000) { 
                RetrofitClient.apiService.getProfilePublic(email = email) 
            }
            if (response.isSuccessful && response.body() != null) return "Email already registered. Please Sign In."
            
            // If response is NOT successful and not 404/200-null, it's a real connection/server error
            if (!response.isSuccessful && response.code() != 404) {
                 return "Server Error (${response.code()}). Check your internet or server."
            }

            val phoneResponse = withTimeout(60000) { 
                RetrofitClient.apiService.checkPhoneSecure(phone = phone) 
            }
            if (phoneResponse.isSuccessful && phoneResponse.body()?.get("exists") == true) return "Mobile number already in use."
            null
        } catch (e: Exception) { 
            Log.e("Auth", "Check fail", e)
            "Connection Error. Please check your internet or server." 
        }
        finally { _isAuthLoading.value = false }
    }

    fun clearFetchedIdentity() {
        _fetchedEmail.value = ""
        _fetchedPhone.value = ""
    }

    fun dismissWelcomeMessage() {
        _showWelcomeMessage.value = false
    }
    suspend fun completeRegistration(localUri: String): String? { 
        val d = _regData.value ?: RegistrationData()
        _isAuthLoading.value = true
        
        try {
            // 1. COMPRESS AND UPLOAD PHOTO
            var cloudPhotoUrl = ""
            try {
                withTimeout(120000) { // 2 minutes timeout
                    val contentResolver = app.contentResolver
                    val inputStream = contentResolver.openInputStream(Uri.parse(localUri))
                    val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                    
                    // Resize and Compress to under 1MB
                    val out = java.io.ByteArrayOutputStream()
                    originalBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, out)
                    val bytes = out.toByteArray()
                    
                    val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                    val uploadResult = RetrofitClient.apiService.uploadImageSecure(request = ImageUploadRequest(base64))
                    
                    if (uploadResult.isSuccessful) {
                        cloudPhotoUrl = uploadResult.body()?.get("url") ?: throw Exception("URL missing")
                    } else {
                        throw Exception("Server rejected upload: ${uploadResult.code()}")
                    }
                }
            } catch (e: Exception) {
                Log.e("Signup", "Photo upload failed", e)
                return "Photo upload failed or timed out. Please try again with better internet."
            }

            // 2. Validation Check via Server
            try {
                val signupRes = withTimeout(60000) {
                    RetrofitClient.apiService.signup(
                        SignupRequest(
                            email = d.email, 
                            password = d.password, 
                            name = d.name, 
                            age = d.age, 
                            gender = d.gender,
                            phoneNumber = d.phone,
                            photoUri = cloudPhotoUrl,
                            country = d.country,
                            state = d.state
                        )
                    )
                }
                if (signupRes.isSuccessful && signupRes.body() != null) {
                    val auth = signupRes.body()!!
                    RetrofitClient.saveToken(auth.token)
                } else if (signupRes.code() == 409) {
                    return "Email already registered. Please Sign In."
                } else {
                    return "Server rejected registration: ${signupRes.code()}. Please try again."
                }
            } catch (e: Exception) { 
                Log.e("Signup", "Secure signup fail", e)
                return "Registration request failed. Check your connection."
            }

            profileDao.deleteMyProfile()
            
            // Generate Unique Referral Code
            val myRefCode = "TINK-" + java.util.UUID.randomUUID().toString().take(4).uppercase()
            
            val user = UserProfile(
                userId = d.email, 
                name = d.name.ifBlank { "Tinklet User" }, 
                age = d.age, 
                email = d.email, 
                phoneNumber = d.phone,
                photoUri = cloudPhotoUrl, 
                isMe = true, 
                connectionStatus = "NONE", 
                coins = 25, 
                country = d.country, 
                state = d.state, 
                gender = d.gender, 
                password = d.password,
                referralCode = myRefCode,
                referredBy = d.referredBy
            )
            
            profileDao.insertProfiles(listOf(user))
            
            // Save to Cloud via SECURE SERVER
            val userWithDevice = user.copy(deviceId = myDeviceId)
            try {
                withTimeout(30000) {
                    // Update: Pass entire profile including photoUri to cloud
                    RetrofitClient.apiService.saveProfileSecure(profile = userWithDevice)
                    if (d.referredBy.isNotBlank() && d.referredBy != "-") {
                        RetrofitClient.apiService.creditReferralSecure(request = ReferralRequest(d.referredBy, myDeviceId))
                    }
                }
            } catch (e: Exception) { Log.e("CloudSync", "Secure save fail", e) }
            
            profileDao.updateProfile(userWithDevice)
            preferenceManager.setLoggedIn(true, user.email)
            _showWelcomeMessage.value = true 
            loadCurrentUser() 
            return null 
        } finally {
            _isAuthLoading.value = false
        }
    }
    fun onLoginSuccess(name: String?, email: String?, phone: String?, password: String = "") { 
        _isFetchingIdentity.value = false 
        _isAuthLoading.value = true
        viewModelScope.launch { 
            try {
                phone?.let { p ->
                    if (p.isNotBlank()) {
                        val phoneCheckRes = try { 
                            withTimeout(15000) {
                                RetrofitClient.apiService.checkPhoneSecure(phone = p)
                            }
                        } catch(e: Exception) { null }
                        
                        if (phoneCheckRes?.isSuccessful == true && phoneCheckRes.body()?.get("exists") == true) {
                            Toast.makeText(app, "Mobile already in use.", Toast.LENGTH_LONG).show()
                            _fetchedPhone.value = ""
                        } else {
                            _fetchedPhone.value = p
                        }
                    }
                }

                val emailStr = email.orEmpty()
                if (emailStr.isBlank()) return@launch
                _fetchedEmail.value = emailStr

                val existing = profileDao.getProfileByEmail(emailStr) ?: try { 
                val res = RetrofitClient.apiService.getProfilePublic(email = emailStr)
                if (res.isSuccessful) res.body() else null
            } catch (e: Exception) { null }
                
                if (existing != null) { 
                    if (_isSignUpMode.value) {
                        Toast.makeText(app, "Account exists. Logging in...", Toast.LENGTH_SHORT).show()
                        _isSignUpMode.value = false
                    }

                    // REMOVED local password check to trust server/oauth result
                    val me = existing.copy(isMe = true)
                    profileDao.updateProfile(me)
                    _currentUser.value = me
                    preferenceManager.setLoggedIn(true, me.email)
                    refreshFeed()
                } else { 
                    _regData.value = RegistrationData(email = emailStr, name = name.orEmpty(), phone = _fetchedPhone.value) 
                }
            } finally {
                _isAuthLoading.value = false
            }
        } 
    }

    fun onResetViewed() { viewModelScope.launch { profileDao.resetViewedOnly() } }
    fun onLogout() { 
        viewModelScope.launch { 
            FirebaseAuth.getInstance().signOut()
            profileDao.deleteAllProfiles() // Clear all local data on logout
            preferenceManager.clearProfileCache()
            _currentUser.value = null
            
            // CLEAR FETCHED IDENTITY STATES
            _fetchedEmail.value = ""
            _fetchedPhone.value = ""
            _fetchedCountry.value = ""
            _fetchedState.value = ""
            _regData.value = null
            
            signaling?.disconnect()
            signaling = null
            CallSignalingHolder.signalingClient = null
        } 
    }
    fun updateProfileDetails(u: UserProfile) { 
        viewModelScope.launch { 
            Toast.makeText(app, "Syncing with Cloud...", Toast.LENGTH_SHORT).show()
            
            // Age calculation if DOB exists
            var updatedUser = if (u.dob.isNotBlank()) {
                try {
                    val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                    val birthDate = sdf.parse(u.dob)
                    if (birthDate != null) {
                        val calendar = java.util.Calendar.getInstance()
                        val currentYear = calendar.get(java.util.Calendar.YEAR)
                        calendar.time = birthDate
                        val birthYear = calendar.get(java.util.Calendar.YEAR)
                        u.copy(age = currentYear - birthYear)
                    } else u
                } catch (e: Exception) { u }
            } else u
            
            // Ensure Device ID is present
            if (updatedUser.deviceId.isBlank()) {
                updatedUser = updatedUser.copy(deviceId = myDeviceId)
            }

            profileDao.updateProfile(updatedUser)
            
            viewModelScope.launch {
                try { 
                    RetrofitClient.apiService.saveProfileSecure(profile = updatedUser)
                } catch(e: Exception) {
                    Log.e("Retrofit_Sync", "Retrofit update failed", e)
                }
            }
            
            loadCurrentUser() 
        } 
    }

    fun updateProfilePhoto(uri: String) { 
        viewModelScope.launch {
            _currentUser.value?.let { 
                val localCached = it.copy(photoUri = uri)
                _currentUser.value = localCached
                profileDao.updateProfile(localCached)
            }
            
            try {
                var cloudPhotoUrl = ""
                withTimeout(120000) {
                    val contentResolver = app.contentResolver
                    val inputStream = contentResolver.openInputStream(Uri.parse(uri))
                    val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                    
                    val out = java.io.ByteArrayOutputStream()
                    originalBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, out)
                    val bytes = out.toByteArray()
                    
                    val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                    val result = RetrofitClient.apiService.uploadImageSecure(request = ImageUploadRequest(base64))
                    cloudPhotoUrl = if (result.isSuccessful) result.body()?.get("url") ?: throw Exception("URL missing") else throw Exception("Upload failed")
                }
                
                _currentUser.value?.let { 
                    val up = it.copy(photoUri = cloudPhotoUrl, deviceId = myDeviceId)
                    profileDao.updateProfile(up)
                    RetrofitClient.apiService.saveProfileSecure(profile = up)
                    loadCurrentUser() 
                }
            } catch (e: Exception) {
                Log.e("Upload", "Primary photo failed", e)
                Toast.makeText(app, "Photo Upload Failed or Timed out!", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    fun updateFcmToken(token: String) {
        viewModelScope.launch {
            val myEmail = _currentUser.value?.email ?: preferenceManager.userEmail.first()
            if (myEmail != null) {
                try {
                    RetrofitClient.apiService.updateFcmToken(request = FcmTokenRequest(myEmail, token))
                } catch (e: Exception) {
                    Log.e("FCM", "Failed to update token on server")
                }
            }
        }
    }

    fun updateSecondaryPhotos(uris: List<Uri>) {
        Toast.makeText(app, "Compressing & Uploading Photos...", Toast.LENGTH_SHORT).show()
        viewModelScope.launch {
            val me = profileDao.getMyProfile() ?: _currentUser.value ?: return@launch
            val currentPhotos = me.secondaryPhotos.toMutableList()
            
            uris.forEach { uri ->
                try {
                    withTimeout(60000) { // 60s per gallery photo
                        val contentResolver = app.contentResolver
                        val inputStream = contentResolver.openInputStream(uri)
                        val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                        
                        val out = java.io.ByteArrayOutputStream()
                        originalBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, out)
                        val bytes = out.toByteArray()
                        
                        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                        val result = RetrofitClient.apiService.uploadImageSecure(request = ImageUploadRequest(base64))
                        
                        if (result.isSuccessful && result.body()?.get("url") != null) {
                            currentPhotos.add(result.body()!!["url"]!!)
                        }
                    }
                } catch (e: Exception) { Log.e("Upload", "Gallery item fail", e) }
            }
            
            val updated = me.copy(secondaryPhotos = currentPhotos.distinct().take(10), deviceId = myDeviceId)
            profileDao.updateProfile(updated)
            _currentUser.value = updated
            RetrofitClient.apiService.saveProfileSecure(profile = updated)
            loadCurrentUser()
        }
    }
    fun deleteSecondaryPhoto(url: String) {
        viewModelScope.launch {
            val me = profileDao.getMyProfile() ?: _currentUser.value ?: return@launch
            val updatedPhotos = me.secondaryPhotos.toMutableList().apply { remove(url) }
            val updated = me.copy(secondaryPhotos = updatedPhotos, deviceId = myDeviceId)
            
            profileDao.updateProfile(updated)
            try { 
                RetrofitClient.apiService.saveProfileSecure(profile = updated)
            } catch (e: Exception) {
                Log.e("DiscoveryViewModel", "Cloud delete sync failed", e)
            }
            loadCurrentUser()
        }
    }
    
    fun deleteProfileData() { 
        viewModelScope.launch { 
            FirebaseAuth.getInstance().signOut()
            profileDao.deleteAllProfiles()
            preferenceManager.clearProfileCache()
            _currentUser.value = null
        } 
    }
    private fun seedMockData() {
        // Test profiles disabled as per user request to focus on real data
    }
    fun getMessages(email: String): Flow<List<ChatMessage>> = chatMessageDao.getMessagesForMatch(email).map { it.reversed() }
    fun sendMessage(partnerEmail: String, text: String, isMe: Boolean = true) { 
        viewModelScope.launch { 
            val myId = _currentUser.value?.email ?: ""
            if (myId.isBlank()) return@launch

            val msg = ChatMessage(matchId = partnerEmail, senderId = if(isMe) "ME" else "OTHER", text = text)
            chatMessageDao.insertMessage(msg)

            try {
                RetrofitClient.apiService.saveMessageSecure(request = MessageRequest(partnerEmail, myId, text))
                signaling?.sendSignal(partnerEmail, "chat_message", sdp = text, messageId = msg.messageId)
            } catch (e: Exception) { Log.e("DiscoveryViewModel", "Cloud save fail") }
        } 
    }

    fun markMessagesAsRead(partnerEmail: String) {
        viewModelScope.launch {
            chatMessageDao.markAllAsRead(partnerEmail)
            // Ideally we should send a list of messageIds or just a "mark all read" signal.
            // For simplicity, we'll signal the last received message or a generic read signal.
            // However, the signaling client supports message-specific read receipts.
            // Let's just mark the most recent "OTHER" messages as read.
            chatMessageDao.getMessagesForMatch(partnerEmail).first().filter { it.senderId == "OTHER" && it.status != "READ" }.forEach {
                signaling?.sendReadReceipt(partnerEmail, it.messageId)
            }
        }
    }

    fun sendImageMessage(partnerEmail: String, uri: Uri) {
        viewModelScope.launch {
            val myId = _currentUser.value?.email ?: ""
            if (myId.isBlank()) return@launch

            _uploadError.value = null
            _uploadProgress.value = 0
            
            try {
                val inputStream = app.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes() ?: return@launch
                val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                
                _uploadProgress.value = 50
                val result = RetrofitClient.apiService.uploadImageSecure(request = ImageUploadRequest(base64))
                val url = if (result.isSuccessful) result.body()?.get("url") else null
                if (url == null) throw Exception("Upload failed")
                
                _uploadProgress.value = 100
                _uploadProgress.value = null
                sendMessage(partnerEmail, "[IMAGE]$url")
            } catch (e: Exception) {
                _uploadProgress.value = null
                _uploadError.value = "Upload failed. Tap to retry."
                Log.e("Chat", "Secure image upload failed", e)
            }
        }
    }
    fun markAsViewed(p: UserProfile) { 
        viewModelScope.launch { 
            if (p.connectionStatus == "NONE") {
                profileDao.updateProfile(p.copy(connectionStatus = "VIEWED"))
            }
        } 
    }
    fun sendGift(email: String, gift: String) { 
        viewModelScope.launch { 
            val me = _currentUser.value ?: return@launch
            if (me.coins >= 1) {
                val updatedMe = me.copy(coins = me.coins - 1)
                profileDao.updateProfile(updatedMe)
                try {
                    RetrofitClient.apiService.saveProfileSecure(profile = updatedMe)
                } catch (e: Exception) {}
                sendMessage(email, "Sent you a $gift 🎁") 
                loadCurrentUser()
            } else {
                Toast.makeText(app, "Need 1 coin to send a gift", Toast.LENGTH_SHORT).show()
            }
        } 
    }
    fun updateFilters(c: String, s: String, a: IntRange, g: String, r: String, h: String, l: String, i: String) { }

    // Block & Report Logic
    fun blockUser(email: String) {
        viewModelScope.launch {
            profileDao.getProfileByEmail(email)?.let {
                profileDao.updateProfile(it.copy(isBlocked = true))
                signaling?.sendSignal(email, "block_user")
                Toast.makeText(app, "${it.name} Blocked", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun unblockUser(email: String) {
        viewModelScope.launch {
            profileDao.getProfileByEmail(email)?.let {
                profileDao.updateProfile(it.copy(isBlocked = false))
                Toast.makeText(app, "${it.name} Unblocked", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun reportUser(email: String) {
        viewModelScope.launch {
            val me = _currentUser.value?.email ?: return@launch
            profileDao.getProfileByEmail(email)?.let {
                profileDao.updateProfile(it.copy(reportCount = it.reportCount + 1))
                try {
                    RetrofitClient.apiService.saveReportSecure(request = ReportRequest(me, email))
                    Toast.makeText(app, "User Reported. We will review this.", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) { Log.e("Report", "Sync fail", e) }
            }
        }
    }

    // Message Management
    fun deleteMessage(msg: ChatMessage, forEveryone: Boolean) {
        viewModelScope.launch {
            if (forEveryone && msg.senderId == "ME") {
                chatMessageDao.updateMessage(msg.copy(isDeletedForEveryone = true, text = "You deleted this message"))
                signaling?.sendDeleteMessage(msg.matchId, msg.timestamp)
                // TODO: Add cloud sync for single message deletion if needed
            } else {
                chatMessageDao.deleteMessage(msg.id)
            }
        }
    }

    fun editMessage(msg: ChatMessage, newText: String) {
        viewModelScope.launch {
            chatMessageDao.updateMessage(msg.copy(text = newText, isEdited = true))
            if (msg.senderId == "ME") {
                signaling?.sendEditMessage(msg.matchId, msg.timestamp, newText)
                // TODO: Add cloud sync for edited text
            }
        }
    }

    fun deleteChat(partnerEmail: String) {
        viewModelScope.launch {
            chatMessageDao.deleteChat(partnerEmail)
            // Server-side cleanup can be added to secure API if needed
            Toast.makeText(app, "Chat Cleared Permanently", Toast.LENGTH_SHORT).show()
        }
    }

    // Media Logic
    fun sendVoiceNote(partnerEmail: String, file: java.io.File, duration: Int) {
        viewModelScope.launch {
            val myId = _currentUser.value?.email ?: return@launch
            try {
                val bytes = file.readBytes()
                val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                
                val result = RetrofitClient.apiService.uploadMediaSecure(
                    request = MediaUploadRequest(base64, "audio", partnerEmail)
                )
                val url = if (result.isSuccessful) result.body()?.get("url") else null
                if (url == null) throw Exception("Upload failed")
                
                val msg = ChatMessage(matchId = partnerEmail, senderId = "ME", voiceUrl = url, duration = duration)
                chatMessageDao.insertMessage(msg)
                signaling?.sendSignal(partnerEmail, "voice_note", sdp = url)
                file.delete()
            } catch (e: Exception) {
                Log.e("Media", "Voice upload failed", e)
                Toast.makeText(app, "Voice upload failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun sendVideoNote(partnerEmail: String, file: java.io.File, duration: Int) {
        viewModelScope.launch {
            val myId = _currentUser.value?.email ?: return@launch
            try {
                val bytes = file.readBytes()
                val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                
                val result = RetrofitClient.apiService.uploadMediaSecure(
                    request = MediaUploadRequest(base64, "video", partnerEmail)
                )
                val url = if (result.isSuccessful) result.body()?.get("url") else null
                if (url == null) throw Exception("Upload failed")
                
                val msg = ChatMessage(matchId = partnerEmail, senderId = "ME", videoNoteUrl = url, duration = duration)
                chatMessageDao.insertMessage(msg)
                signaling?.sendSignal(partnerEmail, "video_note", sdp = url)
                file.delete()
            } catch (e: Exception) {
                Log.e("Media", "Video upload failed", e)
                Toast.makeText(app, "Video upload failed", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
