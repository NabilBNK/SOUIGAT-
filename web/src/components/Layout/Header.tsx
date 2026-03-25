import { useAuth } from '../../hooks/useAuth'
import { LogOut, User as UserIcon } from 'lucide-react'
import { ROLE_LABELS, DEPARTMENT_LABELS } from '../../utils/constants'
import { useSyncStatus } from '../../hooks/useSyncStatus'

export function Header() {
    const { user, logout } = useAuth()
    const syncStatus = useSyncStatus()
    if (!user) return null

    const roleBadge = ROLE_LABELS[user.role]
    const deptBadge = user.department ? DEPARTMENT_LABELS[user.department] : null
    const queuedRecords = syncStatus.summary.pending + syncStatus.summary.inProgress
    const syncProblems = syncStatus.summary.failed + syncStatus.summary.conflict

    const syncLabel = syncProblems > 0
        ? `${syncProblems} sync issue${syncProblems > 1 ? 's' : ''}`
        : queuedRecords > 0
            ? `${queuedRecords} pending sync`
            : 'sync healthy'

    const syncClassName = syncProblems > 0
        ? 'text-red-400 border-red-500/30 bg-red-500/10'
        : queuedRecords > 0
            ? 'text-yellow-400 border-yellow-500/30 bg-yellow-500/10'
            : 'text-emerald-400 border-emerald-500/25 bg-emerald-500/10'

    const perEntityLabel = [
        {
            key: 'TR',
            counts: syncStatus.byEntity.trip,
        },
        {
            key: 'PT',
            counts: syncStatus.byEntity.passenger_ticket,
        },
        {
            key: 'CG',
            counts: syncStatus.byEntity.cargo_ticket,
        },
        {
            key: 'EX',
            counts: syncStatus.byEntity.trip_expense,
        },
        {
            key: 'ST',
            counts: syncStatus.byEntity.settlement,
        },
    ]
        .map(({ key, counts }) => {
            const queued = counts.pending + counts.inProgress
            const issues = counts.failed + counts.conflict
            return `${key}:${queued}/${issues}`
        })
        .join(' ')

    return (
        <header className="h-20 bg-surface-900/80 backdrop-blur-xl border-b border-surface-700 shadow-md flex items-center justify-between px-8 sticky top-0 z-20">
            <div className="flex flex-col gap-1">
                <div className={`px-3 py-1 rounded-md border text-xs font-semibold tracking-wide uppercase ${syncClassName}`}>
                    {syncLabel}
                </div>
                <div className="text-[10px] text-text-muted font-mono tracking-wide">
                    {perEntityLabel}
                </div>
            </div>
            <div className="flex items-center gap-5">
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
