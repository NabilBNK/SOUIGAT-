import client from './client'
import type { LoginRequest, TokenPair, User } from '../types/auth'

export async function login(data: LoginRequest): Promise<TokenPair> {
    const response = await client.post<TokenPair>('/auth/login/', data)
    return response.data
}

export async function logout(): Promise<void> {
    await client.post('/auth/logout/')
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
