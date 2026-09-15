package com.tinklet.bharatdatingapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.tinklet.bharatdatingapp.navigation.BharatNavKey
import com.tinklet.bharatdatingapp.ui.screens.*
import com.tinklet.bharatdatingapp.calling.*
import com.tinklet.bharatdatingapp.ui.theme.BharatDatingAppTheme
import com.tinklet.bharatdatingapp.ui.viewmodel.DiscoveryViewModel
import com.tinklet.bharatdatingapp.ui.viewmodel.RegistrationData
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.android.gms.auth.api.identity.GetSignInIntentRequest
import com.google.android.gms.auth.api.identity.Identity
import com.tinklet.bharatdatingapp.utils.LocationUtils
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.location.Geocoder
import com.google.android.gms.location.LocationServices
import java.util.Locale
import com.tinklet.bharatdatingapp.utils.SoundManager
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.launch

class MainActivity : androidx.fragment.app.FragmentActivity() {
    private lateinit var discoveryViewModel: DiscoveryViewModel
    private lateinit var credentialManager: CredentialManager
    private val WEB_CLIENT_ID = "145266939731-m78drp48kjbslsvc4tmgphvb8lgnspfd.apps.googleusercontent.com"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        discoveryViewModel = ViewModelProvider(this)[DiscoveryViewModel::class.java]
        credentialManager = androidx.credentials.CredentialManager.create(this)
        
        try {
            com.tinklet.bharatdatingapp.utils.AdManager.init(this)
        } catch (e: Exception) { Log.e("MainActivity", "AdManager init fail", e) }

        try {
            requestNotificationPermission()
            fetchAndSyncFcmToken()
        } catch (e: Exception) { Log.e("MainActivity", "Generic startup fail", e) }

        enableEdgeToEdge()
        setContent {
            val themeMode by discoveryViewModel.themeMode.collectAsStateWithLifecycle()
            val darkTheme = when(themeMode) {
                "Dark" -> true
                "Light" -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            BharatDatingAppTheme(darkTheme = darkTheme) {
                val isUserLoggedIn by discoveryViewModel.isUserLoggedIn.collectAsStateWithLifecycle()
                if (isUserLoggedIn == null) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                } else {
                    MainNavigation(
                        discoveryViewModel = discoveryViewModel, 
                        initialLoggedIn = isUserLoggedIn!!,
                        onFetchEmail = { signInWithGoogle() },
                        onFetchPhone = { fetchPhone() },
                        intent = intent
                    )
                }
            }
        }
    }

    private fun signInWithGoogle() {
        discoveryViewModel.setFetchingIdentity(true)
        val request = GetSignInIntentRequest.builder()
            .setServerClientId(WEB_CLIENT_ID)
            .build()

        Identity.getSignInClient(this)
            .getSignInIntent(request)
            .addOnSuccessListener { pendingIntent ->
                try {
                    emailHintLauncher.launch(
                        androidx.activity.result.IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                    )
                } catch (e: Exception) {
                    discoveryViewModel.setFetchingIdentity(false)
                }
            }
            .addOnFailureListener {
                discoveryViewModel.setFetchingIdentity(false)
                Toast.makeText(this, "No Google accounts found", Toast.LENGTH_SHORT).show()
            }
    }

    private val emailHintLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            try {
                val credential = Identity.getSignInClient(this).getSignInCredentialFromIntent(result.data)
                val selectedEmail = credential.id
                val displayName = credential.displayName
                
                discoveryViewModel.onLoginSuccess(displayName, selectedEmail, null)
                discoveryViewModel.setFetchingIdentity(false)
            } catch (e: Exception) {
                discoveryViewModel.setFetchingIdentity(false)
            }
        } else {
            discoveryViewModel.setFetchingIdentity(false)
        }
    }


    private fun handleSignIn(result: GetCredentialResponse) {
        // Obsolete - removed full sign-in logic
    }

    private fun fetchPhone() {
        val request = com.google.android.gms.auth.api.identity.GetPhoneNumberHintIntentRequest.builder().build()
        com.google.android.gms.auth.api.identity.Identity.getSignInClient(this)
            .getPhoneNumberHintIntent(request)
            .addOnSuccessListener { result ->
                try {
                    phoneNumberHintLauncher.launch(
                        androidx.activity.result.IntentSenderRequest.Builder(result.intentSender).build()
                    )
                } catch (e: Exception) { Log.e("PhoneHint", "Launch failed", e) }
            }
            .addOnFailureListener { Log.e("PhoneHint", "Failed to get intent", it) }
    }

    private val phoneNumberHintLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            try {
                val phoneNumber = com.google.android.gms.auth.api.identity.Identity.getSignInClient(this)
                    .getPhoneNumberFromIntent(result.data)
                discoveryViewModel.onLoginSuccess(null, null, phoneNumber)
                discoveryViewModel.setFetchingIdentity(false)
            } catch (e: Exception) { 
                Log.e("PhoneHint", "Extraction failed", e) 
                discoveryViewModel.setFetchingIdentity(false)
            }
        } else {
            discoveryViewModel.setFetchingIdentity(false)
        }
    }

    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != 
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    private fun fetchAndSyncFcmToken() {
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("FCM", "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }
            val token = task.result
            Log.d("FCM", "Token: $token")
            discoveryViewModel.updateFcmToken(token)
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        SoundManager.release()
    }
}

@Composable
fun MainNavigation(
    discoveryViewModel: DiscoveryViewModel, 
    initialLoggedIn: Boolean, 
    onFetchEmail: () -> Unit,
    onFetchPhone: () -> Unit,
    intent: Intent? = null
) {
    val context = LocalContext.current
    val backStack = rememberNavBackStack(if (initialLoggedIn) BharatNavKey.Discovery else BharatNavKey.Registration())
    
    // Handle Notification Intent
    LaunchedEffect(intent) {
        if (initialLoggedIn && intent?.getBooleanExtra("open_chat", false) == true) {
            val email = intent.getStringExtra("target_email")
            if (!email.isNullOrBlank()) {
                backStack.clear()
                backStack.add(BharatNavKey.Discovery)
                backStack.add(BharatNavKey.Chat(email))
            }
        }
    }
    val regData by discoveryViewModel.regData.collectAsStateWithLifecycle()
    val currentUser by discoveryViewModel.currentUser.collectAsStateWithLifecycle()
    val themeMode by discoveryViewModel.themeMode.collectAsStateWithLifecycle()
    val isCheckingUser by discoveryViewModel.isCheckingUser.collectAsStateWithLifecycle()
    val isSignUpMode by discoveryViewModel.isSignUpMode.collectAsStateWithLifecycle()
    
    val fetchedEmail by discoveryViewModel.fetchedEmail.collectAsStateWithLifecycle()
    val fetchedPhone by discoveryViewModel.fetchedPhone.collectAsStateWithLifecycle()
    val fetchedCountry by discoveryViewModel.fetchedCountry.collectAsStateWithLifecycle()
    val fetchedState by discoveryViewModel.fetchedState.collectAsStateWithLifecycle()

    val multiPhotoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            discoveryViewModel.updateSecondaryPhotos(uris.take(10))
            Toast.makeText(context, "Uploading ${uris.size} photos...", Toast.LENGTH_SHORT).show()
        }
    }

    val chatImagePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val lastKey = backStack.lastOrNull()
            if (lastKey is BharatNavKey.Chat) {
                discoveryViewModel.sendImageMessage(lastKey.email, it)
                Toast.makeText(context, "Uploading image...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(currentUser) {
        if (currentUser != null && (backStack.lastOrNull() is BharatNavKey.Registration || backStack.lastOrNull() is BharatNavKey.MobileInput)) {
            backStack.clear()
            backStack.add(BharatNavKey.Discovery)
        }
    }

    LaunchedEffect(regData, currentUser, isCheckingUser) {
        if (!isCheckingUser && regData != null && !regData?.email.isNullOrEmpty() && currentUser == null) {
            if (backStack.lastOrNull() !is BharatNavKey.Registration) {
                backStack.add(BharatNavKey.Registration())
            }
        }
    }

    val myEntryProvider = entryProvider<NavKey> {
        entry<BharatNavKey.Registration> {
            val data = regData ?: RegistrationData()
            val scope = rememberCoroutineScope()
            
            RegistrationScreen(
                discoveryViewModel = discoveryViewModel,
                initialName = data.name,
                initialEmail = fetchedEmail.ifBlank { data.email },
                initialPhone = fetchedPhone.ifBlank { data.phone },
                initialPassword = data.password,
                initialCountry = fetchedCountry,
                initialState = fetchedState,
                isSignUpMode = isSignUpMode,
                onContinue = { n, a, e, p, c, s, g, pass, ref ->
                    if (isSignUpMode) {
                        discoveryViewModel.setRegistrationData(n, a, e, p, c, s, g, pass, ref)
                        backStack.add(BharatNavKey.PhotoCapture())
                    } else {
                        scope.launch {
                            val success = discoveryViewModel.verifyPassword(e, pass)
                            if (success) {
                                // Transition to Discovery is handled by LaunchedEffect(currentUser)
                            } else {
                                Toast.makeText(context, "Incorrect password or user not found", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                onFetchEmail = onFetchEmail,
                onFetchPhone = onFetchPhone,
                onToggleMode = { discoveryViewModel.setSignUpMode(it) }
            )
        }
        entry<BharatNavKey.MobileInput> { 
            // Deprecated
        }
        entry<BharatNavKey.PhotoCapture> { key ->
            PhotoCaptureScreen(
                isUpdate = key.isUpdate,
                onPhotoCaptured = { uri ->
                (context as? MainActivity)?.lifecycleScope?.launch {
                    if (key.isUpdate) {
                        discoveryViewModel.updateProfilePhoto(uri.toString())
                        if(backStack.size > 0) backStack.removeAt(backStack.size - 1)
                    } else {
                        // SECURE REGISTRATION: Upload photo via server during signup
                        val error = discoveryViewModel.completeRegistration(uri.toString())
                        if (error != null) {
                            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                            if (error.contains("Email already registered")) {
                                discoveryViewModel.setSignUpMode(false)
                                backStack.clear()
                                backStack.add(BharatNavKey.Registration())
                            }
                        } else {
                            backStack.clear()
                            backStack.add(BharatNavKey.Discovery)
                        }
                    }
                }
            })
        }
        entry<BharatNavKey.Discovery> { _ ->
            val profiles by discoveryViewModel.profiles.collectAsStateWithLifecycle()

            AppScaffold(backStack, discoveryViewModel) {
                DiscoveryScreen(
                    discoveryViewModel = discoveryViewModel,
                    profiles = profiles, 
                    onCoinCenterClick = { 
                        discoveryViewModel.fetchLeaderboard()
                        backStack.add(BharatNavKey.CoinCenter) 
                    },
                    selectedCountry = "All", 
                    selectedState = "All",
                    ageRange = 18..100,
                    genderFilter = "All",
                    religionFilter = "All",
                    habitFilter = "All",
                    languageFilter = "All",
                    intentionFilter = "All",
                    onFilterChange = { _, _, _, _, _, _, _, _ -> },
                    onLike = { discoveryViewModel.onLike(it) },
                    onSuperLike = { p -> 
                        discoveryViewModel.onSuperLike(p) { msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDislike = { discoveryViewModel.onDislike(it) },
                    onGift = { _, _ -> },
                    onProfileClick = { backStack.add(BharatNavKey.ProfileDetail(it.email)) },
                    onResetViewed = { discoveryViewModel.onResetViewed() },
                    onMarkAsViewed = { discoveryViewModel.markAsViewed(it) },
                    onCancelRequest = { discoveryViewModel.onCancelRequest(it) },
                    rejectedProfiles = discoveryViewModel.rejectedProfiles.collectAsStateWithLifecycle().value,
                    superLikedProfiles = discoveryViewModel.superLikedProfiles.collectAsStateWithLifecycle().value,
                    sentRequests = discoveryViewModel.sentRequests.collectAsStateWithLifecycle().value
                )
            }
        }
        entry<BharatNavKey.ProfileDetail> { key ->
            val profiles by discoveryViewModel.profiles.collectAsStateWithLifecycle()
            val matches by discoveryViewModel.matches.collectAsStateWithLifecycle()
            val rejected by discoveryViewModel.rejectedProfiles.collectAsStateWithLifecycle()
            val incoming by discoveryViewModel.incomingRequests.collectAsStateWithLifecycle()
            val sent by discoveryViewModel.sentRequests.collectAsStateWithLifecycle()
            
            val allPossible = (profiles + matches + rejected + incoming + sent).distinctBy { it.email }
            
            allPossible.find { it.email == key.email }?.let {
                ProfileDetailScreen(
                    profile = it, 
                    discoveryViewModel = discoveryViewModel,
                    onBack = { if(backStack.size > 0) backStack.removeAt(backStack.size - 1) }
                )
            } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        entry<BharatNavKey.Profile> { _ ->
            val me by discoveryViewModel.currentUser.collectAsStateWithLifecycle()
            AppScaffold(backStack, discoveryViewModel) {
                ProfileScreen(
                    user = me,
                    onUpdatePhoto = { backStack.add(BharatNavKey.PhotoCapture(isUpdate = true)) },
                    onUpdatePhotos = { backStack.add(BharatNavKey.MyPhotos) },
                    onLogout = { 
                        discoveryViewModel.onLogout()
                        discoveryViewModel.clearFetchedIdentity()
                        backStack.clear()
                        backStack.add(BharatNavKey.Registration())
                    },
                    onDelete = { 
                        discoveryViewModel.deleteProfileData()
                        backStack.clear()
                        backStack.add(BharatNavKey.Registration())
                    },
                    onEditProfile = { backStack.add(BharatNavKey.EditProfile) },
                    onSubscription = { backStack.add(BharatNavKey.Subscription) },
                    currentTheme = themeMode,
                    onThemeChange = { discoveryViewModel.setThemeMode(it) },
                    currentLanguage = discoveryViewModel.appLanguage.collectAsStateWithLifecycle().value,
                    soundEnabled = discoveryViewModel.interactiveSounds.collectAsStateWithLifecycle().value,
                    onSoundToggle = { discoveryViewModel.setInteractiveSounds(it) }
                )
            }
        }
        entry<BharatNavKey.MyPhotos> {
            val me by discoveryViewModel.currentUser.collectAsStateWithLifecycle()
            val lang by discoveryViewModel.appLanguage.collectAsStateWithLifecycle()
            MyPhotosScreen(
                user = me,
                lang = lang,
                onBack = { if(backStack.size > 0) backStack.removeAt(backStack.size - 1) },
                onUploadClick = { multiPhotoPickerLauncher.launch("image/*") },
                onDeleteClick = { discoveryViewModel.deleteSecondaryPhoto(it) }
            )
        }
        entry<BharatNavKey.CoinCenter> {
            val me by discoveryViewModel.currentUser.collectAsStateWithLifecycle()
            val leaderboard by discoveryViewModel.leaderboard.collectAsStateWithLifecycle()
            CoinCenterScreen(
                user = me,
                leaderboard = leaderboard,
                onAdRewarded = { discoveryViewModel.onAdRewarded() },
                onBadgePurchase = { type, cost ->
                    discoveryViewModel.buyBadge(type, cost)
                },
                onBack = { if(backStack.size > 0) backStack.removeAt(backStack.size - 1) }
            )
        }
        entry<BharatNavKey.Subscription> {
            SubscriptionScreen(onBack = { if(backStack.size > 0) backStack.removeAt(backStack.size - 1) })
        }
        entry<BharatNavKey.EditProfile> {
            val me by discoveryViewModel.currentUser.collectAsStateWithLifecycle()
            EditProfileScreen(
                user = me,
                discoveryViewModel = discoveryViewModel,
                onSave = { updated -> 
                    discoveryViewModel.updateProfileDetails(updated)
                    if(backStack.size > 0) backStack.removeAt(backStack.size - 1)
                },
                onBack = { if(backStack.size > 0) backStack.removeAt(backStack.size - 1) }
            )
        }
        entry<BharatNavKey.Requests> { _ ->
            val incNormal by discoveryViewModel.incomingNormalLikes.collectAsStateWithLifecycle()
            val incSuper by discoveryViewModel.incomingSuperlikes.collectAsStateWithLifecycle()
            val incRejected by discoveryViewModel.incomingRejected.collectAsStateWithLifecycle()
            
            val sentNormal by discoveryViewModel.sentNormalLikes.collectAsStateWithLifecycle()
            val sentSuper by discoveryViewModel.sentSuperlikes.collectAsStateWithLifecycle()
            val sentRejected by discoveryViewModel.sentRejected.collectAsStateWithLifecycle()

            val context = LocalContext.current
            
            AppScaffold(backStack, discoveryViewModel) {
                RequestsScreen(
                    incomingNormal = incNormal,
                    incomingSuper = incSuper,
                    incomingRejected = incRejected,
                    sentNormal = sentNormal,
                    sentSuper = sentSuper,
                    sentRejected = sentRejected,
                    onAccept = { discoveryViewModel.onAccept(it) }, 
                    onReject = { discoveryViewModel.onReject(it) }, 
                    onCancel = { discoveryViewModel.onCancelRequest(it) },
                    onSuperLike = { p -> 
                        discoveryViewModel.onSuperLike(p) { msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onUndoDislike = { discoveryViewModel.onUndoDislike(it) },
                    onProfileClick = { backStack.add(BharatNavKey.ProfileDetail(it.email)) }
                )
            }
        }
        entry<BharatNavKey.Matches> { _ ->
             AppScaffold(backStack, discoveryViewModel) {
                val matches by discoveryViewModel.matches.collectAsStateWithLifecycle()
                MatchesScreen(
                    title = "Connections",
                    matches = matches.reversed(),
                    onMatchClick = { backStack.add(BharatNavKey.ProfileDetail(it.email)) }, 
                    onChatClick = { backStack.add(BharatNavKey.Chat(it.email)) },
                    onBack = { if(backStack.size > 0) backStack.removeAt(backStack.size - 1) },
                    discoveryViewModel = discoveryViewModel
                )
            }
        }
        entry<BharatNavKey.Conversations> { _ ->
             AppScaffold(backStack, discoveryViewModel) {
                val matches by discoveryViewModel.matches.collectAsStateWithLifecycle()
                MatchesScreen(
                    title = "Messages",
                    matches = matches, 
                    onMatchClick = { backStack.add(BharatNavKey.ProfileDetail(it.email)) }, 
                    onChatClick = { backStack.add(BharatNavKey.Chat(it.email)) },
                    onBack = { if(backStack.size > 0) backStack.removeAt(backStack.size - 1) },
                    discoveryViewModel = discoveryViewModel
                )
            }
        }
        entry<BharatNavKey.Chat> { key ->
            val matches by discoveryViewModel.matches.collectAsStateWithLifecycle()
            matches.find { it.email == key.email }?.let { partner ->
                val messages by discoveryViewModel.getMessages(partner.email).collectAsStateWithLifecycle(emptyList())
                ChatScreen(
                    match = partner,
                    messages = messages,
                    discoveryViewModel = discoveryViewModel,
                    onSendMessage = { discoveryViewModel.sendMessage(partner.email, it) },
                    onSendImageClick = { chatImagePickerLauncher.launch("image/*") },
                    onSendGift = { discoveryViewModel.sendGift(partner.email, it) },
                    onCallClick = { 
                        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                        
                        if (hasMic) {
                            val intent = Intent(context, CallActivity::class.java).apply {
                                putExtra(CallActivity.EXTRA_TARGET_ID, partner.email)
                                putExtra(CallActivity.EXTRA_TARGET_NAME, partner.name)
                                putExtra(CallActivity.EXTRA_IS_CALLER, true)
                                putExtra(CallActivity.EXTRA_CALL_TYPE, "audio")
                            }
                            context.startActivity(intent)
                        } else {
                            (context as? androidx.fragment.app.FragmentActivity)?.requestPermissions(
                                arrayOf(Manifest.permission.RECORD_AUDIO), 
                                102
                            )
                        }
                    },
                    onVideoCallClick = {
                        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                        val hasCam = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                        
                        if (hasMic && hasCam) {
                            val intent = Intent(context, CallActivity::class.java).apply {
                                putExtra(CallActivity.EXTRA_TARGET_ID, partner.email)
                                putExtra(CallActivity.EXTRA_TARGET_NAME, partner.name)
                                putExtra(CallActivity.EXTRA_IS_CALLER, true)
                                putExtra(CallActivity.EXTRA_CALL_TYPE, "video")
                            }
                            context.startActivity(intent)
                        } else {
                            (context as? androidx.fragment.app.FragmentActivity)?.requestPermissions(
                                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA), 
                                103
                            )
                        }
                    },
                    onBack = { if(backStack.size > 0) backStack.removeAt(backStack.size - 1) },
                    onNameClick = { p -> backStack.add(BharatNavKey.ProfileDetail(p.email)) }
                )
            }
        }
    }
    NavDisplay(backStack = backStack, entryProvider = myEntryProvider)
}

@Composable
fun AppScaffold(backStack: MutableList<NavKey>, discoveryViewModel: DiscoveryViewModel, content: @Composable () -> Unit) {
    val lang by discoveryViewModel.appLanguage.collectAsStateWithLifecycle()
    val view = androidx.compose.ui.platform.LocalView.current

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = backStack.lastOrNull() == BharatNavKey.Discovery, 
                    onClick = { 
                        view.playSoundEffect(android.view.SoundEffectConstants.CLICK)
                        backStack.clear()
                        backStack.add(BharatNavKey.Discovery) 
                    }, 
                    icon = { Icon(Icons.Rounded.Search, "Discovery") }, 
                    label = { Text(com.tinklet.bharatdatingapp.utils.Translator.translate("Tinklet", lang)) }
                )
                NavigationBarItem(
                    selected = backStack.lastOrNull() == BharatNavKey.Requests, 
                    onClick = { 
                        if(backStack.lastOrNull() != BharatNavKey.Requests) { 
                            view.playSoundEffect(android.view.SoundEffectConstants.CLICK)
                            backStack.add(BharatNavKey.Requests) 
                        } 
                    }, 
                    icon = { Icon(Icons.Rounded.Person, "Inbox") }, 
                    label = { Text(com.tinklet.bharatdatingapp.utils.Translator.translate("Inbox", lang)) }
                )
                NavigationBarItem(
                    selected = backStack.lastOrNull() == BharatNavKey.Matches, 
                    onClick = { 
                        if(backStack.lastOrNull() != BharatNavKey.Matches) { 
                            view.playSoundEffect(android.view.SoundEffectConstants.CLICK)
                            backStack.add(BharatNavKey.Matches) 
                        } 
                    }, 
                    icon = { Icon(Icons.Rounded.Favorite, "Matches") }, 
                    label = { Text(com.tinklet.bharatdatingapp.utils.Translator.translate("Matches", lang)) }
                )
                NavigationBarItem(
                    selected = backStack.lastOrNull() == BharatNavKey.Conversations, 
                    onClick = { 
                        if(backStack.lastOrNull() != BharatNavKey.Conversations) { 
                            view.playSoundEffect(android.view.SoundEffectConstants.CLICK)
                            backStack.add(BharatNavKey.Conversations) 
                        } 
                    }, 
                    icon = { Icon(Icons.AutoMirrored.Rounded.Chat, "Chats") }, 
                    label = { Text(com.tinklet.bharatdatingapp.utils.Translator.translate("Chats", lang)) }
                )
                NavigationBarItem(
                    selected = backStack.lastOrNull() == BharatNavKey.Profile, 
                    onClick = { 
                        if(backStack.lastOrNull() != BharatNavKey.Profile) { 
                            view.playSoundEffect(android.view.SoundEffectConstants.CLICK)
                            backStack.add(BharatNavKey.Profile) 
                        } 
                    }, 
                    icon = { Icon(Icons.Rounded.AccountCircle, "Me") }, 
                    label = { Text(com.tinklet.bharatdatingapp.utils.Translator.translate("Me", lang)) }
                )
            }
        }
    ) { padding -> Box(modifier = Modifier.padding(padding)) { content() } }
}
