import axios, { type InternalAxiosRequestConfig } from 'axios'

const API_BASE = '/api'

const client = axios.create({
    baseURL: API_BASE,
    headers: { 'Content-Type': 'application/json' },
})

let accessToken: string | null = null
let refreshToken: string | null = null
let isRefreshing = false
let failedQueue: Array<{
    resolve: (token: string) => void
    reject: (error: unknown) => void
}> = []

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
    tokenChannel.postMessage({ type: 'tokens-updated', access, refresh })
}

export function clearTokens() {
    accessToken = null
    refreshToken = null
    tokenChannel.postMessage({ type: 'tokens-cleared' })
}

export function getAccessToken() {
    return accessToken
}

function processQueue(error: unknown, token: string | null) {
    failedQueue.forEach((p) => {
        if (error) p.reject(error)
        else if (token) p.resolve(token)
    })
    failedQueue = []
}

// Request interceptor: attach Authorization header
client.interceptors.request.use((config: InternalAxiosRequestConfig) => {
    if (accessToken && config.headers) {
        config.headers.Authorization = `Bearer ${accessToken}`
    }
    return config
})

// Response interceptor: auto-refresh on 401
client.interceptors.response.use(
    (response) => response,
    async (error) => {
        const original = error.config
        if (error.response?.status === 401 && !original._retry && refreshToken) {
            if (isRefreshing) {
                return new Promise((resolve, reject) => {
                    failedQueue.push({
                        resolve: (token) => {
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
                const { data } = await axios.post(`${API_BASE}/auth/token/refresh/`, {
                    refresh: refreshToken,
                    platform: 'web',
                })
                accessToken = data.access
                if (data.refresh) refreshToken = data.refresh
                processQueue(null, data.access)
                original.headers.Authorization = `Bearer ${data.access}`
                return client(original)
            } catch (refreshError) {
                processQueue(refreshError, null)
                clearTokens()
                window.location.href = '/login'
                return Promise.reject(refreshError)
            } finally {
                isRefreshing = false
            }
        }
        return Promise.reject(error)
    }
)

export default client
