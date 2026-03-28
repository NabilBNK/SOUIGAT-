import { useEffect, useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { getTrip, getTrips } from '../../api/trips'
import { formatDateTime } from '../../utils/formatters'
import { DataTable } from '../../components/ui/DataTable'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { useTripStatusMirrorMap } from '../../hooks/useTripMirrorData'
import { shouldPreferMirrorStatus } from '../../utils/tripStatusSource'
import { Link } from 'react-router-dom'
import { Plus, Filter, RefreshCw, TriangleAlert } from 'lucide-react'
import type { Trip, TripStatus, TripFilters } from '../../types/trip'
import type { ColumnDef } from '@tanstack/react-table'
import { queueTripUpsert } from '../../sync/tripSync'
import { getLatestSyncRecordForEntity, syncQueueEvents } from '../../sync/queue'
import type { SyncRecordStatus } from '../../sync/types'

type TripSyncState = {
    status: SyncRecordStatus
    lastError: string | null
}

export function TripList() {
    const [page, setPage] = useState(1)
    const [filters, setFilters] = useState<TripFilters>({})
    const [showFilters, setShowFilters] = useState(false)
    const [syncingTripId, setSyncingTripId] = useState<number | null>(null)
    const [syncNotice, setSyncNotice] = useState<string | null>(null)
    const [syncStateByTripId, setSyncStateByTripId] = useState<Record<number, TripSyncState>>({})

    const { data, isLoading } = useQuery({
        queryKey: ['trips', { page, ...filters }],
        queryFn: () => getTrips({ page, ...filters }),
        placeholderData: (prev) => prev,
    })

    const visibleTripIds = useMemo(
        () => (data?.results ?? []).map((trip) => trip.id),
        [data?.results],
    )
    const activeVisibleTripIds = useMemo(
        () => (data?.results ?? [])
            .filter((trip) => trip.status === 'scheduled' || trip.status === 'in_progress')
            .map((trip) => trip.id),
        [data?.results],
    )
    const { statuses: mirrorStatuses } = useTripStatusMirrorMap(activeVisibleTripIds)
    const mirrorStatusTripIds = useMemo(() => {
        const tripIds = new Set<number>()
        ;(data?.results ?? []).forEach((trip) => {
            const mirrored = mirrorStatuses[trip.id]
            if (shouldPreferMirrorStatus(trip, mirrored)) {
                tripIds.add(trip.id)
            }
        })
        return tripIds
    }, [data?.results, mirrorStatuses])

    const effectiveTrips = useMemo<Trip[]>(() => {
        return (data?.results ?? []).map((trip) => {
            const mirrored = mirrorStatuses[trip.id]
            if (!shouldPreferMirrorStatus(trip, mirrored)) {
                return trip
            }

            return {
                ...trip,
                status: mirrored.status ?? trip.status,
                arrival_datetime: mirrored.arrivalDatetime ?? trip.arrival_datetime,
            }
        })
    }, [data?.results, mirrorStatuses])

    useEffect(() => {
        let mounted = true

        const refreshSyncIssues = async () => {
            if (visibleTripIds.length === 0) {
                if (mounted) {
                    setSyncStateByTripId({})
                }
                return
            }

            const entries = await Promise.all(
                visibleTripIds.map(async (tripId) => {
                    const record = await getLatestSyncRecordForEntity('trip', String(tripId))
                    return [tripId, record] as const
                }),
            )

            if (!mounted) {
                return
            }

            const next: Record<number, TripSyncState> = {}
            entries.forEach(([tripId, record]) => {
                if (record) {
                    next[tripId] = {
                        status: record.status,
                        lastError: record.lastError,
                    }
                }
            })
            setSyncStateByTripId(next)
        }

        const onChanged = () => {
            void refreshSyncIssues()
        }

        void refreshSyncIssues()
        syncQueueEvents.addEventListener('changed', onChanged)

        return () => {
            mounted = false
            syncQueueEvents.removeEventListener('changed', onChanged)
        }
    }, [visibleTripIds])

    const handleManualSync = async (trip: Trip) => {
        setSyncNotice(null)
        setSyncingTripId(trip.id)
        try {
            const latestTrip = await getTrip(trip.id)
            await queueTripUpsert(latestTrip)
            setSyncNotice(`Sync lancé pour le voyage #${trip.id.toString().padStart(4, '0')} avec les données les plus récentes.`)
        } catch (error) {
            const message = error instanceof Error ? error.message : 'Erreur inconnue pendant la mise en file.'
            setSyncStateByTripId((prev) => ({
                ...prev,
                [trip.id]: {
                    status: 'failed',
                    lastError: message,
                },
            }))
            setSyncNotice(`Échec de sync pour le voyage #${trip.id.toString().padStart(4, '0')}: ${message}`)
        } finally {
            setSyncingTripId(null)
        }
    }

    const columns = useMemo<ColumnDef<Trip>[]>(
        () => [
            {
                accessorKey: 'id',
                header: 'N°',
                cell: (info) => (
                    <span className="font-mono text-text-muted">
                        #{info.getValue<number>().toString().padStart(4, '0')}
                    </span>
                ),
            },
            {
                id: 'route',
                header: 'Trajet',
                cell: ({ row }) => {
                    const t = row.original
                    return (
                        <Link
                            to={`/office/trips/${t.id}`}
                            className="inline-flex items-center gap-2 hover:opacity-90"
                            onClick={(e) => e.stopPropagation()}
                        >
                            <span className="font-medium text-brand-400 hover:text-brand-300">{t.origin_office_name}</span>
                            <span className="text-text-muted">→</span>
                            <span className="font-medium text-brand-400 hover:text-brand-300">{t.destination_office_name}</span>
                        </Link>
                    )
                },
            },
            {
                accessorKey: 'departure_datetime',
                header: 'Départ',
                cell: (info) => (
                    <span className="text-text-secondary">
                        {formatDateTime(info.getValue<string>())}
                    </span>
                ),
            },
            {
                accessorKey: 'bus_plate',
                header: 'Bus',
                cell: (info) => (
                    <span className="px-2 py-0.5 bg-slate-200 dark:bg-slate-700/50 rounded text-text-secondary font-mono text-[11px]">
                        {info.getValue<string>()}
                    </span>
                ),
            },
            {
                accessorKey: 'status',
                header: 'Statut',
                cell: (info) => {
                    const isMirrorStatus = mirrorStatusTripIds.has(info.row.original.id)
                    return (
                        <div className="flex items-center gap-2">
                            <StatusBadge status={info.getValue<TripStatus>()} type="trip" />
                            {isMirrorStatus && (
                                <span className="inline-flex items-center rounded border border-brand-500/30 bg-[#137fec]/10 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-brand-300">
                                    Firebase
                                </span>
                            )}
                        </div>
                    )
                },
            },
            {
                accessorKey: 'passenger_count',
                header: 'Passagers',
                cell: (info) => (
                    <span className="text-text-secondary">
                        {info.getValue<number>() || 0}
                    </span>
                ),
            },
            {
                accessorKey: 'cargo_count',
                header: 'Colis',
                cell: (info) => (
                    <span className="text-text-secondary">
                        {info.getValue<number>() || 0}
                    </span>
                ),
            },
            {
                id: 'actions',
                header: '',
                cell: ({ row }) => {
                    const isSyncing = syncingTripId === row.original.id
                    const syncState = syncStateByTripId[row.original.id]
                    const issue = syncState?.lastError
                    const showIssue = Boolean(
                        issue
                        && (syncState?.status === 'failed' || syncState?.status === 'conflict'),
                    )

                    return (
                        <div className="flex items-center gap-2">
                            <button
                                type="button"
                                onClick={(e) => {
                                    e.stopPropagation()
                                    void handleManualSync(row.original)
                                }}
                                disabled={isSyncing}
                                className="inline-flex items-center gap-1 text-[11px] text-text-secondary hover:text-text-primary disabled:opacity-60 disabled:cursor-not-allowed"
                                title={issue ?? 'Relancer la synchronisation Firebase'}
                            >
                                <RefreshCw className={`h-3.5 w-3.5 ${isSyncing ? 'animate-spin' : ''}`} />
                                Sync
                            </button>
                            {showIssue && (
                                <span className="inline-flex items-center text-[11px] text-red-500" title={issue ?? undefined}>
                                    <TriangleAlert className="h-3.5 w-3.5" />
                                </span>
                            )}
                        </div>
                    )
                },
            },
        ],
        [mirrorStatusTripIds, syncingTripId, syncStateByTripId]
    )

    const handleFilterChange = (key: keyof TripFilters, value: string | undefined) => {
        setFilters((prev) => ({ ...prev, [key]: value || undefined }))
        setPage(1)
    }

    const pageCount = data?.count ? Math.ceil(data.count / 10) : 1

    return (
        <div className="space-y-6 animate-fade-in">
            {/* Header */}
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <div>
                    <h1 className="text-xl font-bold text-text-primary">Voyages</h1>
                    <p className="text-sm text-text-muted mt-1">
                        Gérez tous les départs et arrivées.
                    </p>
                </div>
                <div className="flex items-center gap-3">
                    <button
                        onClick={() => setShowFilters(!showFilters)}
                        className={`px-4 py-2.5 rounded-md flex items-center gap-2 text-sm font-semibold transition-colors ${showFilters || Object.keys(filters).length > 0
                            ? 'bg-slate-200 dark:bg-slate-700 text-text-primary'
                            : 'bg-surface-800/80 backdrop-blur-md border border-surface-700 text-text-secondary hover:text-text-primary'
                            }`}
                    >
                        <Filter className="w-4 h-4" />
                        Filtres
                        {Object.keys(filters).length > 0 && (
                            <span className="w-2 h-2 rounded-full bg-[#137fec] absolute top-2 right-2"></span>
                        )}
                    </button>
                    <Link
                        to="/office/trips/new"
                        className="px-4 py-2.5 bg-[#137fec] hover:bg-[#0b5ed7] text-white rounded-md flex items-center gap-2 text-sm font-semibold transition-colors"
                    >
                        <Plus className="w-4 h-4" />
                        Nouveau voyage
                    </Link>
                </div>
            </div>

            {/* Filters Panel */}
            {showFilters && (
                <div className="bg-surface-800/80 backdrop-blur-md border border-surface-700 rounded-lg p-5 animate-slide-in grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                    <div>
                        <label className="block text-[12px] font-medium text-text-secondary mb-1.5 uppercase tracking-wider">
                            Statut
                        </label>
                        <select
                            value={filters.status || ''}
                            onChange={(e) => handleFilterChange('status', e.target.value)}
                            className="w-full bg-surface-900 border border-surface-700 rounded-md px-3 py-2 text-sm text-text-primary focus:outline-none focus:ring-1 focus:ring-brand-500"
                        >
                            <option value="">Tous les statuts</option>
                            <option value="scheduled">Programmé</option>
                            <option value="in_progress">En cours</option>
                            <option value="completed">Terminé</option>
                            <option value="cancelled">Annulé</option>
                        </select>
                    </div>
                    <div>
                        <label className="block text-[12px] font-medium text-text-secondary mb-1.5 uppercase tracking-wider">
                            Date (à partir de)
                        </label>
                        <input
                            type="date"
                            value={filters.date_from || ''}
                            onChange={(e) => handleFilterChange('date_from', e.target.value)}
                            className="w-full bg-surface-900 border border-surface-700 rounded-md px-3 py-2 text-sm text-text-primary focus:outline-none focus:ring-1 focus:ring-brand-500 [color-scheme:dark]"
                        />
                    </div>
                    <div>
                        <label className="block text-[12px] font-medium text-text-secondary mb-1.5 uppercase tracking-wider">
                            Date (jusqu'à)
                        </label>
                        <input
                            type="date"
                            value={filters.date_to || ''}
                            onChange={(e) => handleFilterChange('date_to', e.target.value)}
                            className="w-full bg-surface-900 border border-surface-700 rounded-md px-3 py-2 text-sm text-text-primary focus:outline-none focus:ring-1 focus:ring-brand-500 [color-scheme:dark]"
                        />
                    </div>
                    <div className="flex items-end">
                        <button
                            onClick={() => {
                                setFilters({})
                                setPage(1)
                            }}
                            disabled={Object.keys(filters).length === 0}
                            className="w-full px-4 py-2 text-sm text-text-secondary hover:text-text-primary bg-surface-900 hover:bg-slate-200 dark:bg-slate-700 rounded-md transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                            Réinitialiser
                        </button>
                    </div>
                </div>
            )}

            {/* Table */}
            {syncNotice && (
                <div className="rounded-md border border-surface-700 bg-surface-800/70 px-4 py-2 text-sm text-text-secondary">
                    {syncNotice}
                </div>
            )}

            <DataTable
                columns={columns}
                data={effectiveTrips}
                pageCount={pageCount}
                pageIndex={page - 1}
                onPageChange={(newIndex) => setPage(newIndex + 1)}
                isLoading={isLoading}
                onRowClick={() => {
                    // Navigate to details if needed, but we have the action button
                }}
                emptyMessage="Aucun voyage ne correspond à vos filtres."
            />
        </div>
    )
}
