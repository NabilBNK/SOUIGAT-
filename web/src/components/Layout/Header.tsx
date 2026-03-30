import { useAuth } from '../../hooks/useAuth'
import { LogOut, MoonStar, Sun, User as UserIcon } from 'lucide-react'
import { ROLE_LABELS, DEPARTMENT_LABELS } from '../../utils/constants'
import { useTheme } from '../../context/ThemeContext'

export function Header() {
    const { user, logout } = useAuth()
    const { theme, toggleTheme } = useTheme()
    if (!user) return null

    const roleBadge = ROLE_LABELS[user.role]
    const deptBadge = user.department ? DEPARTMENT_LABELS[user.department] : null

    return (
        <header className="h-20 bg-surface-900/80 backdrop-blur-xl border-b border-surface-700 shadow-md flex items-center justify-between px-8 sticky top-0 z-20">
            <div className="flex flex-col gap-1">
                <div className="px-3 py-1 rounded-md border text-xs font-semibold tracking-wide uppercase text-blue-300 border-blue-500/30 bg-blue-500/10">
                    backend sync source
                </div>
                <div className="text-[10px] text-text-muted tracking-wide">
                    Client queue status hidden (legacy path)
                </div>
            </div>
            <div className="flex items-center gap-5">
                <button
                    type="button"
                    onClick={toggleTheme}
                    className="inline-flex items-center gap-2 rounded-lg border border-surface-700 bg-surface-800/80 px-3 py-2 text-xs font-semibold tracking-wide text-text-secondary hover:text-text-primary hover:border-brand-500/40 transition-colors"
                    title={theme === 'dark' ? 'Activer le theme clair' : 'Activer le theme sombre'}
                >
                    {theme === 'dark' ? <Sun className="w-4 h-4" /> : <MoonStar className="w-4 h-4" />}
                    <span>{theme === 'dark' ? 'Clair' : 'Sombre'}</span>
                </button>

                {/* Role badge */}
                <div className="flex items-center gap-2">
                    <span className="px-3 py-1 rounded-md bg-brand-500/15 border border-brand-500/20 text-brand-400 text-xs font-bold tracking-wider uppercase shadow-sm">
                        {roleBadge}
                    </span>
                    {deptBadge && (
                        <span className="px-3 py-1 rounded-md bg-accent-500/15 border border-accent-500/20 text-accent-400 text-xs font-bold tracking-wider uppercase shadow-sm">
                            {deptBadge}
                        </span>
                    )}
                </div>

                {/* User info */}
                <div className="flex items-center gap-3 pl-5 border-l border-surface-700">
                    <div className="w-9 h-9 rounded-lg bg-surface-700 border border-surface-600 flex items-center justify-center shadow-inner">
                        <UserIcon className="w-4 h-4 text-text-secondary" />
                    </div>
                    <div className="hidden sm:block">
                        <p className="text-sm font-semibold text-text-primary leading-tight">
                            {user.first_name} {user.last_name}
                        </p>
                        <p className="text-xs text-text-muted mt-0.5">{user.phone}</p>
                    </div>
                </div>

                {/* Logout */}
                <button
                    onClick={logout}
                    className="p-2 rounded-lg text-text-muted hover:text-red-400 hover:bg-red-500/10 transition-all duration-200 outline-none focus-visible:ring-2 focus-visible:ring-red-400"
                    title="Déconnexion"
                >
                    <LogOut className="w-[18px] h-[18px]" />
                </button>
            </div>
        </header>
    )
}
