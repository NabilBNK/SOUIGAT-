package com.souigat.mobile.data.remote.interceptor

import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject

/**
 * Handles 401 Unauthorized responses by refreshing the JWT access token.
 *
 * TODO Phase 3.1: Inject AuthRepository and implement refresh flow:
 *   1. POST /api/auth/token/refresh/ with refresh token
 *   2. Store new access token
 *   3. Retry original request with new token
 *   4. If refresh fails, clear tokens and navigate to login
 *
 * For now, returns null (no retry) on 401.
 */
class TokenRefreshAuthenticator @Inject constructor() : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // TODO Phase 3.1: Implement token refresh
        return null // null = do not retry, let 401 propagate
    }
}
