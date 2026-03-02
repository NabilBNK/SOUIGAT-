import { createContext, useState, useCallback, useEffect, type ReactNode } from 'react'
import type { AuthState } from '../types/auth'
import { login as loginApi, logout as logoutApi, getMe } from '../api/auth'
import { setTokens, clearTokens, getAccessToken } from '../api/client'

interface AuthContextValue extends AuthState {
    login: (phone: string, password: string) => Promise<void>
    logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
    const [state, setState] = useState<AuthState>({
        user: null,
        accessToken: null,
        refreshToken: null,
        isAuthenticated: false,
        isLoading: true,
    })

    // Attempt to restore session
    useEffect(() => {
        let mounted = true
        // Without sessionStorage, we must check if tokens exist in client memory (though typically they wouldn't on full reload)
        getMe()
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
        clearTokens()
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
