package com.tinklet.bharatdatingapp

import android.widget.Toast
import androidx.credentials.*
import com.google.android.libraries.identity.googleid.*
import com.truecaller.android.sdk.oAuth.*
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object TinkletLoginFinal {
    // 1. Google login call
    fun googleLogin(activity: android.app.Activity, webClientId: String, onSuccess: (String, String) -> Unit) {
        val manager = CredentialManager.create(activity)
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(true)
            .build()
        val req = GetCredentialRequest.Builder().addCredentialOption(option).build()

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val res = manager.getCredential(activity, req)
                val cred = GoogleIdTokenCredential.createFrom(res.credential.data)
                onSuccess(cred.displayName ?: "User", cred.id)
            } catch (e: Exception) {
                Toast.makeText(activity, "Google Setup Missing: Check SHA-1 & SHA-256", Toast.LENGTH_LONG).show()
            }
        }
    }

    // 2. Truecaller login call (Fixed for SDK 3.1.0)
    fun truecallerLogin(activity: androidx.fragment.app.FragmentActivity) {
        if (!TcSdk.getInstance().isOAuthFlowUsable) {
            Toast.makeText(activity, "Truecaller not available", Toast.LENGTH_SHORT).show()
            return
        }
        val verifier = CodeVerifierUtil.generateRandomCodeVerifier()
        val challenge = CodeVerifierUtil.getCodeChallenge(verifier)
        TcSdk.getInstance().setOAuthState(UUID.randomUUID().toString())
        TcSdk.getInstance().setOAuthScopes(arrayOf("profile")) // Minimum scope to avoid error
        if (challenge != null) TcSdk.getInstance().setCodeChallenge(challenge)
        TcSdk.getInstance().getAuthorizationCode(activity)
    }
}
