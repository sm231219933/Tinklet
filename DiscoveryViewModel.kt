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
    fun setAppLanguage(lang: String) { viewModelScope.launch { preferenceManager.setAppLanguage(lang) } }

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
                val expiry = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000)
                val updatedMe = me.copy(coins = me.coins - cost, badgeType = type, badgeExpiry = expiry)
                profileDao.updateProfile(updatedMe)
                try { RetrofitClient.apiService.saveProfileSecure(profile = updatedMe) } catch (e: Exception) {}
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

    val profiles: StateFlow<List<UserProfile>> = combine(
        profileDao.getAllProfilesFlow(), _remoteProfiles, _currentUser
    ) { local, remote, me ->
        val meEmail = me?.email ?: ""
        val swipedEmails = local.filter {
            it.connectionStatus == "LIKED" ||
            it.connectionStatus == "LIKE" ||
            it.connectionStatus == "REJECTED" ||
            it.connectionStatus == "ACCEPTED" ||
            it.connectionStatus == "SUPERLIKE_SENT"
        }.map { it.email }.toSet()
        val combined = remote.filter { it.email != meEmail && !swipedEmails.contains(it.email) }
        combined.distinctBy { it.email }.sortedByDescending { it.lastActive }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    val sentRequests: StateFlow<List<UserProfile>> = profileDao.getAllProfilesFlow().map { list ->
        list.filter { (it.connectionStatus == "LIKED" || it.connectionStatus == "LIKE") && !it.isMe }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val rejectedProfiles: StateFlow<List<UserProfile>> = profileDao.getAllProfilesFlow().map { list ->
        list.filter { it.connectionStatus == "REJECTED" && !it.isMe }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val superLikedProfiles: StateFlow<List<UserProfile>> = profileDao.getAllProfilesFlow().map { list ->
        list.filter { it.connectionStatus == "SUPERLIKE_SENT" && !it.isMe }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val incomingRequests: StateFlow<List<UserProfile>> = profileDao.getAllProfilesFlow().map { list ->
        list.filter { (it.connectionStatus == "SUPERLIKE" || it.connectionStatus == "PENDING") && !it.isMe }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

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

        viewModelScope.launch {
            preferenceManager.jwtToken.collect { token -> RetrofitClient.setToken(token) }
        }

        viewModelScope.launch {
            delay(1000)
            startBackendSyncLoop()
            loadCurrentUser()
        }

        seedMockData()
        onResetViewed()

        viewModelScope.launch {
            preferenceManager.interactiveSounds.collect { enabled -> SoundManager.isSoundEnabled = enabled }
        }
    }

    private fun initSignaling(userId: String) {
        if (userId.isBlank()) return
        Log.d("Signaling_Trace", "Initializing Socket.io Signaling for $userId")
        signaling = SignalingClient("http://15.206.14.213:3000", userId, object : SignalingClient.SignalingListener {
            override fun onIncomingCall(fromUserId: String, fromUserName: String, offer: String, callType: String) {
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
            override fun onCallAnswered(answer: String) { NotificationHelper.cancelCallNotification(app) }
            override fun onCallRejected() { NotificationHelper.cancelCallNotification(app) }
            override fun onCallEnded() { NotificationHelper.cancelCallNotification(app) }
            override fun onIceCandidateReceived(candidate: String) {}
            override fun onChatMessageReceived(fromUserId: String, text: String, messageId: String?) {
                viewModelScope.launch {
                    val msg = ChatMessage(matchId = fromUserId, senderId = "OTHER", text = text, messageId = messageId ?: java.util.UUID.randomUUID().toString(), status = "DELIVERED")
                    chatMessageDao.insertMessage(msg)
                    messageId?.let { signaling?.sendDeliveryReceipt(fromUserId, it) }
                    val senderName = profileDao.getProfileByEmail(fromUserId)?.name ?: "New Message"
                    NotificationHelper.showMessageNotification(app, senderName, text, fromUserId)
                }
            }
            override fun onMessageDelivered(fromUserId: String, messageId: String) {
                viewModelScope.launch { chatMessageDao.updateMessageStatus(messageId, "DELIVERED") }
            }
            override fun onMessageRead(fromUserId: String, messageId: String) {
                viewModelScope.launch { chatMessageDao.updateMessageStatus(messageId, "READ") }
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
                    profileDao.getProfileByEmail(fromUserId)?.let { profileDao.updateProfile(it.copy(isBlocked = true)) }
                }
            }
            override fun onCallFailed(reason: String) { Log.e("Signaling_Trace", "Call Failed: $reason") }
            override fun onConnected() { Log.d("Signaling_Trace", "Signaling Socket CONNECTED successfully") }
        })
        signaling?.connect()
        CallSignalingHolder.signalingClient = signaling
    }

    fun refreshFeed() {
        viewModelScope.launch {
            try {
                val response = withTimeoutOrNull(10000) { RetrofitClient.apiService.getFeed() }
                if (response?.isSuccessful == true) {
                    val remote = response.body()?.feed ?: emptyList()
                    val myEmail = _currentUser.value?.email ?: ""
                    val filtered = remote.filter { it.email != myEmail }
                    _remoteProfiles.value = filtered
                    Log.d("Feed", "Feed updated with ${filtered.size} profiles")
                }
            } catch (e: Exception) { Log.e("Feed", "Refresh fail", e) }
        }
    }

    private fun startBackendSyncLoop() {
        viewModelScope.launch {
            while (true) {
                try {
                    val myId = _currentUser.value?.email ?: ""
                    if (myId.isNotBlank()) {
                        if (signaling == null) initSignaling(myId)
                        refreshFeed()

                        val syncRes = RetrofitClient.apiService.syncAll()
                        if (syncRes.isSuccessful && syncRes.body() != null) {
                            val data = syncRes.body()!!

                            // Sync my sent actions. LIKE is normalized to LIKED for the local UI.
                            data.sent.forEach { record ->
                                val targetEmail = record.toUserId.trim().lowercase()
                                val local = profileDao.getProfileByEmail(targetEmail)
                                val status = when (record.action.uppercase()) {
                                    "LIKE" -> "LIKED"
                                    "SUPERLIKE" -> "SUPERLIKE_SENT"
                                    else -> record.action.uppercase()
                                }
                                if (local == null) {
                                    val pRes = try { RetrofitClient.apiService.getProfileSecure(targetEmail) } catch(e: Exception) { null }
                                    if (pRes?.isSuccessful == true && pRes.body() != null) {
                                        profileDao.insertProfiles(listOf(pRes.body()!!.copy(connectionStatus = status, isMe = false)))
                                    }
                                } else if (local.connectionStatus != status) {
                                    profileDao.insertProfiles(listOf(local.copy(connectionStatus = status, isMe = false)))
                                }
                            }

                            // IMPORTANT: always update existing local profiles too.
                            // Previously incoming requests were inserted only when local == null,
                            // so a profile already viewed in Discovery never appeared in Inbox.
                            data.incoming.forEach { record ->
                                val fromEmail = record.fromUserId.trim().lowercase()
                                val local = profileDao.getProfileByEmail(fromEmail)
                                val status = if (record.action.uppercase() == "SUPERLIKE") "SUPERLIKE" else "PENDING"
                                if (local == null) {
                                    val pRes = try { RetrofitClient.apiService.getProfileSecure(fromEmail) } catch(e: Exception) { null }
                                    if (pRes?.isSuccessful == true && pRes.body() != null) {
                                        profileDao.insertProfiles(listOf(pRes.body()!!.copy(connectionStatus = status, isMe = false)))
                                    }
                                } else if (!local.isMe) {
                                    profileDao.insertProfiles(listOf(local.copy(connectionStatus = status, isMe = false)))
                                }
                            }

                            val matchPartnerIds = data.matches.mapNotNull { m ->
                                val users = m["users"] as? List<*>
                                users?.filter { it != myId }?.firstOrNull() as? String
                            }
                            matchPartnerIds.forEach { partnerId ->
                                val local = profileDao.getProfileByEmail(partnerId)
                                if (local == null || local.connectionStatus != "ACCEPTED") {
                                    val pRes = try { RetrofitClient.apiService.getProfileSecure(partnerId) } catch(e: Exception) { null }
                                    if (pRes?.isSuccessful == true && pRes.body() != null) {
                                        profileDao.insertProfiles(listOf(pRes.body()!!.copy(connectionStatus = "ACCEPTED", isMe = false)))
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) { Log.e("CloudSync", "Deep sync fail", e) }
                delay(15000)
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

            preferenceManager.cachedProfile.first()?.let { json ->
                try {
                    val cached = Gson().fromJson(json, UserProfile::class.java)
                    if (_currentUser.value == null) {
                        _currentUser.value = cached
                        initSignaling(cached.email)
                    }
                } catch (e: Exception) { Log.e("Cache", "Failed to load cache") }
            }

            val directMe = profileDao.getMyProfile()
            if (directMe != null) {
                _currentUser.value = directMe
                initSignaling(directMe.email)
                preferenceManager.saveProfileCache(Gson().toJson(directMe))
                refreshFeed()
                val matchesCount = profileDao.getProfileCount()
                if (matchesCount <= 1) Log.w("Stability", "Data mismatch detected. Triggering deep sync loop.")
            } else {
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
                        } else onLogout()
                    } catch (e: Exception) { Log.e("Recovery", "Failed to recover user", e) }
                } else onLogout()
            }

            profileDao.getAllProfilesFlow().collect { list ->
                list.find { it.isMe }?.let { me ->
                    if (_currentUser.value != me) {
                        _currentUser.value = me
                        initSignaling(me.email)
                        preferenceManager.saveProfileCache(Gson().toJson(me))
                        try {
                            val meWithDevice = me.copy(deviceId = myDeviceId)
                            RetrofitClient.apiService.saveProfileSecure(profile = meWithDevice)
                        } catch(e: Exception) { Log.e("DiscoveryViewModel", "Auto-sync failed", e) }
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
            val targetEmail = p.email.trim().lowercase()

            // Server is the source of truth for coins. Do not reject using stale local coins.
            val response = try {
                RetrofitClient.apiService.swipeAction(SwipeActionRequest(targetEmail, "LIKE"))
            } catch (e: Exception) {
                Log.e("Action", "Like sync failed", e)
                Toast.makeText(app, "Connection error. Please try again.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            if (response.isSuccessful && response.body() != null) {
                val res = response.body()!!
                val serverCoins = res.coins ?: (me.coins - 1).coerceAtLeast(0)
                val newMe = me.copy(coins = serverCoins)
                _currentUser.value = newMe
                profileDao.updateProfile(newMe)

                // Use the status expected by sentRequests/profiles.
                val status = if (res.matched) "ACCEPTED" else "LIKED"
                profileDao.insertProfiles(listOf(p.copy(email = targetEmail, connectionStatus = status, isMe = false)))
                _remoteProfiles.value = _remoteProfiles.value.filter { it.email.trim().lowercase() != targetEmail }

                if (res.matched) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(app, "It's a Match! ❤️", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                val message = response.errorBody()?.string()?.takeIf { it.isNotBlank() } ?: "Unable to Like this profile."
                Log.e("Action", "Like rejected: ${response.code()} $message")
                Toast.makeText(app, if (response.code() == 400) "Insufficient coins." else "Unable to Like. Please try again.", Toast.LENGTH_SHORT).show()
                refreshFeed()
            }
        }
    }

    fun onSuperLike(p: UserProfile, onFail: (String) -> Unit) {
        viewModelScope.launch {
            val me = _currentUser.value ?: return@launch
            val targetEmail = p.email.trim().lowercase()

            // Server is the source of truth for the 10-coin charge.
            val response = try {
                RetrofitClient.apiService.swipeAction(SwipeActionRequest(targetEmail, "SUPERLIKE"))
            } catch (e: Exception) {
                Log.e("Action", "Superlike sync failed", e)
                onFail("Connection error.")
                return@launch
            }

            if (response.isSuccessful && response.body() != null) {
                val res = response.body()!!
                val serverCoins = res.coins ?: (me.coins - 10).coerceAtLeast(0)
                val newMe = me.copy(coins = serverCoins)
                _currentUser.value = newMe
                profileDao.updateProfile(newMe)

                val status = if (res.matched) "ACCEPTED" else "SUPERLIKE_SENT"
                profileDao.insertProfiles(listOf(p.copy(email = targetEmail, connectionStatus = status, isMe = false)))
                _remoteProfiles.value = _remoteProfiles.value.filter { it.email.trim().lowercase() != targetEmail }

                if (res.matched) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(app, "It's a Match! 🌟", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                Log.e("Action", "Superlike rejected: ${response.code()}")
                onFail(if (response.code() == 400) "Insufficient coins." else "Unable to Super Like.")
                refreshFeed()
            }
        }
    }

    fun onAccept(p: UserProfile) {
        viewModelScope.launch {
            val me = _currentUser.value?.email ?: ""
            if (me.isBlank()) return@launch
            try {
                profileDao.insertProfiles(listOf(p.copy(connectionStatus = "ACCEPTED", isMe = false)))
                RetrofitClient.apiService.swipeAction(SwipeActionRequest(p.email.trim().lowercase(), "LIKE"))
                Log.d("MatchLogic", "Successfully matched with ${p.email}")
            } catch (e: Exception) { Log.e("API", "Accept fail", e) }
        }
    }

    fun onReject(p: UserProfile) {
        viewModelScope.launch {
            try {
                profileDao.insertProfiles(listOf(p.copy(connectionStatus = "REJECTED", isMe = false)))
                RetrofitClient.apiService.swipeAction(SwipeActionRequest(p.email.trim().lowercase(), "REJECTED"))
            } catch (e: Exception) {}
        }
    }

    fun onDislike(p: UserProfile) {
        viewModelScope.launch {
            profileDao.insertProfiles(listOf(p.copy(connectionStatus = "REJECTED", isMe = false)))
            _remoteProfiles.value = _remoteProfiles.value.filter { it.email.trim().lowercase() != p.email.trim().lowercase() }
            try {
                RetrofitClient.apiService.swipeAction(SwipeActionRequest(p.email.trim().lowercase(), "REJECTED"))
            } catch (e: Exception) { Log.e("Action", "Reject sync fail", e) }
        }
    }

    fun onUndoDislike(p: UserProfile) {
        viewModelScope.launch {
            profileDao.insertProfiles(listOf(p.copy(connectionStatus = "NONE", isMe = false)))
            try { RetrofitClient.apiService.swipeAction(SwipeActionRequest(p.email.trim().lowercase(), "CANCEL")) } catch (e: Exception) {}
        }
    }

    fun onCancelRequest(p: UserProfile) {
        viewModelScope.launch {
            profileDao.insertProfiles(listOf(p.copy(connectionStatus = "NONE", isMe = false)))
            try { RetrofitClient.apiService.swipeAction(SwipeActionRequest(p.email.trim().lowercase(), "CANCEL")) } catch (e: Exception) {}
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
                val fullUser = auth.user.copy(isMe = true)
                profileDao.insertProfiles(listOf(fullUser))
                _currentUser.value = fullUser
                preferenceManager.setLoggedIn(true, fullUser.email)
                refreshFeed()
                true
            } else if (response.code() == 401) {
                Toast.makeText(app, "Incorrect password", Toast.LENGTH_SHORT).show(); false
            } else if (response.code() == 404) {
                Toast.makeText(app, "User not found", Toast.LENGTH_SHORT).show(); false
            } else {
                Toast.makeText(app, "Server Error: ${response.code()}", Toast.LENGTH_SHORT).show(); false
            }
        } catch(e: Exception) {
            Log.e("Auth", "Verify fail", e)
            Toast.makeText(app, "Connection Error. Check Server.", Toast.LENGTH_SHORT).show()
            false
        } finally { _isAuthLoading.value = false }
    }

    fun setRegistrationData(n: String, a: Int, e: String, p: String, c: String, s: String, g: String, pass: String, ref: String = "") {
        _regData.value = RegistrationData(name = n, age = a, email = e, phone = p, country = c, state = s, gender = g, password = pass, referredBy = ref)
    }

    fun onAdRewarded() {
        viewModelScope.launch {
            _currentUser.value?.let { me ->
                val updated = me.copy(coins = me.coins + 5, deviceId = myDeviceId)
                profileDao.updateProfile(updated)
                try { RetrofitClient.apiService.saveProfileSecure(profile = updated) } catch (e: Exception) {}
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
            try { RetrofitClient.apiService.saveProfileSecure(profile = updated) } catch (e: Exception) {}
            Toast.makeText(app, "Ad-Free Activated! 🚀", Toast.LENGTH_LONG).show()
            loadCurrentUser()
        } else Toast.makeText(app, "Need 250 coins.", Toast.LENGTH_SHORT).show()
    }

    fun setFetchedLocation(country: String, state: String) { _fetchedCountry.value = country; _fetchedState.value = state }
    fun setSignUpMode(isSignUp: Boolean) { _isSignUpMode.value = isSignUp }

    fun detectUserLocation(onResolutionRequired: (com.google.android.gms.common.api.ResolvableApiException) -> Unit, onFail: (String) -> Unit) {
        viewModelScope.launch {
            _isDetectingLocation.value = true
            val helper = LocationHelper(app)
            val result = helper.fetchLocation()
            result.fold(
                onSuccess = { loc ->
                    _fetchedCountry.value = loc.country; _fetchedState.value = loc.state
                    Log.d("Location", "Detected: ${loc.country}, ${loc.state}")
                },
                onFailure = { error ->
                    if (error is com.google.android.gms.common.api.ResolvableApiException) onResolutionRequired(error)
                    else onFail(error.message ?: "Unknown location error")
                }
            )
            _isDetectingLocation.value = false
        }
    }

    fun setFetchingIdentity(fetching: Boolean) { _isFetchingIdentity.value = fetching }

    suspend fun checkRegistrationValidity(email: String, phone: String): String? {
        _isAuthLoading.value = true
        return try {
            val response = withTimeout(60000) { RetrofitClient.apiService.getProfileSecure(email = email) }
            if (response.isSuccessful && response.body() != null) return "Email already registered. Please Sign In."
            val phoneResponse = withTimeout(60000) { RetrofitClient.apiService.checkPhoneSecure(phone = phone) }
            if (phoneResponse.isSuccessful && phoneResponse.body()?.get("exists") == true) return "Mobile number already in use."
            null
        } catch (e: Exception) {
            Log.e("Auth", "Check fail", e)
            "Connection Error. Please check your internet or server."
        } finally { _isAuthLoading.value = false }
    }

    fun clearFetchedIdentity() { _fetchedEmail.value = ""; _fetchedPhone.value = "" }
    fun dismissWelcomeMessage() { _showWelcomeMessage.value = false }

    suspend fun completeRegistration(localUri: String): String? {
        val d = _regData.value ?: RegistrationData()
        _isAuthLoading.value = true
        try {
            var cloudPhotoUrl = ""
            try {
                withTimeout(120000) {
                    val contentResolver = app.contentResolver
                    val inputStream = contentResolver.openInputStream(Uri.parse(localUri))
                    val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                    val out = java.io.ByteArrayOutputStream()
                    originalBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, out)
                    val bytes = out.toByteArray()
                    val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                    val uploadResult = RetrofitClient.apiService.uploadImageSecure(request = ImageUploadRequest(base64))
                    if (uploadResult.isSuccessful) cloudPhotoUrl = uploadResult.body()?.get("url") ?: throw Exception("URL missing")
                    else throw Exception("Server rejected upload: ${uploadResult.code()}")
                }
            } catch (e: Exception) {
                Log.e("Signup", "Photo upload failed", e)
                return "Photo upload failed or timed out. Please try again with better internet."
            }

            try {
                withTimeout(30000) {
                    val signupRes = RetrofitClient.apiService.signup(SignupRequest(email = d.email, password = d.password, name = d.name, age = d.age, gender = d.gender, photoUri = cloudPhotoUrl))
                    if (signupRes.isSuccessful && signupRes.body() != null) RetrofitClient.saveToken(signupRes.body()!!.token)
                    else if (signupRes.code() == 409) throw Exception("ALREADY_REG")
                }
            } catch (e: Exception) {
                if (e.message == "ALREADY_REG") return "Email already registered. Please Sign In."
                Log.e("Signup", "Secure signup fail", e)
            }

            profileDao.deleteMyProfile()
            val myRefCode = "TINK-" + java.util.UUID.randomUUID().toString().take(4).uppercase()
            val user = UserProfile(userId = d.email, name = d.name.ifBlank { "Tinklet User" }, age = d.age, email = d.email, photoUri = cloudPhotoUrl, isMe = true, connectionStatus = "NONE", coins = 25, country = d.country, state = d.state, gender = d.gender, password = d.password, referralCode = myRefCode, referredBy = d.referredBy)
            profileDao.insertProfiles(listOf(user))
            val userWithDevice = user.copy(deviceId = myDeviceId)
            try {
                withTimeout(30000) {
                    RetrofitClient.apiService.saveProfileSecure(profile = userWithDevice)
                    if (d.referredBy.isNotBlank() && d.referredBy != "-") RetrofitClient.apiService.creditReferralSecure(request = ReferralRequest(d.referredBy, myDeviceId))
                }
            } catch (e: Exception) { Log.e("CloudSync", "Secure save fail", e) }
            profileDao.updateProfile(userWithDevice)
            preferenceManager.setLoggedIn(true, user.email)
            _showWelcomeMessage.value = true
            loadCurrentUser()
            return null
        } finally { _isAuthLoading.value = false }
    }

    fun onLoginSuccess(name: String?, email: String?, phone: String?, password: String = "") {
        _isFetchingIdentity.value = false
        _isAuthLoading.value = true
        viewModelScope.launch {
            try {
                phone?.let { p ->
                    if (p.isNotBlank()) {
                        val phoneCheckRes = try { withTimeout(15000) { RetrofitClient.apiService.checkPhoneSecure(phone = p) } } catch(e: Exception) { null }
                        if (phoneCheckRes?.isSuccessful == true && phoneCheckRes.body()?.get("exists") == true) {
                            Toast.makeText(app, "Mobile already in use.", Toast.LENGTH_LONG).show(); _fetchedPhone.value = ""
                        } else _fetchedPhone.value = p
                    }
                }
                val emailStr = email.orEmpty()
                if (emailStr.isBlank()) return@launch
                _fetchedEmail.value = emailStr
                val existing = profileDao.getProfileByEmail(emailStr) ?: try {
                    val res = RetrofitClient.apiService.getProfileSecure(email = emailStr)
                    if (res.isSuccessful) res.body() else null
                } catch (e: Exception) { null }
                if (existing != null) {
                    if (_isSignUpMode.value) {
                        Toast.makeText(app, "Account exists. Logging in...", Toast.LENGTH_SHORT).show()
                        _isSignUpMode.value = false
                    }
                    val me = existing.copy(isMe = true)
                    profileDao.updateProfile(me)
                    _currentUser.value = me
                    preferenceManager.setLoggedIn(true, me.email)
                    refreshFeed()
                } else _regData.value = RegistrationData(email = emailStr, name = name.orEmpty(), phone = _fetchedPhone.value)
            } finally { _isAuthLoading.value = false }
        }
    }

    fun onResetViewed() { viewModelScope.launch { profileDao.resetViewedOnly() } }

    fun onLogout() {
        viewModelScope.launch {
            FirebaseAuth.getInstance().signOut()
            profileDao.deleteAllProfiles()
            preferenceManager.clearProfileCache()
            _currentUser.value = null
            _fetchedEmail.value = ""; _fetchedPhone.value = ""; _fetchedCountry.value = ""; _fetchedState.value = ""; _regData.value = null
            signaling?.disconnect(); signaling = null; CallSignalingHolder.signalingClient = null
        }
    }

    fun updateProfileDetails(u: UserProfile) {
        viewModelScope.launch {
            Toast.makeText(app, "Syncing with Cloud...", Toast.LENGTH_SHORT).show()
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
            if (updatedUser.deviceId.isBlank()) updatedUser = updatedUser.copy(deviceId = myDeviceId)
            profileDao.updateProfile(updatedUser)
            viewModelScope.launch { try { RetrofitClient.apiService.saveProfileSecure(profile = updatedUser) } catch(e: Exception) { Log.e("Retrofit_Sync", "Retrofit update failed", e) } }
            loadCurrentUser()
        }
    }

    fun updateProfilePhoto(uri: String) {
        viewModelScope.launch {
            _currentUser.value?.let { profileDao.updateProfile(it.copy(photoUri = uri)) }
            try {
                val inputStream = app.contentResolver.openInputStream(Uri.parse(uri))
                val bytes = inputStream?.readBytes() ?: return@launch
                val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                val result = RetrofitClient.apiService.uploadImageSecure(request = ImageUploadRequest(base64))
                val url = if (result.isSuccessful) result.body()?.get("url") else null
                if (url == null) throw Exception("Upload failed")
                _currentUser.value?.let {
                    val up = it.copy(photoUri = url, deviceId = myDeviceId)
                    profileDao.updateProfile(up); RetrofitClient.apiService.saveProfileSecure(profile = up); loadCurrentUser()
                }
            } catch (e: Exception) { Toast.makeText(app, "Photo Upload Failed!", Toast.LENGTH_SHORT).show() }
        }
    }

    fun updateFcmToken(token: String) {
        viewModelScope.launch {
            val myEmail = _currentUser.value?.email ?: preferenceManager.userEmail.first()
            if (myEmail != null) try { RetrofitClient.apiService.updateFcmToken(request = FcmTokenRequest(myEmail, token)) } catch (e: Exception) { Log.e("FCM", "Failed to update token on server") }
        }
    }

    fun updateSecondaryPhotos(uris: List<Uri>) {
        Toast.makeText(app, "Uploading Photos...", Toast.LENGTH_SHORT).show()
        viewModelScope.launch {
            val me = profileDao.getMyProfile() ?: _currentUser.value ?: return@launch
            val currentPhotos = me.secondaryPhotos.toMutableList()
            uris.forEach { uri ->
                try {
                    val inputStream = app.contentResolver.openInputStream(uri)
                    val bytes = inputStream?.readBytes() ?: return@forEach
                    val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                    val result = RetrofitClient.apiService.uploadImageSecure(request = ImageUploadRequest(base64))
                    val url = if (result.isSuccessful) result.body()?.get("url") else null
                    if (url != null) currentPhotos.add(url)
                } catch (e: Exception) { Log.e("Upload", "Fail", e) }
            }
            val updated = me.copy(secondaryPhotos = currentPhotos.distinct().take(10), deviceId = myDeviceId)
            profileDao.updateProfile(updated); _currentUser.value = updated; RetrofitClient.apiService.saveProfileSecure(profile = updated); loadCurrentUser()
        }
    }

    fun deleteSecondaryPhoto(url: String) {
        viewModelScope.launch {
            val me = profileDao.getMyProfile() ?: _currentUser.value ?: return@launch
            val updated = me.copy(secondaryPhotos = me.secondaryPhotos.toMutableList().apply { remove(url) }, deviceId = myDeviceId)
            profileDao.updateProfile(updated)
            try { RetrofitClient.apiService.saveProfileSecure(profile = updated) } catch (e: Exception) { Log.e("DiscoveryViewModel", "Cloud delete sync failed", e) }
            loadCurrentUser()
        }
    }

    fun deleteProfileData() {
        viewModelScope.launch { FirebaseAuth.getInstance().signOut(); profileDao.deleteAllProfiles(); preferenceManager.clearProfileCache(); _currentUser.value = null }
    }

    private fun seedMockData() { }

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
            chatMessageDao.getMessagesForMatch(partnerEmail).first().filter { it.senderId == "OTHER" && it.status != "READ" }.forEach { signaling?.sendReadReceipt(partnerEmail, it.messageId) }
        }
    }

    fun sendImageMessage(partnerEmail: String, uri: Uri) {
        viewModelScope.launch {
            val myId = _currentUser.value?.email ?: ""
            if (myId.isBlank()) return@launch
            _uploadError.value = null; _uploadProgress.value = 0
            try {
                val inputStream = app.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes() ?: return@launch
                val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                _uploadProgress.value = 50
                val result = RetrofitClient.apiService.uploadImageSecure(request = ImageUploadRequest(base64))
                val url = if (result.isSuccessful) result.body()?.get("url") else null
                if (url == null) throw Exception("Upload failed")
                _uploadProgress.value = 100; _uploadProgress.value = null
                sendMessage(partnerEmail, "[IMAGE]$url")
            } catch (e: Exception) {
                _uploadProgress.value = null; _uploadError.value = "Upload failed. Tap to retry."; Log.e("Chat", "Secure image upload failed", e)
            }
        }
    }

    fun markAsViewed(p: UserProfile) {
        viewModelScope.launch { if (p.connectionStatus == "NONE") profileDao.updateProfile(p.copy(connectionStatus = "VIEWED")) }
    }

    fun sendGift(email: String, gift: String) {
        viewModelScope.launch {
            val me = _currentUser.value ?: return@launch
            if (me.coins >= 1) {
                val updatedMe = me.copy(coins = me.coins - 1)
                profileDao.updateProfile(updatedMe)
                try { RetrofitClient.apiService.saveProfileSecure(profile = updatedMe) } catch (e: Exception) {}
                sendMessage(email, "Sent you a $gift 🎁")
                loadCurrentUser()
            } else Toast.makeText(app, "Need 1 coin to send a gift", Toast.LENGTH_SHORT).show()
        }
    }

    fun updateFilters(c: String, s: String, a: IntRange, g: String, r: String, h: String, l: String, i: String) { }

    fun blockUser(email: String) {
        viewModelScope.launch {
            profileDao.getProfileByEmail(email)?.let {
                profileDao.updateProfile(it.copy(isBlocked = true)); signaling?.sendSignal(email, "block_user"); Toast.makeText(app, "${it.name} Blocked", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun unblockUser(email: String) {
        viewModelScope.launch {
            profileDao.getProfileByEmail(email)?.let { profileDao.updateProfile(it.copy(isBlocked = false)); Toast.makeText(app, "${it.name} Unblocked", Toast.LENGTH_SHORT).show() }
        }
    }

    fun reportUser(email: String) {
        viewModelScope.launch {
            val me = _currentUser.value?.email ?: return@launch
            profileDao.getProfileByEmail(email)?.let {
                profileDao.updateProfile(it.copy(reportCount = it.reportCount + 1))
                try { RetrofitClient.apiService.saveReportSecure(request = ReportRequest(me, email)); Toast.makeText(app, "User Reported. We will review this.", Toast.LENGTH_SHORT).show() } catch (e: Exception) { Log.e("Report", "Sync fail", e) }
            }
        }
    }

    fun deleteMessage(msg: ChatMessage, forEveryone: Boolean) {
        viewModelScope.launch {
            if (forEveryone && msg.senderId == "ME") {
                chatMessageDao.updateMessage(msg.copy(isDeletedForEveryone = true, text = "You deleted this message")); signaling?.sendDeleteMessage(msg.matchId, msg.timestamp)
            } else chatMessageDao.deleteMessage(msg.id)
        }
    }

    fun editMessage(msg: ChatMessage, newText: String) {
        viewModelScope.launch {
            chatMessageDao.updateMessage(msg.copy(text = newText, isEdited = true))
            if (msg.senderId == "ME") signaling?.sendEditMessage(msg.matchId, msg.timestamp, newText)
        }
    }

    fun deleteChat(partnerEmail: String) {
        viewModelScope.launch { chatMessageDao.deleteChat(partnerEmail); Toast.makeText(app, "Chat Cleared Permanently", Toast.LENGTH_SHORT).show() }
    }

    fun sendVoiceNote(partnerEmail: String, file: java.io.File, duration: Int) {
        viewModelScope.launch {
            try {
                val bytes = file.readBytes()
                val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                val result = RetrofitClient.apiService.uploadMediaSecure(request = MediaUploadRequest(base64, "audio", partnerEmail))
                val url = if (result.isSuccessful) result.body()?.get("url") else null
                if (url == null) throw Exception("Upload failed")
                val msg = ChatMessage(matchId = partnerEmail, senderId = "ME", voiceUrl = url, duration = duration)
                chatMessageDao.insertMessage(msg); signaling?.sendSignal(partnerEmail, "voice_note", sdp = url); file.delete()
            } catch (e: Exception) { Log.e("Media", "Voice upload failed", e); Toast.makeText(app, "Voice upload failed", Toast.LENGTH_SHORT).show() }
        }
    }

    fun sendVideoNote(partnerEmail: String, file: java.io.File, duration: Int) {
        viewModelScope.launch {
            try {
                val bytes = file.readBytes()
                val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                val result = RetrofitClient.apiService.uploadMediaSecure(request = MediaUploadRequest(base64, "video", partnerEmail))
                val url = if (result.isSuccessful) result.body()?.get("url") else null
                if (url == null) throw Exception("Upload failed")
                val msg = ChatMessage(matchId = partnerEmail, senderId = "ME", videoNoteUrl = url, duration = duration)
                chatMessageDao.insertMessage(msg); signaling?.sendSignal(partnerEmail, "video_note", sdp = url); file.delete()
            } catch (e: Exception) { Log.e("Media", "Video upload failed", e); Toast.makeText(app, "Video upload failed", Toast.LENGTH_SHORT).show() }
        }
    }
}
