import { NavLink } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'
import {
    LayoutDashboard,
    Bus,
    Ticket,
    Package,
    FileText,
    Users,
    Shield,
    AlertTriangle,
    Settings,
    BarChart3,
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
    { label: 'Billets Passagers', to: '/office/tickets', icon: Ticket, roles: ['admin', 'office_staff'], departments: ['all', 'passenger'] },
    { label: 'Rapports', to: '/office/reports', icon: FileText, roles: ['admin', 'office_staff'] },
    // Cargo
    { label: 'Colis', to: '/cargo', icon: Package, roles: ['admin', 'office_staff'], departments: ['all', 'cargo'] },
    // Admin
    { label: 'Administration', to: '/admin', icon: Shield, roles: ['admin'] },
    { label: 'Utilisateurs', to: '/admin/users', icon: Users, roles: ['admin'] },
    { label: 'Autobus', to: '/admin/buses', icon: Bus, roles: ['admin'] },
    { label: 'Tarification', to: '/admin/pricing', icon: Settings, roles: ['admin'] },
    { label: 'Journal d\'audit', to: '/admin/audit', icon: BarChart3, roles: ['admin'] },
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
        <aside className="fixed left-0 top-0 bottom-0 w-60 bg-surface-800 border-r border-surface-600/50 flex flex-col z-30">
            {/* Logo */}
            <div className="h-16 flex items-center px-5 border-b border-surface-600/50">
                <div className="flex items-center gap-2.5">
                    <div className="w-8 h-8 rounded-md bg-brand-500 flex items-center justify-center">
                        <Bus className="w-4 h-4 text-white" />
                    </div>
                    <span className="text-lg font-semibold tracking-tight text-text-primary">SOUIGAT</span>
                </div>
            </div>

            {/* Navigation */}
            <nav className="flex-1 overflow-y-auto py-4 px-3 space-y-0.5">
                {visible.map((item) => (
                    <NavLink
                        key={item.to}
                        to={item.to}
                        end={item.to === '/office' || item.to === '/cargo' || item.to === '/admin'}
                        className={({ isActive }) =>
                            `flex items-center gap-3 px-3 py-2.5 rounded-md text-[13px] font-medium transition-all duration-150 ${isActive
                                ? 'bg-brand-500/15 text-brand-400'
                                : 'text-text-secondary hover:text-text-primary hover:bg-surface-700/60'
                            }`
                        }
                    >
                        <item.icon className="w-[18px] h-[18px] shrink-0" />
                        <span>{item.label}</span>
                    </NavLink>
                ))}
            </nav>

            {/* Version */}
            <div className="px-5 py-3 border-t border-surface-600/50">
                <p className="text-[11px] text-text-muted">SOUIGAT v1.0 — Phase 2</p>
            </div>
        </aside>
    )
}
