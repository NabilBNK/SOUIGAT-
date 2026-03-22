import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { getCargoTickets } from '../../api/cargo'
import { formatCurrency, formatDateTime } from '../../utils/formatters'
import { DataTable } from '../../components/ui/DataTable'
import { createColumnHelper } from '@tanstack/react-table'
import { Package, Search, AlertCircle, TrendingUp, Navigation } from 'lucide-react'
import { CARGO_STATUS_LABELS } from '../../types/ticket'
import type { CargoTicket, CargoStatus } from '../../types/ticket'
import { useNavigate } from 'react-router-dom'
import type { ColumnDef } from '@tanstack/react-table'

const columnHelper = createColumnHelper<CargoTicket>()

const columns: ColumnDef<CargoTicket, any>[] = [
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
            <span className={`text-[10px] uppercase font-bold px-1.5 py-0.5 rounded ${info.getValue() === 'prepaid' ? 'bg-status-success/10 text-emerald-600 dark:text-emerald-400' : 'bg-status-warning/10 text-yellow-600 dark:text-yellow-400'
                }`}>
                {info.getValue() === 'prepaid' ? 'Payé' : 'À payer'}
            </span>
        ),
    }),
    columnHelper.accessor('status', {
        header: 'Statut',
        cell: info => {
            const status = info.getValue() as CargoStatus
            return (
                <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium border ${['delivered', 'paid'].includes(status) ? 'bg-status-success/10 text-emerald-600 dark:text-emerald-400 border-status-success/20' :
                    ['created', 'in_transit', 'arrived'].includes(status) ? 'bg-[#137fec]/10 text-[#137fec] dark:text-[#60a5fa] border-brand-500/20' :
                        'bg-red-50 dark:bg-red-900/20 text-red-600 dark:text-red-400 border-status-error/20'
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
        <div className="bg-white dark:bg-[#1a2634] border border-slate-200 dark:border-slate-800 rounded-xl p-5 hover:border-brand-500/30 transition-colors">
            <div className="flex items-center justify-between mb-4">
                <div className="p-2 bg-slate-100 dark:bg-[#1e293b]/50 rounded-lg text-[#137fec] dark:text-[#60a5fa]">
                    {icon}
                </div>
                {trend && <span className="text-xs font-medium text-emerald-600 dark:text-emerald-400">{trend}</span>}
            </div>
            <div>
                <p className="text-sm text-slate-400 dark:text-slate-500 mb-1">{title}</p>
                <h3 className="text-2xl font-bold text-slate-900 dark:text-slate-100">{value}</h3>
            </div>
        </div>
    )

    return (
        <div className="space-y-6 animate-fade-in">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <div>
                    <h1 className="text-2xl font-bold text-slate-900 dark:text-slate-100">Département Colis</h1>
                    <p className="text-sm text-slate-400 dark:text-slate-500 mt-1">Gérez la messagerie, les expéditions et les réceptions.</p>
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
            <div className="bg-white dark:bg-[#1a2634] border border-slate-200 dark:border-slate-800 p-4 rounded-xl flex flex-col md:flex-row gap-4">
                <div className="flex-1 relative">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 dark:text-slate-500" />
                    <input
                        type="text"
                        placeholder="Rechercher (Nom, Réf, Téléphone)..."
                        value={search}
                        onChange={handleSearch}
                        className="w-full bg-slate-100 dark:bg-[#1e293b] border border-slate-200 dark:border-slate-800 rounded-lg pl-10 pr-3 py-2 text-sm text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-brand-500/50"
                    />
                </div>
                <div className="w-full md:w-64">
                    <select
                        value={statusFilter}
                        onChange={(e) => {
                            setStatusFilter(e.target.value)
                            setPage(1)
                        }}
                        className="w-full bg-slate-100 dark:bg-[#1e293b] border border-slate-200 dark:border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-brand-500/50 appearance-none"
                    >
                        <option value="">Tous les statuts</option>
                        {Object.entries(CARGO_STATUS_LABELS).map(([key, label]) => (
                            <option key={key} value={key}>{label}</option>
                        ))}
                    </select>
                </div>
            </div>

            {/* Data Table */}
            <div className="bg-white dark:bg-[#1a2634] border border-slate-200 dark:border-slate-800 rounded-xl overflow-hidden min-h-[400px]">
                {error ? (
                    <div className="p-8 text-center text-red-600 dark:text-red-400">
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
