import { useState, useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { getTrips } from '../../api/trips'
import { formatDateTime } from '../../utils/formatters'
import { DataTable } from '../../components/ui/DataTable'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { Link } from 'react-router-dom'
import { Plus, Filter } from 'lucide-react'
import type { Trip, TripStatus, TripFilters } from '../../types/trip'
import type { ColumnDef } from '@tanstack/react-table'

export function TripList() {
    const [page, setPage] = useState(1)
    const [filters, setFilters] = useState<TripFilters>({})
    const [showFilters, setShowFilters] = useState(false)

    const { data, isLoading } = useQuery({
        queryKey: ['trips', { page, ...filters }],
        queryFn: () => getTrips({ page, ...filters }),
        placeholderData: (prev) => prev,
    })

    const columns = useMemo<ColumnDef<Trip>[]>(
        () => [
            {
                accessorKey: 'id',
                header: 'N°',
                cell: (info) => (
                    <span className="font-mono text-slate-400 dark:text-slate-500">
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
                        <div className="flex items-center gap-2">
                            <span className="font-medium">{t.origin_office_name}</span>
                            <span className="text-slate-400 dark:text-slate-500">→</span>
                            <span className="font-medium">{t.destination_office_name}</span>
                        </div>
                    )
                },
            },
            {
                accessorKey: 'departure_datetime',
                header: 'Départ',
                cell: (info) => (
                    <span className="text-slate-600 dark:text-slate-400">
                        {formatDateTime(info.getValue<string>())}
                    </span>
                ),
            },
            {
                accessorKey: 'bus_plate',
                header: 'Bus',
                cell: (info) => (
                    <span className="px-2 py-0.5 bg-slate-200 dark:bg-slate-700/50 rounded text-slate-600 dark:text-slate-400 font-mono text-[11px]">
                        {info.getValue<string>()}
                    </span>
                ),
            },
            {
                accessorKey: 'status',
                header: 'Statut',
                cell: (info) => (
                    <StatusBadge status={info.getValue<TripStatus>()} type="trip" />
                ),
            },
            {
                accessorKey: 'passenger_count',
                header: 'Passagers',
                cell: (info) => (
                    <span className="text-slate-600 dark:text-slate-400">
                        {info.getValue<number>() || 0}
                    </span>
                ),
            },
            {
                accessorKey: 'cargo_count',
                header: 'Colis',
                cell: (info) => (
                    <span className="text-slate-600 dark:text-slate-400">
                        {info.getValue<number>() || 0}
                    </span>
                ),
            },
            {
                id: 'actions',
                header: '',
                cell: ({ row }) => (
                    <Link
                        to={`/office/trips/${row.original.id}`}
                        className="text-[#137fec] dark:text-[#60a5fa] hover:text-brand-300 font-medium text-[13px] transition-colors"
                        onClick={(e) => e.stopPropagation()}
                    >
                        Gérer →
                    </Link>
                ),
            },
        ],
        []
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
                    <h1 className="text-xl font-bold text-slate-900 dark:text-slate-100">Voyages</h1>
                    <p className="text-sm text-slate-400 dark:text-slate-500 mt-1">
                        Gérez tous les départs et arrivées.
                    </p>
                </div>
                <div className="flex items-center gap-3">
                    <button
                        onClick={() => setShowFilters(!showFilters)}
                        className={`px-4 py-2.5 rounded-md flex items-center gap-2 text-sm font-semibold transition-colors ${showFilters || Object.keys(filters).length > 0
                            ? 'bg-slate-200 dark:bg-slate-700 text-slate-900 dark:text-slate-100'
                            : 'bg-white dark:bg-[#1a2634] border border-slate-200 dark:border-slate-800 text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:text-slate-100'
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
                <div className="bg-white dark:bg-[#1a2634] border border-slate-200 dark:border-slate-800 rounded-lg p-5 animate-slide-in grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                    <div>
                        <label className="block text-[12px] font-medium text-slate-600 dark:text-slate-400 mb-1.5 uppercase tracking-wider">
                            Statut
                        </label>
                        <select
                            value={filters.status || ''}
                            onChange={(e) => handleFilterChange('status', e.target.value)}
                            className="w-full bg-slate-100 dark:bg-[#1e293b] border border-slate-200 dark:border-slate-800 rounded-md px-3 py-2 text-sm text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-brand-500"
                        >
                            <option value="">Tous les statuts</option>
                            <option value="scheduled">Programmé</option>
                            <option value="in_progress">En cours</option>
                            <option value="completed">Terminé</option>
                            <option value="cancelled">Annulé</option>
                        </select>
                    </div>
                    <div>
                        <label className="block text-[12px] font-medium text-slate-600 dark:text-slate-400 mb-1.5 uppercase tracking-wider">
                            Date (à partir de)
                        </label>
                        <input
                            type="date"
                            value={filters.date_from || ''}
                            onChange={(e) => handleFilterChange('date_from', e.target.value)}
                            className="w-full bg-slate-100 dark:bg-[#1e293b] border border-slate-200 dark:border-slate-800 rounded-md px-3 py-2 text-sm text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-brand-500 [color-scheme:dark]"
                        />
                    </div>
                    <div>
                        <label className="block text-[12px] font-medium text-slate-600 dark:text-slate-400 mb-1.5 uppercase tracking-wider">
                            Date (jusqu'à)
                        </label>
                        <input
                            type="date"
                            value={filters.date_to || ''}
                            onChange={(e) => handleFilterChange('date_to', e.target.value)}
                            className="w-full bg-slate-100 dark:bg-[#1e293b] border border-slate-200 dark:border-slate-800 rounded-md px-3 py-2 text-sm text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-brand-500 [color-scheme:dark]"
                        />
                    </div>
                    <div className="flex items-end">
                        <button
                            onClick={() => {
                                setFilters({})
                                setPage(1)
                            }}
                            disabled={Object.keys(filters).length === 0}
                            className="w-full px-4 py-2 text-sm text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:text-slate-100 bg-slate-100 dark:bg-[#1e293b] hover:bg-slate-200 dark:bg-slate-700 rounded-md transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                            Réinitialiser
                        </button>
                    </div>
                </div>
            )}

            {/* Table */}
            <DataTable
                columns={columns}
                data={data?.results || []}
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
