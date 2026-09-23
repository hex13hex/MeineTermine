package com.example.meinetermine

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.HttpURLConnection
import java.net.URL
import com.google.api.services.drive.Drive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GoogleDriveAuth {

    companion object {
        const val DRIVE_FILE_SCOPE =
            "https://www.googleapis.com/auth/drive.file"

        const val REQUEST_CODE_AUTH = 1001
    }

    fun requestDriveAccess(
        activity: Activity,
        onResult: (Boolean) -> Unit
    ) {

        val connectivityManager =
            activity.getSystemService(Context.CONNECTIVITY_SERVICE)
                    as ConnectivityManager

        val network = connectivityManager.activeNetwork

        val capabilities =
            network?.let {
                connectivityManager.getNetworkCapabilities(it)
            }

        val hasInternet =
            capabilities?.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET
            ) == true

        if (!hasInternet) {

            Toast.makeText(
                activity,
                "Нет подключения к интернету",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val request = AuthorizationRequest.Builder()
            .setRequestedScopes(
                listOf(
                    Scope(DRIVE_FILE_SCOPE)
                )
            )
            .build()

        Identity.getAuthorizationClient(activity)
            .authorize(request)
            .addOnSuccessListener { result ->

                if (result.hasResolution()) {

                    result.pendingIntent?.let { pendingIntent ->

                        activity.startIntentSenderForResult(
                            pendingIntent.intentSender,
                            REQUEST_CODE_AUTH,
                            null,
                            0,
                            0,
                            0
                        )
                    }

                } else {

                    val accessToken = result.accessToken

                    if (
                        accessToken != null &&
                        result.grantedScopes.contains(DRIVE_FILE_SCOPE)
                    ) {

                        checkDriveWithToken(
                            activity,
                            accessToken
                        ) { connected ->

                            if (connected) {
                                showConnectedMessage(activity)
                            }

                            onResult(connected)
                        }

                    } else {

                        Toast.makeText(
                            activity,
                            "Google Drive не подключён",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .addOnFailureListener { exception ->

                Toast.makeText(
                    activity,
                    "Ошибка Google: ${exception.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    fun handleAuthorizationResult(
        activity: Activity,
        data: Intent?,
        onResult: (Boolean) -> Unit
    ) {
        if (data == null) {
            onResult(false)
            return
        }

        try {
            val result =
                Identity.getAuthorizationClient(activity)
                    .getAuthorizationResultFromIntent(data)

            val connected =
                result.accessToken != null &&
                        result.grantedScopes.contains(DRIVE_FILE_SCOPE)

            onResult(connected)

            if (connected) {
                showConnectedMessage(activity)
            }

        } catch (e: Exception) {

            Toast.makeText(
                activity,
                "Ошибка Google: ${e.message}",
                Toast.LENGTH_LONG
            ).show()

            onResult(false)
        }
    }

    fun checkDriveAccess(
        activity: Activity,
        onResult: (Boolean) -> Unit
    ) {

        val connectivityManager =
            activity.getSystemService(Context.CONNECTIVITY_SERVICE)
                    as ConnectivityManager

        val network = connectivityManager.activeNetwork

        val capabilities =
            network?.let {
                connectivityManager.getNetworkCapabilities(it)
            }

        val hasInternet =
            capabilities?.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET
            ) == true

        if (!hasInternet) {
            onResult(false)
            return
        }

        val request = AuthorizationRequest.Builder()
            .setRequestedScopes(
                listOf(
                    Scope(DRIVE_FILE_SCOPE)
                )
            )
            .build()

        Identity.getAuthorizationClient(activity)
            .authorize(request)
            .addOnSuccessListener { result ->

                val connected =
                    !result.hasResolution() &&
                            result.accessToken != null &&
                            result.grantedScopes.contains(DRIVE_FILE_SCOPE)

                onResult(connected)
            }
            .addOnFailureListener {
                onResult(false)
            }
    }

    private fun showConnectedMessage(activity: Activity) {

        Toast.makeText(
            activity,
            "Google Drive подключён",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun checkDriveWithToken(
        activity: Activity,
        accessToken: String,
        onResult: (Boolean) -> Unit
    ) {

        CoroutineScope(Dispatchers.IO).launch {

            try {

                val url = URL(
                    "https://www.googleapis.com/drive/v3/about?fields=user"
                )

                val connection =
                    url.openConnection() as HttpURLConnection

                connection.requestMethod = "GET"

                connection.setRequestProperty(
                    "Authorization",
                    "Bearer $accessToken"
                )

                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000

                val responseCode = connection.responseCode

                connection.disconnect()

                CoroutineScope(Dispatchers.Main).launch {
                    onResult(responseCode == HttpURLConnection.HTTP_OK)
                }

            } catch (e: Exception) {

                CoroutineScope(Dispatchers.Main).launch {
                    onResult(false)
                }
            }
        }
    }
}