import { useAuth } from '../../hooks/useAuth'
import { LogOut, User as UserIcon } from 'lucide-react'
import { ROLE_LABELS, DEPARTMENT_LABELS } from '../../utils/constants'

export function Header() {
    const { user, logout } = useAuth()
    if (!user) return null

    const roleBadge = ROLE_LABELS[user.role]
    const deptBadge = user.department ? DEPARTMENT_LABELS[user.department] : null

    return (
        <header className="h-16 bg-surface-800/70 backdrop-blur-sm border-b border-surface-600/50 flex items-center justify-between px-6 sticky top-0 z-20">
            <div />
            <div className="flex items-center gap-4">
                {/* Role badge */}
                <div className="flex items-center gap-2">
                    <span className="px-2.5 py-1 rounded-sm bg-brand-500/15 text-brand-400 text-[11px] font-semibold tracking-wide uppercase">
                        {roleBadge}
                    </span>
                    {deptBadge && (
                        <span className="px-2.5 py-1 rounded-sm bg-accent-500/15 text-accent-400 text-[11px] font-semibold tracking-wide uppercase">
                            {deptBadge}
                        </span>
                    )}
                </div>

                {/* User info */}
                <div className="flex items-center gap-2.5 pl-4 border-l border-surface-600/50">
                    <div className="w-8 h-8 rounded-sm bg-surface-600 flex items-center justify-center">
                        <UserIcon className="w-4 h-4 text-text-secondary" />
                    </div>
                    <div className="hidden sm:block">
                        <p className="text-[13px] font-medium text-text-primary leading-tight">
                            {user.first_name} {user.last_name}
                        </p>
                        <p className="text-[11px] text-text-muted">{user.phone}</p>
                    </div>
                </div>

                {/* Logout */}
                <button
                    onClick={logout}
                    className="p-2 rounded-sm text-text-muted hover:text-status-error hover:bg-status-error/10 transition-colors duration-150"
                    title="Déconnexion"
                >
                    <LogOut className="w-4 h-4" />
                </button>
            </div>
        </header>
    )
}
