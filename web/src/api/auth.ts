import client, { getRefreshToken } from './client'
import type { FirebaseCustomTokenResponse, LoginRequest, TokenPair, User } from '../types/auth'

export async function login(data: LoginRequest): Promise<TokenPair> {
    const response = await client.post<TokenPair>('/auth/login/', data)
    return response.data
}

export async function logout(): Promise<void> {
    await client.post('/auth/logout/', { refresh: getRefreshToken() })
}

export async function getMe(): Promise<User> {
    const response = await client.get<User>('/auth/me/')
    return response.data
}

export async function refreshTokenApi(refresh: string): Promise<TokenPair> {
    const response = await client.post<TokenPair>('/auth/token/refresh/', {
        refresh,
        platform: 'web',
    })
    return response.data
}

export async function getFirebaseCustomToken(platform: 'web' | 'mobile' = 'web'): Promise<FirebaseCustomTokenResponse> {
    const response = await client.post<FirebaseCustomTokenResponse>('/auth/firebase-token/', { platform })
    return response.data
}
