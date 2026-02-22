import { useAuth } from '../../hooks/useAuth'
import { useQuery } from '@tanstack/react-query'
import { getTrips } from '../../api/trips'
import { MetricCard } from '../../components/ui/Card'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { PageLoader } from '../../components/ui/LoadingSpinner'
import { formatCurrency, formatDateTime } from '../../utils/formatters'
import { Bus, Ticket, Package, TrendingUp } from 'lucide-react'
import { Link } from 'react-router-dom'
import type { Trip, TripStatus } from '../../types/trip'

export function OfficeDashboard() {
    const { user } = useAuth()
    const { data, isLoading } = useQuery({
        queryKey: ['trips', { page: 1 }],
        queryFn: () => getTrips({ page: 1 }),
    })

    if (isLoading) return <PageLoader />

    const trips: Trip[] = data?.results || []
    const activeTrips = trips.filter((t: Trip) => t.status === 'in_progress').length
    const scheduledTrips = trips.filter((t: Trip) => t.status === 'scheduled').length
    const totalPassengers = trips.reduce((sum: number, t: Trip) => sum + (t.passenger_count || 0), 0)
    const totalRevenue = trips
        .filter((t: Trip) => t.status === 'completed')
        .reduce((sum: number, t: Trip) => sum + (t.passenger_count || 0) * t.passenger_base_price, 0)

    return (
        <div className="space-y-6 animate-fade-in">
            <div>
                <h1 className="text-xl font-bold text-text-primary">
                    Bonjour, {user?.first_name} 👋
                </h1>
                <p className="text-sm text-text-muted mt-1">Vue d'ensemble de vos opérations</p>
            </div>

            {/* Metrics */}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                <MetricCard
                    label="Voyages actifs"
                    value={activeTrips}
                    icon={<Bus className="w-5 h-5" />}
                />
                <MetricCard
                    label="Voyages programmés"
                    value={scheduledTrips}
                    icon={<Bus className="w-5 h-5" />}
                />
                <MetricCard
                    label="Passagers (page)"
                    value={totalPassengers}
                    icon={<Ticket className="w-5 h-5" />}
                />
                <MetricCard
                    label="Revenus"
                    value={formatCurrency(totalRevenue)}
                    icon={<TrendingUp className="w-5 h-5" />}
                />
            </div>

            {/* Recent trips table */}
            <div className="bg-surface-800 border border-surface-600/50 rounded-lg overflow-hidden">
                <div className="px-5 py-4 border-b border-surface-600/50 flex items-center justify-between">
                    <h2 className="text-sm font-semibold text-text-primary">Derniers voyages</h2>
                    <Link
                        to="/office/trips"
                        className="text-[12px] text-brand-400 hover:text-brand-300 font-medium transition-colors"
                    >
                        Voir tout →
                    </Link>
                </div>
                <div className="overflow-x-auto">
                    <table className="w-full">
                        <thead>
                            <tr className="border-b border-surface-600/30">
                                <th className="text-left px-5 py-3 text-[11px] font-semibold text-text-muted uppercase tracking-wider">Trajet</th>
                                <th className="text-left px-5 py-3 text-[11px] font-semibold text-text-muted uppercase tracking-wider">Bus</th>
                                <th className="text-left px-5 py-3 text-[11px] font-semibold text-text-muted uppercase tracking-wider">Départ</th>
                                <th className="text-left px-5 py-3 text-[11px] font-semibold text-text-muted uppercase tracking-wider">Statut</th>
                                <th className="text-right px-5 py-3 text-[11px] font-semibold text-text-muted uppercase tracking-wider">Prix base</th>
                            </tr>
                        </thead>
                        <tbody>
                            {trips.slice(0, 8).map((trip: Trip) => (
                                <tr
                                    key={trip.id}
                                    className="border-b border-surface-600/20 hover:bg-surface-700/40 transition-colors cursor-pointer"
                                >
                                    <td className="px-5 py-3">
                                        <Link to={`/office/trips/${trip.id}`} className="block">
                                            <span className="text-sm font-medium text-text-primary">
                                                {trip.origin_office_name}
                                            </span>
                                            <span className="text-text-muted mx-1.5">→</span>
                                            <span className="text-sm font-medium text-text-primary">
                                                {trip.destination_office_name}
                                            </span>
                                        </Link>
                                    </td>
                                    <td className="px-5 py-3">
                                        <span className="text-sm text-text-secondary">{trip.bus_plate}</span>
                                    </td>
                                    <td className="px-5 py-3">
                                        <span className="text-sm text-text-secondary">{formatDateTime(trip.departure_datetime)}</span>
                                    </td>
                                    <td className="px-5 py-3">
                                        <StatusBadge status={trip.status as TripStatus} type="trip" />
                                    </td>
                                    <td className="px-5 py-3 text-right">
                                        <span className="text-sm font-medium text-text-primary">
                                            {formatCurrency(trip.passenger_base_price)}
                                        </span>
                                    </td>
                                </tr>
                            ))}
                            {trips.length === 0 && (
                                <tr>
                                    <td colSpan={5} className="px-5 py-8 text-center text-sm text-text-muted">
                                        <Package className="w-8 h-8 mx-auto mb-2 opacity-30" />
                                        Aucun voyage trouvé
                                    </td>
                                </tr>
                            )}
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    )
}
