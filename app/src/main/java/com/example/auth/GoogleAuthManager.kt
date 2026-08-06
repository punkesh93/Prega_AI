package com.example.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

/**
 * Prega AI — Google Sign-In, via Credential Manager.
 *
 * Sign-in is optional everywhere in this app. Nothing is gated behind it —
 * it exists purely so someone who wants her profile backed up to a Google
 * account can have that, and so a returning user's name and photo can be
 * filled in without retyping them. Declining or cancelling must always fall
 * through cleanly to the local, on-device experience.
 *
 * ── SETUP REQUIRED BEFORE THIS WORKS ──────────────────────────────────────
 * This needs a Web (server) OAuth client ID from Google Cloud Console, tied
 * to your app's package name and SHA-1 signing fingerprint:
 *
 *  1. console.cloud.google.com → APIs & Services → Credentials
 *  2. Create Credentials → OAuth client ID → Web application
 *     (yes, Web — not Android — this is what proves the token came from your
 *     app's build, not a spoofed one; see Credential Manager's docs on this
 *     if it seems backwards)
 *  3. Copy the client ID into [WEB_CLIENT_ID] below.
 *  4. Register your debug AND release SHA-1 fingerprints against the
 *     project, or every sign-in attempt fails with NoCredentialException.
 *
 * Until that's done, [signIn] returns [GoogleAuthResult.NotConfigured] and
 * the UI shows a plain "not set up yet" message rather than crashing.
 */
object GoogleAuthConfig {
    /** Replace with your real Web client ID from Google Cloud Console. */
    const val WEB_CLIENT_ID = "YOUR_WEB_CLIENT_ID.apps.googleusercontent.com"

    val isConfigured: Boolean
        get() = WEB_CLIENT_ID.isNotBlank() &&
            !WEB_CLIENT_ID.startsWith("YOUR_") &&
            WEB_CLIENT_ID.endsWith(".apps.googleusercontent.com")
}

sealed interface GoogleAuthResult {
    data class Success(val name: String, val email: String, val photoUrl: String) : GoogleAuthResult
    /** She backed out of the picker. Not an error — say nothing alarming. */
    data object Cancelled : GoogleAuthResult
    /** No client ID wired up yet. */
    data object NotConfigured : GoogleAuthResult
    /** No Google account on the device, or some other recoverable failure. */
    data class Failure(val message: String) : GoogleAuthResult
}

class GoogleAuthManager(private val context: Context) {

    private val credentialManager by lazy { CredentialManager.create(context) }

    suspend fun signIn(): GoogleAuthResult {
        if (!GoogleAuthConfig.isConfigured) return GoogleAuthResult.NotConfigured

        val option = GetGoogleIdOption.Builder()
            // false: offer every Google account on the device, not only ones
            // that have used this app before — most first launches are the
            // first time.
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(GoogleAuthConfig.WEB_CLIENT_ID)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        return try {
            val response = credentialManager.getCredential(context, request)
            val credential = response.credential

            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleId = GoogleIdTokenCredential.createFrom(credential.data)
                GoogleAuthResult.Success(
                    name = googleId.displayName.orEmpty(),
                    email = googleId.id,
                    photoUrl = googleId.profilePictureUri?.toString().orEmpty(),
                )
            } else {
                GoogleAuthResult.Failure("Unexpected credential type returned.")
            }
        } catch (e: GetCredentialCancellationException) {
            GoogleAuthResult.Cancelled
        } catch (e: NoCredentialException) {
            GoogleAuthResult.Failure("No Google account found on this device.")
        } catch (e: GoogleIdTokenParsingException) {
            GoogleAuthResult.Failure("Couldn't verify that account. Try again.")
        } catch (e: GetCredentialException) {
            GoogleAuthResult.Failure(e.message ?: "Sign-in didn't complete.")
        }
    }
}
