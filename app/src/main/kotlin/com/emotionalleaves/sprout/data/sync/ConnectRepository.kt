package com.emotionalleaves.sprout.data.sync

import com.emotionalleaves.sprout.data.secure.SecureStore
import com.emotionalleaves.sprout.data.settings.SettingsStore
import com.emotionalleaves.sprout.data.simplefin.AccessUrl
import com.emotionalleaves.sprout.data.simplefin.ClaimResult
import com.emotionalleaves.sprout.data.simplefin.SetupToken
import com.emotionalleaves.sprout.data.simplefin.SetupTokenResult
import com.emotionalleaves.sprout.data.simplefin.SimpleFinClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface ConnectResult {
    data object Connected : ConnectResult
    data class InvalidToken(val reason: SetupTokenResult.Reason) : ConnectResult
    data object AlreadyUsed : ConnectResult
    data object NetworkProblem : ConnectResult
    data class ServerProblem(val httpCode: Int) : ConnectResult
    data object BadAccessUrl : ConnectResult
    data object StorageFailed : ConnectResult
}

/**
 * Claims a setup token and stores the access URL. Invalid tokens never cause a network request.
 * A new token replaces the stored access URL but keeps every bit of local data.
 */
class ConnectRepository(
    private val client: SimpleFinClient,
    private val secureStore: SecureStore,
    private val settings: SettingsStore,
) {
    suspend fun connect(token: String): ConnectResult = withContext(Dispatchers.IO) {
        val claimUrl = when (val decoded = SetupToken.decode(token)) {
            is SetupTokenResult.Invalid -> return@withContext ConnectResult.InvalidToken(decoded.reason)
            is SetupTokenResult.Valid -> decoded.claimUrl
        }
        when (val claim = client.claim(claimUrl)) {
            is ClaimResult.Success -> {
                if (AccessUrl.parse(claim.accessUrl) == null) return@withContext ConnectResult.BadAccessUrl
                try {
                    secureStore.saveAccessUrl(claim.accessUrl)
                } catch (e: Exception) {
                    return@withContext ConnectResult.StorageFailed
                }
                settings.onNewConnection()
                ConnectResult.Connected
            }
            ClaimResult.AlreadyUsed -> ConnectResult.AlreadyUsed
            is ClaimResult.NetworkError -> ConnectResult.NetworkProblem
            is ClaimResult.ServerError -> ConnectResult.ServerProblem(claim.httpCode)
            ClaimResult.BadResponse -> ConnectResult.BadAccessUrl
        }
    }
}
