import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'
import type { Role } from '../../types/auth'

interface RoleGuardProps {
    allowedRoles: Role[]
    requireDepartment?: string
}

export function RoleGuard({ allowedRoles, requireDepartment }: RoleGuardProps) {
    const { user } = useAuth()

    if (!user) return <Navigate to="/login" replace />

    if (!allowedRoles.includes(user.role)) {
        return <Navigate to="/unauthorized" replace />
    }

    if (requireDepartment && user.role !== 'admin' && user.department !== requireDepartment && user.department !== 'all') {
        return <Navigate to="/unauthorized" replace />
    }

    return <Outlet />
}
