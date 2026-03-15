import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import {
    AlertCircle,
    ArrowRight,
    Bus,
    Download,
    MapPin,
    Package,
    Plus,
    Receipt,
    TrendingDown,
    TrendingUp,
    Wallet,
} from 'lucide-react'
import { getDailyReport, getExportDownloadUrl, getExportStatus, triggerExport } from '../../api/reports'
import { getPendingSettlements } from '../../api/settlements'
import { getTrips } from '../../api/trips'
import { Button } from '../../components/ui/Button'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { useAuth } from '../../hooks/useAuth'
import type { SettlementListItem } from '../../types/settlement'
import type { Trip } from '../../types/trip'
import { formatCurrency, formatDateTime } from '../../utils/formatters'

type MetricTone = 'success' | 'info' | 'warning' | 'danger'

export function OfficeDashboard() {
    const { user } = useAuth()
    const today = new Date().toISOString().split('T')[0]
    const [exportTaskId, setExportTaskId] = useState<string | null>(null)
    const [exportDownloadToken, setExportDownloadToken] = useState<string | null>(null)
    const [exportError, setExportError] = useState<string | null>(null)
    const [exportStartedAt, setExportStartedAt] = useState<number | null>(null)

    const { data: dailyReports, isError: isReportError } = useQuery({
        queryKey: ['dashboard', 'daily-report', today],
        queryFn: () => getDailyReport({ date_from: today, date_to: today }),
    })

    const { data: tripsData, isLoading: isTripsLoading, isError: isTripsError } = useQuery({
        queryKey: ['dashboard', 'recent-trips'],
        queryFn: () => getTrips({ page: 1 }),
        placeholderData: (previous) => previous,
    })

    const { data: activeTripsData } = useQuery({
        queryKey: ['dashboard', 'active-trips'],
        queryFn: () => getTrips({ status: 'in_progress', page: 1 }),
        placeholderData: (previous) => previous,
    })

    const { data: pendingSettlementsData, isLoading: isSettlementsLoading, isError: isSettlementsError } = useQuery({
        queryKey: ['dashboard', 'pending-settlements'],
        queryFn: () => getPendingSettlements({ page_size: 5 }),
        placeholderData: (previous) => previous,
    })

    const exportMutation = useMutation({
        mutationFn: () => triggerExport({
            report_type: 'daily',
            filters: {
                date_from: today,
                date_to: today,
            },
        }),
        onSuccess: (data) => {
            setExportTaskId(data.task_id)
            setExportDownloadToken(data.download_token)
            setExportError(null)
            setExportStartedAt(Date.now())
        },
        onError: () => {
            setExportError('Impossible de lancer le rapport du jour.')
        },
    })

    const { data: exportStatus } = useQuery({
        queryKey: ['dashboard', 'daily-export', exportTaskId],
        queryFn: () => getExportStatus(exportTaskId!),
        enabled: !!exportTaskId,
        refetchInterval: (query) => {
            const status = query.state.data?.status
            if (status === 'success' || status === 'failure') return false
            return 2000
        },
    })

    useEffect(() => {
        if (exportStatus?.status === 'success' && exportStatus.task_id && exportDownloadToken) {
            const timer = setTimeout(() => {
                window.location.assign(getExportDownloadUrl(exportStatus.task_id, exportDownloadToken))
                setExportTaskId(null)
                setExportDownloadToken(null)
                setExportStartedAt(null)
            }, 400)

            return () => clearTimeout(timer)
        }

        if (exportStatus?.status === 'failure') {
            setExportError(exportStatus.error || 'L export du rapport a echoue.')
            setExportTaskId(null)
            setExportDownloadToken(null)
            setExportStartedAt(null)
        }
    }, [exportDownloadToken, exportStatus])

    useEffect(() => {
        if (!exportStartedAt || !exportTaskId) return undefined

        const timeoutId = window.setInterval(() => {
            if (Date.now() - exportStartedAt >= 60000) {
                setExportError('Le rapport prend trop de temps. Reessayez dans quelques instants.')
                setExportTaskId(null)
                setExportDownloadToken(null)
                setExportStartedAt(null)
            }
        }, 5000)

        return () => window.clearInterval(timeoutId)
    }, [exportStartedAt, exportTaskId])

    const dailyTotals = useMemo(() => {
        return (dailyReports || []).reduce(
            (totals, report) => ({
                totalTrips: totals.totalTrips + report.total_trips,
                passengerRevenue: totals.passengerRevenue + report.passenger_revenue,
                cargoRevenue: totals.cargoRevenue + report.cargo_revenue,
                expenseTotal: totals.expenseTotal + report.expense_total,
                netRevenue: totals.netRevenue + report.net_revenue,
            }),
            {
                totalTrips: 0,
                passengerRevenue: 0,
                cargoRevenue: 0,
                expenseTotal: 0,
                netRevenue: 0,
            }
        )
    }, [dailyReports])

    const activeTrips = activeTripsData?.results || []
    const recentTrips = (tripsData?.results || []).slice(0, 6)
    const pendingSettlements = pendingSettlementsData?.results || []

    const pendingSettlementMap = useMemo(() => {
        return new Map<number, SettlementListItem>(
            pendingSettlements.map((settlement) => [settlement.trip, settlement])
        )
    }, [pendingSettlements])

    const settlementListPath = user?.role === 'admin' ? '/admin/settlements' : '/office/trips'
    const isExporting = !!exportTaskId && exportStatus?.status !== 'success' && exportStatus?.status !== 'failure'
    const officeLabel = user?.office_name || 'operations'
    const firstName = user?.first_name || 'Equipe'

    const metricCards = [
        {
            label: 'Total revenue',
            value: formatCurrency(dailyTotals.passengerRevenue + dailyTotals.cargoRevenue),
            meta: `${dailyTotals.totalTrips} trip(s) today`,
            icon: <Wallet className="h-5 w-5" />,
            tone: 'success' as const,
            to: '/office/reports',
        },
        {
            label: 'Active trips',
            value: activeTrips.length.toString(),
            meta: activeTrips.length === 1 ? '1 trip in progress' : `${activeTrips.length} trips in progress`,
            icon: <Bus className="h-5 w-5" />,
            tone: 'info' as const,
            to: '/office/trips',
        },
        {
            label: 'Pending settlements',
            value: pendingSettlements.length.toString(),
            meta: getPendingSettlementMeta(pendingSettlements.length),
            icon: <Receipt className="h-5 w-5" />,
            tone: getPendingSettlementTone(pendingSettlements.length),
            to: settlementListPath,
        },
        {
            label: 'Total expenses',
            value: formatCurrency(dailyTotals.expenseTotal),
            meta: `${formatCurrency(dailyTotals.netRevenue)} net expected`,
            icon: <TrendingDown className="h-5 w-5" />,
            tone: 'danger' as const,
            to: '/office/reports',
        },
    ]

    return (
        <div className="space-y-6 animate-fade-in">
            <section className="rounded-3xl border border-surface-600/50 bg-gradient-to-br from-surface-800 via-surface-800 to-surface-700 p-6 shadow-lg shadow-surface-900/20">
                <div className="flex flex-col gap-5 lg:flex-row lg:items-center lg:justify-between">
                    <div className="space-y-3">
                        <div className="inline-flex items-center gap-2 rounded-full border border-brand-500/20 bg-brand-500/10 px-3 py-1 text-xs font-semibold uppercase tracking-[0.2em] text-brand-300">
                            <MapPin className="h-3.5 w-3.5" />
                            {officeLabel}
                        </div>
                        <div>
                            <h1 className="text-3xl font-semibold tracking-tight text-text-primary">
                                Welcome back, {firstName}
                            </h1>
                            <p className="mt-2 max-w-2xl text-sm text-text-secondary">
                                Here is today&apos;s office view for trips, cash handovers, and financial follow-up.
                            </p>
                        </div>
                    </div>

                    <div className="flex flex-col gap-3 sm:flex-row">
                        <Button
                            variant="secondary"
                            icon={<Download className="h-4 w-4" />}
                            isLoading={exportMutation.isPending || isExporting}
                            disabled={isExporting}
                            onClick={() => exportMutation.mutate()}
                        >
                            Daily report
                        </Button>
                        <Link
                            to="/office/trips/new"
                            className="inline-flex items-center justify-center gap-2 rounded-lg bg-brand-500 px-4 py-2.5 text-sm font-semibold text-white shadow-sm shadow-brand-500/30 transition-colors hover:bg-brand-600"
                        >
                            <Plus className="h-4 w-4" />
                            Create new trip
                        </Link>
                    </div>
                </div>

                {(isExporting || exportError || isReportError || isTripsError || isSettlementsError) && (
                    <div className="mt-5 space-y-3">
                        {isExporting && (
                            <div className="flex items-center gap-3 rounded-2xl border border-brand-500/20 bg-brand-500/10 px-4 py-3 text-sm text-brand-200">
                                <Download className="h-4 w-4" />
                                Preparing the Excel report
                                {typeof exportStatus?.progress === 'number' ? ` (${exportStatus.progress}%)` : ''}
                            </div>
                        )}
                        {exportError && (
                            <InlineAlert message={exportError} />
                        )}
                        {(isReportError || isTripsError || isSettlementsError) && (
                            <InlineAlert message="Some dashboard sections could not be loaded. You can still use the available actions." />
                        )}
                    </div>
                )}
            </section>

            <section className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
                {metricCards.map((card) => (
                    <MetricLinkCard
                        key={card.label}
                        label={card.label}
                        value={card.value}
                        meta={card.meta}
                        to={card.to}
                        icon={card.icon}
                        tone={card.tone}
                    />
                ))}
            </section>

            <section className="grid grid-cols-1 gap-6 xl:grid-cols-[minmax(0,2fr)_minmax(320px,1fr)]">
                <div className="overflow-hidden rounded-3xl border border-surface-600/50 bg-surface-800 shadow-lg shadow-surface-900/10">
                    <div className="flex flex-col gap-3 border-b border-surface-600/50 px-6 py-5 sm:flex-row sm:items-center sm:justify-between">
                        <div>
                            <h2 className="text-lg font-semibold text-text-primary">Recent activity</h2>
                            <p className="mt-1 text-sm text-text-muted">
                                Latest trips and the actions your office can take right now.
                            </p>
                        </div>
                        <Link
                            to="/office/trips"
                            className="inline-flex items-center gap-2 text-sm font-semibold text-brand-400 transition-colors hover:text-brand-300"
                        >
                            View all trips
                            <ArrowRight className="h-4 w-4" />
                        </Link>
                    </div>

                    <div className="overflow-x-auto">
                        <table className="min-w-full divide-y divide-surface-600/50">
                            <thead className="bg-surface-700/40">
                                <tr className="text-left text-[11px] font-semibold uppercase tracking-[0.18em] text-text-muted">
                                    <th className="px-6 py-4">Trip</th>
                                    <th className="px-6 py-4">Route</th>
                                    <th className="px-6 py-4">Conductor</th>
                                    <th className="px-6 py-4">Finance</th>
                                    <th className="px-6 py-4">Status</th>
                                    <th className="px-6 py-4 text-right">Action</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-surface-600/40">
                                {isTripsLoading ? (
                                    Array.from({ length: 4 }).map((_, index) => (
                                        <tr key={index}>
                                            <td className="px-6 py-5" colSpan={6}>
                                                <div className="h-12 animate-pulse rounded-2xl bg-surface-700/60" />
                                            </td>
                                        </tr>
                                    ))
                                ) : recentTrips.length > 0 ? (
                                    recentTrips.map((trip) => {
                                        const pendingSettlement = pendingSettlementMap.get(trip.id)
                                        return (
                                            <RecentTripRow
                                                key={trip.id}
                                                trip={trip}
                                                pendingSettlement={pendingSettlement}
                                            />
                                        )
                                    })
                                ) : (
                                    <tr>
                                        <td className="px-6 py-12 text-center text-sm text-text-muted" colSpan={6}>
                                            No trips found for the current dashboard view.
                                        </td>
                                    </tr>
                                )}
                            </tbody>
                        </table>
                    </div>
                </div>

                <div className="space-y-6">
                    <div className="rounded-3xl border border-surface-600/50 bg-surface-800 p-6 shadow-lg shadow-surface-900/10">
                        <div className="flex items-start justify-between gap-4">
                            <div>
                                <h2 className="text-lg font-semibold text-text-primary">Pending settlements</h2>
                                <p className="mt-1 text-sm text-text-muted">
                                    Trips that still need a cash handover review.
                                </p>
                            </div>
                            <div className={getPendingBadgeClass(pendingSettlements.length)}>
                                {pendingSettlements.length}
                            </div>
                        </div>

                        <div className="mt-5 space-y-3">
                            {isSettlementsLoading ? (
                                Array.from({ length: 3 }).map((_, index) => (
                                    <div key={index} className="h-20 animate-pulse rounded-2xl bg-surface-700/60" />
                                ))
                            ) : pendingSettlements.length > 0 ? (
                                pendingSettlements.map((settlement) => (
                                    <Link
                                        key={settlement.id}
                                        to={`/office/trips/${settlement.trip}`}
                                        className="block rounded-2xl border border-surface-600/50 bg-surface-700/40 p-4 transition-colors hover:border-brand-500/40 hover:bg-surface-700"
                                    >
                                        <div className="flex items-start justify-between gap-3">
                                            <div>
                                                <div className="text-sm font-semibold text-text-primary">
                                                    Trip #{settlement.trip.toString().padStart(4, '0')}
                                                </div>
                                                <div className="mt-1 text-sm text-text-secondary">
                                                    {settlement.origin_name} to {settlement.destination_name}
                                                </div>
                                            </div>
                                            <StatusBadge status={settlement.status} type="settlement" />
                                        </div>
                                        <div className="mt-4 flex items-center justify-between text-sm">
                                            <div className="text-text-muted">{settlement.conductor_name}</div>
                                            <div className="font-semibold text-status-warning">
                                                {formatCurrency(settlement.net_cash_expected)}
                                            </div>
                                        </div>
                                    </Link>
                                ))
                            ) : (
                                <div className="rounded-2xl border border-dashed border-status-success/30 bg-status-success/5 p-4 text-sm text-status-success">
                                    All settlements are up to date for now.
                                </div>
                            )}
                        </div>

                        <Link
                            to={settlementListPath}
                            className="mt-5 inline-flex items-center gap-2 text-sm font-semibold text-brand-400 transition-colors hover:text-brand-300"
                        >
                            Review settlement work
                            <ArrowRight className="h-4 w-4" />
                        </Link>
                    </div>

                    <div className="rounded-3xl border border-surface-600/50 bg-surface-800 p-6 shadow-lg shadow-surface-900/10">
                        <h2 className="text-lg font-semibold text-text-primary">Today at a glance</h2>
                        <div className="mt-5 space-y-4">
                            <SummaryLine
                                icon={<TrendingUp className="h-4 w-4" />}
                                label="Passenger revenue"
                                value={formatCurrency(dailyTotals.passengerRevenue)}
                            />
                            <SummaryLine
                                icon={<Package className="h-4 w-4" />}
                                label="Cargo revenue"
                                value={formatCurrency(dailyTotals.cargoRevenue)}
                            />
                            <SummaryLine
                                icon={<Receipt className="h-4 w-4" />}
                                label="Trips completed or created today"
                                value={dailyTotals.totalTrips.toString()}
                            />
                            <SummaryLine
                                icon={<Bus className="h-4 w-4" />}
                                label="Trips currently running"
                                value={activeTrips.length.toString()}
                            />
                        </div>
                    </div>
                </div>
            </section>
        </div>
    )
}

function MetricLinkCard({
    label,
    value,
    meta,
    to,
    icon,
    tone,
}: {
    label: string
    value: string
    meta: string
    to: string
    icon: ReactNode
    tone: MetricTone
}) {
    const toneClasses: Record<MetricTone, string> = {
        success: 'bg-status-success/10 text-status-success',
        info: 'bg-brand-500/10 text-brand-300',
        warning: 'bg-status-warning/10 text-status-warning',
        danger: 'bg-status-error/10 text-status-error',
    }

    return (
        <Link
            to={to}
            className="group rounded-3xl border border-surface-600/50 bg-surface-800 p-5 shadow-lg shadow-surface-900/10 transition-all hover:-translate-y-0.5 hover:border-brand-500/30 hover:bg-surface-700/80"
        >
            <div className="flex items-start justify-between gap-4">
                <div>
                    <p className="text-sm font-medium text-text-muted">{label}</p>
                    <p className="mt-2 text-3xl font-semibold tracking-tight text-text-primary">{value}</p>
                    <p className="mt-2 text-sm text-text-secondary">{meta}</p>
                </div>
                <div className={`rounded-2xl p-3 ${toneClasses[tone]}`}>
                    {icon}
                </div>
            </div>
        </Link>
    )
}

function RecentTripRow({
    trip,
    pendingSettlement,
}: {
    trip: Trip
    pendingSettlement?: SettlementListItem
}) {
    const financeText = pendingSettlement
        ? formatCurrency(pendingSettlement.net_cash_expected)
        : trip.expense_total
            ? `${formatCurrency(trip.expense_total)} expenses`
            : '--'
    const actionLabel = pendingSettlement
        ? 'Open settlement'
        : trip.status === 'in_progress'
            ? 'Track trip'
            : trip.status === 'scheduled'
                ? 'Review trip'
                : 'View trip'

    return (
        <tr className="transition-colors hover:bg-surface-700/30">
            <td className="px-6 py-5">
                <div className="font-mono text-sm font-semibold text-text-primary">
                    #{trip.id.toString().padStart(4, '0')}
                </div>
                <div className="mt-1 text-xs text-text-muted">
                    {formatDateTime(trip.departure_datetime)}
                </div>
            </td>
            <td className="px-6 py-5">
                <div className="text-sm font-medium text-text-primary">
                    {trip.origin_office_name} to {trip.destination_office_name}
                </div>
                <div className="mt-1 text-xs text-text-muted">
                    Bus {trip.bus_plate}
                </div>
            </td>
            <td className="px-6 py-5">
                <div className="text-sm font-medium text-text-primary">{trip.conductor_name}</div>
                <div className="mt-1 text-xs text-text-muted">
                    {(trip.passenger_count || 0)} passenger(s), {(trip.cargo_count || 0)} cargo
                </div>
            </td>
            <td className="px-6 py-5">
                <div className={`text-sm font-semibold ${pendingSettlement ? 'text-status-warning' : 'text-text-secondary'}`}>
                    {financeText}
                </div>
            </td>
            <td className="px-6 py-5">
                <div className="flex flex-wrap items-center gap-2">
                    <StatusBadge status={trip.status} type="trip" />
                    {pendingSettlement && (
                        <StatusBadge status={pendingSettlement.status} type="settlement" />
                    )}
                </div>
            </td>
            <td className="px-6 py-5 text-right">
                <Link
                    to={`/office/trips/${trip.id}`}
                    className="inline-flex items-center gap-2 text-sm font-semibold text-brand-400 transition-colors hover:text-brand-300"
                >
                    {actionLabel}
                    <ArrowRight className="h-4 w-4" />
                </Link>
            </td>
        </tr>
    )
}

function SummaryLine({
    icon,
    label,
    value,
}: {
    icon: ReactNode
    label: string
    value: string
}) {
    return (
        <div className="flex items-center justify-between rounded-2xl border border-surface-600/40 bg-surface-700/30 px-4 py-3">
            <div className="flex items-center gap-3">
                <div className="rounded-xl bg-brand-500/10 p-2 text-brand-300">
                    {icon}
                </div>
                <span className="text-sm text-text-secondary">{label}</span>
            </div>
            <span className="text-sm font-semibold text-text-primary">{value}</span>
        </div>
    )
}

function InlineAlert({ message }: { message: string }) {
    return (
        <div className="flex items-start gap-3 rounded-2xl border border-status-error/20 bg-status-error/10 px-4 py-3 text-sm text-status-error">
            <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
            <span>{message}</span>
        </div>
    )
}

function getPendingSettlementTone(count: number): MetricTone {
    if (count >= 3) return 'danger'
    if (count >= 1) return 'warning'
    return 'success'
}

function getPendingSettlementMeta(count: number): string {
    if (count === 0) return 'No handovers waiting'
    if (count <= 2) return 'Review needed today'
    return 'High priority follow-up'
}

function getPendingBadgeClass(count: number): string {
    if (count === 0) {
        return 'inline-flex h-10 min-w-10 items-center justify-center rounded-2xl bg-status-success/10 px-3 text-sm font-semibold text-status-success'
    }

    if (count <= 2) {
        return 'inline-flex h-10 min-w-10 items-center justify-center rounded-2xl bg-status-warning/10 px-3 text-sm font-semibold text-status-warning'
    }

    return 'inline-flex h-10 min-w-10 items-center justify-center rounded-2xl bg-status-error/10 px-3 text-sm font-semibold text-status-error'
}
