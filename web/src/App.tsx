import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useEffect } from 'react'
import { AuthProvider } from './context/AuthContext'
import { useAuth } from './hooks/useAuth'
import { ProtectedRoute } from './components/guards/ProtectedRoute'
import { RoleGuard } from './components/guards/RoleGuard'
import { AppShell } from './components/Layout/AppShell'
import { Login } from './pages/Login'
import { NotFound, Unauthorized } from './pages/NotFound'
import { OfficeDashboard } from './pages/office/Dashboard'
import { TripList } from './pages/office/TripList'
import { TripCreatePage as TripCreate } from './pages/office/TripCreate'
import { TripDetailPage as TripDetail } from './pages/office/TripDetail'
import { GlobalTicketList } from './pages/office/GlobalTicketList'
import { ReportsPage as Reports } from './pages/office/Reports'
import { CargoDashboard } from './pages/cargo/CargoDashboard'
import { CargoDetail } from './pages/cargo/CargoDetail'
import { AdminDashboard } from './pages/admin/AdminDashboard'
import { UserManagement } from './pages/admin/UserManagement'
import { BusManagement } from './pages/admin/BusManagement'
import { OfficeManagement } from './pages/admin/OfficeManagement'
import { PricingManagement } from './pages/admin/PricingManagement'
import { AuditLog } from './pages/admin/AuditLog'
import { QuarantineReview } from './pages/admin/QuarantineReview'
import { SettlementsPage } from './pages/admin/Settlements'
import { startSyncEngine, stopSyncEngine } from './sync/engine'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: (failureCount, error: any) => {
        if (error?.response?.status === 401) return false;
        return failureCount < 1;
      },
      refetchOnWindowFocus: false,
    },
  },
})

function RootRedirect() {
  const { user } = useAuth()
  if (!user) return <Navigate to="/login" replace />

  switch (user.role) {
    case 'admin':
      return <Navigate to="/admin" replace />
    case 'office_staff':
      if (user.department === 'cargo') return <Navigate to="/cargo" replace />
      return <Navigate to="/office" replace />
    case 'conductor':
    case 'driver':
      return <Navigate to="/unauthorized" replace />
    default:
      return <Navigate to="/login" replace />
  }
}

export default function App() {
  useEffect(() => {
    startSyncEngine()
    return () => {
      stopSyncEngine()
    }
  }, [])

  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          <Routes>
            {/* Public */}
            <Route path="/login" element={<Login />} />
            <Route path="/unauthorized" element={<Unauthorized />} />

            {/* Protected routes */}
            <Route element={<ProtectedRoute />}>
              <Route path="/" element={<RootRedirect />} />

              <Route element={<AppShell />}>
                {/* Office routes (Non-cargo staff only) */}
                <Route element={<RoleGuard allowedRoles={['admin', 'office_staff']} requireDepartment="all" />}>
                  <Route path="/office" element={<OfficeDashboard />} />
                  <Route path="/office/trips" element={<TripList />} />
                  <Route path="/office/trips/new" element={<TripCreate />} />
                  <Route path="/office/trips/:id" element={<TripDetail />} />
                  <Route path="/office/tickets" element={<GlobalTicketList />} />
                  <Route path="/office/reports" element={<Reports />} />
                </Route>

                {/* Cargo routes */}
                <Route element={<RoleGuard allowedRoles={['admin', 'office_staff']} requireDepartment="cargo" />}>
                  <Route path="/cargo" element={<CargoDashboard />} />
                  <Route path="/cargo/tickets/:id" element={<CargoDetail />} />
                </Route>

                {/* Admin routes */}
                <Route element={<RoleGuard allowedRoles={['admin']} />}>
                  <Route path="/admin" element={<AdminDashboard />} />
                  <Route path="/admin/users" element={<UserManagement />} />
                  <Route path="/admin/buses" element={<BusManagement />} />
                  <Route path="/admin/offices" element={<OfficeManagement />} />
                  <Route path="/admin/pricing" element={<PricingManagement />} />
                  <Route path="/admin/settlements" element={<SettlementsPage />} />
                  <Route path="/admin/audit" element={<AuditLog />} />
                </Route>

                {/* Quarantine: admin + office_staff */}
                <Route element={<RoleGuard allowedRoles={['admin', 'office_staff']} />}>
                  <Route path="/admin/quarantine" element={<QuarantineReview />} />
                </Route>
              </Route>
            </Route>

            {/* 404 */}
            <Route path="*" element={<NotFound />} />
          </Routes>
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
