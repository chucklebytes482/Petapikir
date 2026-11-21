package com.example.sync

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.MessageDigest
import kotlin.coroutines.resume

sealed class DriveAuthResult {
    data class Authorized(val accessToken: String) : DriveAuthResult()
    data class NeedsResolution(val pendingIntent: PendingIntent) : DriveAuthResult()
    data class Failed(val message: String) : DriveAuthResult()
}

class GoogleDriveAuthManager(private val context: Context) {

    @Volatile
    private var cachedToken: String? = null

    @Volatile
    private var cachedTokenAt: Long = 0L

    companion object {
        private val SCOPES = listOf(
            Scope("https://www.googleapis.com/auth/drive")
        )
        private const val TOKEN_CACHE_TTL_MS = 45 * 60 * 1000L
    }

    fun invalidateToken() {
        cachedToken = null
        cachedTokenAt = 0L
    }

    suspend fun getAccessToken(
        activity: Activity? = null,
        forceRefresh: Boolean = false
    ): DriveAuthResult {
        val now = System.currentTimeMillis()
        val existing = cachedToken
        if (!forceRefresh && !existing.isNullOrBlank() && (now - cachedTokenAt) < TOKEN_CACHE_TTL_MS) {
            return DriveAuthResult.Authorized(existing)
        }

        val request = AuthorizationRequest.builder()
            .setRequestedScopes(SCOPES)
            .build()

        val client = if (activity != null) {
            Identity.getAuthorizationClient(activity)
        } else {
            Identity.getAuthorizationClient(context)
        }

        return suspendCancellableCoroutine { continuation ->
            client.authorize(request)
                .addOnSuccessListener { result ->
                    if (result.hasResolution()) {
                        val pending = result.pendingIntent
                        if (pending != null) {
                            continuation.resume(DriveAuthResult.NeedsResolution(pending))
                        } else {
                            continuation.resume(DriveAuthResult.Failed("Izin Google Drive belum selesai."))
                        }
                    } else {
                        val token = result.accessToken
                        if (!token.isNullOrBlank()) {
                            cachedToken = token
                            cachedTokenAt = System.currentTimeMillis()
                            continuation.resume(DriveAuthResult.Authorized(token))
                        } else {
                            continuation.resume(DriveAuthResult.Failed("Token akses Google Drive tidak tersedia."))
                        }
                    }
                }
                .addOnFailureListener { error ->
                    val readable = if (error is ApiException) {
                        formatApiExceptionMessage(error.statusCode)
                    } else {
                        error.localizedMessage ?: "Gagal terhubung ke Google Drive."
                    }
                    continuation.resume(DriveAuthResult.Failed(readable))
                }
        }
    }

    fun parseAuthorizationIntentResult(
        activity: Activity? = null,
        data: Intent?
    ): DriveAuthResult {
        if (data == null) {
            return DriveAuthResult.Failed("Otorisasi dibatalkan.")
        }
        val client = if (activity != null) {
            Identity.getAuthorizationClient(activity)
        } else {
            Identity.getAuthorizationClient(context)
        }
        return try {
            val result = client.getAuthorizationResultFromIntent(data)
            val token = result.accessToken
            if (!token.isNullOrBlank()) {
                cachedToken = token
                cachedTokenAt = System.currentTimeMillis()
                DriveAuthResult.Authorized(token)
            } else {
                DriveAuthResult.Failed("Token akses tidak diterima.")
            }
        } catch (e: ApiException) {
            DriveAuthResult.Failed(formatApiExceptionMessage(e.statusCode))
        } catch (e: Exception) {
            DriveAuthResult.Failed(e.localizedMessage ?: "Otorisasi dibatalkan.")
        }
    }

    private fun formatApiExceptionMessage(statusCode: Int): String {
        return when (statusCode) {
            8, 10 -> {
                val sha1 = getAppSha1Fingerprint() ?: "tidak terbaca"
                "Otorisasi Google Drive gagal (kode $statusCode). " +
                    "Pastikan Android OAuth Client ID di Google Cloud Console cocok dengan " +
                    "Package: ${context.packageName} & SHA-1: $sha1, serta Google Drive API aktif."
            }
            16, 12501 -> "Otorisasi Google Drive dibatalkan oleh pengguna."
            7 -> "Tidak ada koneksi internet untuk otorisasi Google Drive."
            else -> "Otorisasi Google Drive gagal (kode $statusCode)."
        }
    }

    @Suppress("DEPRECATION")
    fun getAppSha1Fingerprint(): String? = runCatching {
        val pm = context.packageManager
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val signingInfo = info.signingInfo
            if (signingInfo?.hasMultipleSigners() == true) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo?.signingCertificateHistory
            }
        } else {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
        }
        val certBytes = signatures?.firstOrNull()?.toByteArray() ?: return@runCatching null
        val digest = MessageDigest.getInstance("SHA-1").digest(certBytes)
        digest.joinToString(":") { "%02X".format(it) }
    }.getOrNull()
}
