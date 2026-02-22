import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { getCargoTickets } from '../../api/cargo'
import { formatCurrency, formatDateTime } from '../../utils/formatters'
import { DataTable } from '../../components/ui/DataTable'
import { createColumnHelper } from '@tanstack/react-table'
import { Package, Search, AlertCircle, TrendingUp, Navigation } from 'lucide-react'
import { CARGO_STATUS_LABELS } from '../../types/ticket'
import type { CargoTicket } from '../../types/ticket'
import { useNavigate } from 'react-router-dom'

const columnHelper = createColumnHelper<CargoTicket>()

const columns = [
    columnHelper.accessor('ticket_number', {
        header: 'Réf.',
        cell: info => <span className="font-mono text-xs">{info.getValue()}</span>,
    }),
    columnHelper.accessor('sender_name', {
        header: 'Expéditeur',
        cell: info => info.getValue(),
    }),
    columnHelper.accessor('receiver_name', {
        header: 'Destinataire',
        cell: info => info.getValue(),
    }),
    columnHelper.accessor('cargo_tier', {
        header: 'Type',
        cell: info => <span className="capitalize">{info.getValue()}</span>,
    }),
    columnHelper.accessor('price', {
        header: 'Prix',
        cell: info => formatCurrency(info.getValue()),
    }),
    columnHelper.accessor('payment_source', {
        header: 'Paiement',
        cell: info => (
            <span className={`text-[10px] uppercase font-bold px-1.5 py-0.5 rounded ${info.getValue() === 'prepaid' ? 'bg-status-success/10 text-status-success' : 'bg-status-warning/10 text-status-warning'
                }`}>
                {info.getValue() === 'prepaid' ? 'Payé' : 'À payer'}
            </span>
        ),
    }),
    columnHelper.accessor('status', {
        header: 'Statut',
        cell: info => {
            const status = info.getValue()
            return (
                <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium border ${['delivered', 'paid'].includes(status) ? 'bg-status-success/10 text-status-success border-status-success/20' :
                    ['created', 'in_transit', 'arrived'].includes(status) ? 'bg-brand-500/10 text-brand-400 border-brand-500/20' :
                        'bg-status-error/10 text-status-error border-status-error/20'
                    }`}>
                    {CARGO_STATUS_LABELS[status] || status}
                </span>
            )
        },
    }),
    columnHelper.accessor('created_at', {
        header: 'Date d\'envoi',
        cell: info => {
            const val = info.getValue()
            return val ? formatDateTime(val) : '-'
        },
    }),
]

export function CargoDashboard() {
    const navigate = useNavigate()
    const [page, setPage] = useState(1)
    const [search, setSearch] = useState('')
    const [statusFilter, setStatusFilter] = useState('')

    // Quick search trigger that ignores pagination initially
    const handleSearch = (e: React.ChangeEvent<HTMLInputElement>) => {
        setSearch(e.target.value)
        setPage(1)
    }

    const { data, isLoading, error } = useQuery({
        queryKey: ['cargo', page, search, statusFilter],
        queryFn: () => {
            const params: Record<string, string | number> = { page }
            if (search) params.search = search
            if (statusFilter) params.status = statusFilter
            return getCargoTickets(params)
        },
    })

    const StatCard = ({ title, value, icon, trend }: { title: string, value: string | number, icon: React.ReactNode, trend?: string }) => (
        <div className="bg-surface-800 border border-surface-600/50 rounded-xl p-5 hover:border-brand-500/30 transition-colors">
            <div className="flex items-center justify-between mb-4">
                <div className="p-2 bg-surface-700/50 rounded-lg text-brand-400">
                    {icon}
                </div>
                {trend && <span className="text-xs font-medium text-status-success">{trend}</span>}
            </div>
            <div>
                <p className="text-sm text-text-muted mb-1">{title}</p>
                <h3 className="text-2xl font-bold text-text-primary">{value}</h3>
            </div>
        </div>
    )

    return (
        <div className="space-y-6 animate-fade-in">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <div>
                    <h1 className="text-2xl font-bold text-text-primary">Département Colis</h1>
                    <p className="text-sm text-text-muted mt-1">Gérez la messagerie, les expéditions et les réceptions.</p>
                </div>
            </div>

            {/* Simulated Metrics (In a real app, these would come from a specific /reports/cargo endpoint) */}
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
                <StatCard title="Colis Aujourd'hui" value={data?.count || 0} icon={<Package className="w-5 h-5" />} trend="+12%" />
                <StatCard title="En transit" value="..." icon={<Navigation className="w-5 h-5" />} />
                <StatCard title="A réceptionner" value="..." icon={<AlertCircle className="w-5 h-5" />} />
                <StatCard title="Revenus" value="..." icon={<TrendingUp className="w-5 h-5" />} />
            </div>

            {/* Filters */}
            <div className="bg-surface-800 border border-surface-600/50 p-4 rounded-xl flex flex-col md:flex-row gap-4">
                <div className="flex-1 relative">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-text-muted" />
                    <input
                        type="text"
                        placeholder="Rechercher (Nom, Réf, Téléphone)..."
                        value={search}
                        onChange={handleSearch}
                        className="w-full bg-surface-700 border border-surface-600/50 rounded-lg pl-10 pr-3 py-2 text-sm text-text-primary focus:outline-none focus:ring-2 focus:ring-brand-500/50"
                    />
                </div>
                <div className="w-full md:w-64">
                    <select
                        value={statusFilter}
                        onChange={(e) => {
                            setStatusFilter(e.target.value)
                            setPage(1)
                        }}
                        className="w-full bg-surface-700 border border-surface-600/50 rounded-lg px-3 py-2 text-sm text-text-primary focus:outline-none focus:ring-2 focus:ring-brand-500/50 appearance-none"
                    >
                        <option value="">Tous les statuts</option>
                        {Object.entries(CARGO_STATUS_LABELS).map(([key, label]) => (
                            <option key={key} value={key}>{label}</option>
                        ))}
                    </select>
                </div>
            </div>

            {/* Data Table */}
            <div className="bg-surface-800 border border-surface-600/50 rounded-xl overflow-hidden min-h-[400px]">
                {error ? (
                    <div className="p-8 text-center text-status-error">
                        <AlertCircle className="w-8 h-8 mx-auto mb-2 opacity-80" />
                        <p>Erreur lors du chargement des colis.</p>
                    </div>
                ) : (
                    <DataTable
                        data={data?.results || []}
                        columns={columns}
                        isLoading={isLoading}
                        pageCount={data ? Math.ceil(data.count / 10) : 1}
                        pageIndex={page - 1}
                        onPageChange={(newIndex) => setPage(newIndex + 1)}
                        onRowClick={(ticket) => navigate(`/cargo/tickets/${ticket.id}`)}
                        emptyMessage="Aucun colis trouvé avec ces critères."
                    />
                )}
            </div>
        </div>
    )
}
