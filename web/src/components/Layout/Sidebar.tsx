import { NavLink } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'
import {
    LayoutDashboard,
    Bus,
    Package,
    FileText,
    Users,
    Shield,
    AlertTriangle,
    BarChart3,
    Ticket,
    Landmark,
    Route,
} from 'lucide-react'
import type { Role } from '../../types/auth'

interface NavItem {
    label: string
    to: string
    icon: React.FC<{ className?: string }>
    roles: Role[]
    departments?: string[]
}

const navItems: NavItem[] = [
    // Office
    { label: 'Tableau de bord', to: '/office', icon: LayoutDashboard, roles: ['admin', 'office_staff'] },
    { label: 'Voyages', to: '/office/trips', icon: Bus, roles: ['admin', 'office_staff'] },
    { label: 'Billets Passagers', to: '/office/tickets', icon: Ticket, roles: ['admin', 'office_staff'] },
    { label: 'Rapports', to: '/office/reports', icon: FileText, roles: ['admin'] },
    // Cargo
    { label: 'Colis', to: '/cargo', icon: Package, roles: ['admin', 'office_staff'], departments: ['all', 'cargo'] },
    // Admin
    { label: 'Administration', to: '/admin', icon: Shield, roles: ['admin'] },
    { label: 'Utilisateurs', to: '/admin/users', icon: Users, roles: ['admin'] },
    { label: 'Autobus', to: '/admin/buses', icon: Bus, roles: ['admin'] },
    { label: 'Templates Route', to: '/admin/templates', icon: Route, roles: ['admin'] },
    { label: 'Reglements', to: '/admin/settlements', icon: Landmark, roles: ['admin'] },
    { label: "Journal d'audit", to: '/admin/audit', icon: BarChart3, roles: ['admin'] },
    { label: 'Quarantaine', to: '/admin/quarantine', icon: AlertTriangle, roles: ['admin', 'office_staff'] },
]

export function Sidebar() {
    const { user } = useAuth()
    if (!user) return null

    const visible = navItems.filter((item) => {
        if (!item.roles.includes(user.role)) return false
        if (item.departments && user.department && !item.departments.includes(user.department)) return false
        return true
    })

    return (
        <aside
            className="fixed left-0 top-0 bottom-0 w-64 backdrop-blur-xl border-r border-[var(--sidebar-border)] shadow-2xl flex flex-col z-30"
            style={{ background: 'var(--sidebar-bg)' }}
        >
            {/* Logo */}
            <div className="h-20 flex items-center px-6 border-b border-[var(--sidebar-border-soft)]">
                <div className="flex items-center gap-3">
                    <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-[var(--sidebar-logo-from)] to-[var(--sidebar-logo-to)] border border-[var(--sidebar-border-soft)] shadow-lg flex items-center justify-center">
                        <Bus className="w-5 h-5 text-[var(--sidebar-accent)]" />
                    </div>
                    <div>
                        <span className="block text-lg font-bold tracking-tight text-[var(--sidebar-text)] uppercase leading-tight">SOUIGAT</span>
                        <span className="block text-[10px] font-semibold tracking-wider text-[var(--sidebar-accent)] uppercase leading-none">Transport</span>
                    </div>
                </div>
            </div>

            {/* Navigation */}
            <nav className="flex-1 overflow-y-auto py-5 px-4 space-y-1">
                {visible.map((item) => (
                    <NavLink
                        key={item.to}
                        to={item.to}
                        end={item.to === '/office' || item.to === '/cargo' || item.to === '/admin'}
                        className={({ isActive }) =>
                            `flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-all duration-200 outline-none focus-visible:ring-2 focus-visible:ring-brand-500 ${
                                isActive
                                    ? 'bg-[var(--sidebar-active-bg)] text-[var(--sidebar-active-text)] shadow-inner'
                                    : 'text-[var(--sidebar-muted)] hover:text-[var(--sidebar-text)] hover:bg-[var(--sidebar-hover-bg)]'
                            }`
                        }
                    >
                        <item.icon className="w-[18px] h-[18px] shrink-0" />
                        <span>{item.label}</span>
                    </NavLink>
                ))}
            </nav>

            {/* Version */}
            <div className="px-6 py-4 border-t border-[var(--sidebar-border-soft)] flex flex-col gap-1">
                <p className="text-xs text-[var(--sidebar-muted)] font-medium">SOUIGAT v1.0</p>
                <p className="text-[10px] text-[var(--sidebar-muted)]/80 tracking-wide uppercase">Phase 2 — Admin</p>
            </div>
        </aside>
    )
}

