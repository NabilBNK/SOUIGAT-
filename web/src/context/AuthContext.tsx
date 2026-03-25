import { createContext, useState, useCallback, useEffect, type ReactNode } from 'react'
import type { AuthState } from '../types/auth'
import { login as loginApi, logout as logoutApi, getMe } from '../api/auth'
import { setTokens, clearTokens, getAccessToken, authEvents } from '../api/client'
import { disconnectFirebaseSession, ensureFirebaseSession } from '../firebase/auth'

interface AuthContextValue extends AuthState {
    login: (phone: string, password: string) => Promise<void>
    logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

// Prevent duplicate bootstrap /auth/me calls in React StrictMode.
let bootstrapMePromise: Promise<Awaited<ReturnType<typeof getMe>>> | null = null
let bootstrapMeResult: Awaited<ReturnType<typeof getMe>> | null | undefined = undefined

export function AuthProvider({ children }: { children: ReactNode }) {
    const [state, setState] = useState<AuthState>({
        user: null,
        accessToken: null,
        refreshToken: null,
        isAuthenticated: false,
        isLoading: true,
    })

    // Listen for 401 unauthorized events from the API client
    useEffect(() => {
        const handler = () => {
            void disconnectFirebaseSession()
            clearTokens()
            bootstrapMeResult = null
            setState({
                user: null,
                accessToken: null,
                refreshToken: null,
                isAuthenticated: false,
                isLoading: false,
            })
        }
        authEvents.addEventListener('unauthorized', handler)
        return () => authEvents.removeEventListener('unauthorized', handler)
    }, [])

    useEffect(() => {
        if (!state.isAuthenticated) {
            void disconnectFirebaseSession()
            return
        }

        const initFirebaseSync = async () => {
            await ensureFirebaseSession()
        }

        void initFirebaseSync()
    }, [state.isAuthenticated])

    // Attempt to restore session
    useEffect(() => {
        let mounted = true

        const access = getAccessToken()
        if (!access) {
            bootstrapMeResult = null
            if (mounted) {
                setState({
                    user: null,
                    accessToken: null,
                    refreshToken: null,
                    isAuthenticated: false,
                    isLoading: false,
                })
            }
            return () => {
                mounted = false
            }
        }

        if (bootstrapMeResult !== undefined) {
            if (mounted) {
                if (bootstrapMeResult) {
                    setState({
                        user: bootstrapMeResult,
                        accessToken: getAccessToken(),
                        refreshToken: null,
                        isAuthenticated: true,
                        isLoading: false,
                    })
                } else {
                    clearTokens()
                    setState({
                        user: null,
                        accessToken: null,
                        refreshToken: null,
                        isAuthenticated: false,
                        isLoading: false,
                    })
                }
            }
            return () => {
                mounted = false
            }
        }

        if (!bootstrapMePromise) {
            bootstrapMePromise = getMe()
                .then((user) => {
                    bootstrapMeResult = user
                    return user
                })
                .catch((error) => {
                    bootstrapMeResult = null
                    throw error
                })
                .finally(() => {
                    bootstrapMePromise = null
                })
        }

        bootstrapMePromise
            .then((user) => {
                if (mounted) {
                    setState({
                        user,
                        accessToken: getAccessToken(),
                        refreshToken: null, // Refresh token is typically managed via cookie or mem
                        isAuthenticated: true,
                        isLoading: false,
                    })
                }
            })
            .catch(() => {
                clearTokens()
                bootstrapMeResult = null
                if (mounted) {
                    setState({
                        user: null,
                        accessToken: null,
                        refreshToken: null,
                        isAuthenticated: false,
                        isLoading: false,
                    })
                }
            })

        return () => {
            mounted = false
        }
    }, [])


    const login = useCallback(async (phone: string, password: string) => {
        const tokens = await loginApi({ phone, password, platform: 'web' })
        setTokens(tokens.access, tokens.refresh)
        const user = await getMe()
        bootstrapMeResult = user
        setState({
            user,
            accessToken: tokens.access,
            refreshToken: tokens.refresh,
            isAuthenticated: true,
            isLoading: false,
        })
    }, [])

    const logout = useCallback(async () => {
        try {
            await logoutApi()
        } catch {
            // Ignore logout errors
        }
        void disconnectFirebaseSession()
        clearTokens()
        bootstrapMeResult = null
        setState({
            user: null,
            accessToken: null,
            refreshToken: null,
            isAuthenticated: false,
            isLoading: false,
        })
    }, [])

    return (
        <AuthContext.Provider value={{ ...state, login, logout }}>
            {children}
        </AuthContext.Provider>
    )
}

export { AuthContext, type AuthContextValue }
