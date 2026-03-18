package com.jacobibanez.plugin.android.godotplaygameservices.signin

import android.util.Log
import com.google.android.gms.games.GamesSignInClient
import com.google.android.gms.games.PlayGames
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.auth
import com.jacobibanez.plugin.android.godotplaygameservices.BuildConfig
import com.jacobibanez.plugin.android.godotplaygameservices.signals.SignInSignals.firebaseCheckConnectedUserSignal
import com.jacobibanez.plugin.android.godotplaygameservices.signals.SignInSignals.firebaseAuthWithGoogleSignal
import com.jacobibanez.plugin.android.godotplaygameservices.signals.SignInSignals.firebaseLinkWithGoogleSignal
import com.jacobibanez.plugin.android.godotplaygameservices.signals.SignInSignals.firebaseSignInAnonymouslySignal
import com.jacobibanez.plugin.android.godotplaygameservices.signals.SignInSignals.serverSideAccessRequested
import com.jacobibanez.plugin.android.godotplaygameservices.signals.SignInSignals.userAuthenticated
import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin.emitSignal

import kotlinx.coroutines.launch

import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.GoogleAuthProvider

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class SignInProxy(
    private val godot: Godot,
    private val gamesSignInClient: GamesSignInClient = PlayGames.getGamesSignInClient(godot.getActivity()!!)
) {

    private val tag: String = SignInProxy::class.java.simpleName

    fun isAuthenticated() {
        Log.d(tag, "Checking if user is authenticated")
        gamesSignInClient.isAuthenticated.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d(tag, "User authenticated: ${task.result.isAuthenticated}")
                emitSignal(
                    godot,
                    BuildConfig.GODOT_PLUGIN_NAME,
                    userAuthenticated,
                    task.result.isAuthenticated
                )
            } else {
                Log.e(tag, "User not authenticated. Cause: ${task.exception}", task.exception)
                emitSignal(
                    godot, BuildConfig.GODOT_PLUGIN_NAME, userAuthenticated, false
                )
            }
        }
    }

    fun signIn() {
        Log.d(tag, "Signing in")
        gamesSignInClient.signIn().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d(tag, "User signed in: ${task.result.isAuthenticated}")
                emitSignal(
                    godot,
                    BuildConfig.GODOT_PLUGIN_NAME,
                    userAuthenticated,
                    task.result.isAuthenticated
                )
            } else {
                Log.e(tag, "User not signed in. Cause: ${task.exception}", task.exception)
                emitSignal(godot, BuildConfig.GODOT_PLUGIN_NAME, userAuthenticated, false)
            }
        }
    }

    fun signInRequestServerSideAccess(serverClientId: String, forceRefreshToken: Boolean) {
        Log.d(
            tag,
            "Requesting server side access for client id $serverClientId with refresh token $forceRefreshToken"
        )
        gamesSignInClient.requestServerSideAccess(serverClientId, forceRefreshToken)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(tag, "Access granted to server side for user: $serverClientId")
                    emitSignal(
                        godot, BuildConfig.GODOT_PLUGIN_NAME, serverSideAccessRequested, task.result
                    )
                } else {
                    Log.e(
                        tag,
                        "Failed to request server side access. Cause: ${task.exception}",
                        task.exception
                    )
                }
            }
    }

    fun firebaseCheckConnectedUser() {
        Log.d(tag, "firebaseCheckConnectedUser: called")
        val user = Firebase.auth.currentUser
        Log.d(tag, "firebaseCheckConnectedUser: currentUser=${user?.uid ?: "null"}")

        if (user == null) {
            Log.w(tag, "firebaseCheckConnectedUser: no user connected, emitting null")
            emitSignal(godot, BuildConfig.GODOT_PLUGIN_NAME, firebaseCheckConnectedUserSignal, null)
            return
        }

        Log.d(tag, "firebaseCheckConnectedUser: requesting ID token for uid=${user.uid}")
        user.getIdToken(false).addOnCompleteListener { tokenTask ->
            if (tokenTask.isSuccessful) {
                val token = tokenTask.result.token
                emitSignal(
                    godot, BuildConfig.GODOT_PLUGIN_NAME, firebaseCheckConnectedUserSignal, token
                )
            } else {
                Log.e(tag, "firebaseCheckConnectedUser: failed to fetch token")
                emitSignal(
                    godot,
                    BuildConfig.GODOT_PLUGIN_NAME,
                    firebaseCheckConnectedUserSignal,
                    ""
                )
            }
        }
    }

    fun firebaseSignInAnonymously() {
        Log.d(tag, "firebaseSignInAnonymously")
        val auth = Firebase.auth

        auth.signInAnonymously().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d(tag, "SignInAnonymously:success")
                val user = auth.currentUser

                user?.getIdToken(false)?.addOnCompleteListener { tokenTask ->
                    if (tokenTask.isSuccessful) {
                        val token = tokenTask.result.token
                        emitSignal(
                            godot,
                            BuildConfig.GODOT_PLUGIN_NAME,
                            firebaseSignInAnonymouslySignal,
                            token
                        )
                    } else {
                        Log.e(tag, "firebaseSignInAnonymously: failed to fetch token")
                        emitSignal(
                            godot,
                            BuildConfig.GODOT_PLUGIN_NAME,
                            firebaseSignInAnonymouslySignal,
                            ""
                        )
                    }
                }
            } else {
                Log.w(tag, "SignInAnonymously:failure", task.exception)
            }
        }
    }

    fun firebaseAuthWithGoogle() {
        Log.d(tag, "firebaseAuthWithGoogle")
        val activity = godot.getActivity() ?: return
        val resId = activity.resources.getIdentifier(
            "default_web_client_id",
            "string",
            activity.packageName
        )
        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(activity.getString(resId))
            .setFilterByAuthorizedAccounts(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val credentialManager = CredentialManager.create(activity)

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = credentialManager.getCredential(
                    context = activity,
                    request = request
                )

                // Create Google ID Token
                val googleIdTokenCredential =
                    GoogleIdTokenCredential.createFrom(result.credential.data)
                // Sign in to Firebase with using the token
                val credential =
                    GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                val auth = Firebase.auth
                auth.signInWithCredential(credential)
                    .addOnCompleteListener() { task ->
                        if (task.isSuccessful) {
                            // Sign in success, update UI with the signed-in user's information
                            Log.d(tag, "signInWithCredential:success")
                            val user = auth.currentUser
                            user?.getIdToken(false)?.addOnCompleteListener { tokenTask ->
                                if (tokenTask.isSuccessful) {
                                    val token = tokenTask.result.token
                                    emitSignal(
                                        godot,
                                        BuildConfig.GODOT_PLUGIN_NAME,
                                        firebaseAuthWithGoogleSignal,
                                        token
                                    )
                                } else {
                                    Log.e(
                                        tag,
                                        "firebaseAuthWithGoogle: failed to fetch token"
                                    )
                                    emitSignal(
                                        godot,
                                        BuildConfig.GODOT_PLUGIN_NAME,
                                        firebaseAuthWithGoogleSignal,
                                        ""
                                    )
                                }
                            }
                        } else {
                            // If sign in fails, display a message to the user
                            Log.w(tag, "signInWithCredential:failure", task.exception)
                        }
                    }

            } catch (e: GetCredentialException) {
                Log.e("GodotPlugin", "Erreur: ${e.localizedMessage}")
            }
        }
    }

    fun firebaseLinkWithGoogle() {
        Log.d(tag, "firebaseAuthWithGoogle")
        val activity = godot.getActivity() ?: return
        val resId = activity.resources.getIdentifier(
            "default_web_client_id",
            "string",
            activity.packageName
        )
        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(activity.getString(resId))
            .setFilterByAuthorizedAccounts(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val credentialManager = CredentialManager.create(activity)
        CoroutineScope(Dispatchers.Main).launch {

            val result = credentialManager.getCredential(
                context = activity,
                request = request
            )

            // Create Google ID Token
            val googleIdTokenCredential =
                GoogleIdTokenCredential.createFrom(result.credential.data)
            // Sign in to Firebase with using the token
            val credential =
                GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
            val auth = Firebase.auth
            val user = auth.currentUser
            user?.linkWithCredential(credential)?.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(tag, "linkWithCredential:success")
                    emitSignal(
                        godot,
                        BuildConfig.GODOT_PLUGIN_NAME,
                        firebaseLinkWithGoogleSignal,
                        true
                    )

                } else {

                    val exception = task.exception
                    if (exception is FirebaseAuthUserCollisionException) {
                        Log.w(
                            tag,
                            "Google account already linked to another Firebase user"
                        )
                        emitSignal(
                            godot,
                            BuildConfig.GODOT_PLUGIN_NAME,
                            firebaseLinkWithGoogleSignal,
                            false
                        )

                    } else {
                        Log.w(tag, "linkWithCredential:failure", exception)
                    }
                }
            }
        }
    }
}
