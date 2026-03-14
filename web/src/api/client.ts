import axios, { type InternalAxiosRequestConfig } from 'axios'

const API_BASE = '/api'

const client = axios.create({
    baseURL: API_BASE,
    headers: { 'Content-Type': 'application/json' },
})

let accessToken: string | null = localStorage.getItem('souigat_access')
let refreshToken: string | null = localStorage.getItem('souigat_refresh')
let isRefreshing = false
let failedQueue: Array<{
    resolve: (token: string) => void
    reject: (error: unknown) => void
}> = []

export const authEvents = new EventTarget()

const tokenChannel = new BroadcastChannel('souigat:tokens')

tokenChannel.onmessage = (event) => {
    if (event.data.type === 'tokens-updated') {
        accessToken = event.data.access
        refreshToken = event.data.refresh
    } else if (event.data.type === 'tokens-cleared') {
        accessToken = null
        refreshToken = null
    }
}

export function setTokens(access: string, refresh: string) {
    accessToken = access
    refreshToken = refresh
    localStorage.setItem('souigat_access', access)
    localStorage.setItem('souigat_refresh', refresh)
    tokenChannel.postMessage({ type: 'tokens-updated', access, refresh })
}

export function clearTokens() {
    accessToken = null
    refreshToken = null
    localStorage.removeItem('souigat_access')
    localStorage.removeItem('souigat_refresh')
    tokenChannel.postMessage({ type: 'tokens-cleared' })
}

export function getAccessToken() {
    return accessToken
}

export function getRefreshToken() {
    return refreshToken
}

function processQueue(error: unknown, token: string | null) {
    failedQueue.forEach((p) => {
        if (error) p.reject(error)
        else if (token) p.resolve(token)
    })
    failedQueue = []
}

function isPublicAuthRoute(url?: string) {
    if (!url) return false
    return url.includes('/auth/login/') || url.includes('/auth/token/refresh/')
}

// Request interceptor: attach Authorization header
client.interceptors.request.use((config: InternalAxiosRequestConfig) => {
    if (accessToken && config.headers && !isPublicAuthRoute(config.url)) {
        config.headers.Authorization = `Bearer ${accessToken}`
    }
    return config
})

// Response interceptor: auto-refresh on 401
client.interceptors.response.use(
    (response) => response,
    async (error) => {
        const original = error.config

        // Prevent infinite loops ONLY for login or refresh itself
        if (isPublicAuthRoute(original.url)) {
            return Promise.reject(error)
        }

        if (error.response?.status === 401 && !original._retry) {
            if (!refreshToken) {
                clearTokens()
                authEvents.dispatchEvent(new Event('unauthorized'))
                return Promise.reject(error)
            }

            if (isRefreshing) {
                return new Promise((resolve, reject) => {
                    failedQueue.push({
                        resolve: (token) => {
                            original.headers = original.headers ?? {}
                            original.headers.Authorization = `Bearer ${token}`
                            resolve(client(original))
                        },
                        reject,
                    })
                })
            }

            original._retry = true
            isRefreshing = true

            try {
                // Using a fresh axios instance to avoid interceptor loops
                const { data } = await axios.post(`${API_BASE}/auth/token/refresh/`, {
                    refresh: refreshToken,
                    platform: 'web',
                }, { withCredentials: true })
                setTokens(data.access, data.refresh || refreshToken)

                processQueue(null, data.access)
                original.headers = original.headers ?? {}
                original.headers.Authorization = `Bearer ${data.access}`
                return client(original)
            } catch (refreshError) {
                console.error('[CLIENT] Attempting to redirect to login after refresh failure');
                processQueue(refreshError, null)
                clearTokens()
                authEvents.dispatchEvent(new Event('unauthorized'))
                return Promise.reject(refreshError)
            } finally {
                isRefreshing = false
            }
        }
        return Promise.reject(error)
    }
)

export default client
