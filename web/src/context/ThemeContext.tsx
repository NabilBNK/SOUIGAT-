import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'

export type ThemeMode = 'dark' | 'light'

type ThemeContextValue = {
    theme: ThemeMode
    setTheme: (next: ThemeMode) => void
    toggleTheme: () => void
}

const STORAGE_KEY = 'souigat_theme'

const ThemeContext = createContext<ThemeContextValue | undefined>(undefined)

function readInitialTheme(): ThemeMode {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved === 'dark' || saved === 'light') {
        return saved
    }
    return 'dark'
}

export function ThemeProvider({ children }: { children: ReactNode }) {
    const [theme, setThemeState] = useState<ThemeMode>(() => readInitialTheme())

    useEffect(() => {
        document.documentElement.setAttribute('data-theme', theme)
        localStorage.setItem(STORAGE_KEY, theme)
    }, [theme])

    const value = useMemo<ThemeContextValue>(() => ({
        theme,
        setTheme: (next: ThemeMode) => setThemeState(next),
        toggleTheme: () => setThemeState((prev) => (prev === 'dark' ? 'light' : 'dark')),
    }), [theme])

    return (
        <ThemeContext.Provider value={value}>
            {children}
        </ThemeContext.Provider>
    )
}

// eslint-disable-next-line react-refresh/only-export-components
export function useTheme() {
    const ctx = useContext(ThemeContext)
    if (!ctx) {
        throw new Error('useTheme must be used inside ThemeProvider')
    }
    return ctx
}
