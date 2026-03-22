import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { createColumnHelper } from '@tanstack/react-table'
import { getPassengerTickets } from '../../api/tickets'
import { formatCurrency, formatDateTime } from '../../utils/formatters'
import { DataTable } from '../../components/ui/DataTable'
import { Ticket as TicketIcon } from 'lucide-react'

const columnHelper = createColumnHelper<any>()

const columns = [
    columnHelper.accessor('ticket_number', {
        header: 'N° Billet',
        cell: info => <span className="font-semibold text-slate-900 dark:text-slate-100">{info.getValue()}</span>,
    }),
    columnHelper.accessor('passenger_name', {
        header: 'Passager',
        cell: info => info.getValue(),
    }),
    columnHelper.accessor('trip', {
        header: 'Trajet',
        cell: info => {
            const tripId = info.getValue()
            return <span className="text-slate-600 dark:text-slate-400">Vol #{tripId}</span>
        },
    }),
    columnHelper.accessor('seat_number', {
        header: 'Siège',
        cell: info => info.getValue() || '-',
    }),
    columnHelper.accessor('price', {
        header: 'Prix',
        cell: info => formatCurrency(info.getValue()),
    }),
    columnHelper.accessor('payment_source', {
        header: 'Paiement',
        cell: info => {
            const val = info.getValue()
            return <span className="capitalize">{val === 'cash' ? 'Espèces' : val}</span>
        },
    }),
    columnHelper.accessor('created_at', {
        header: 'Créé le',
        cell: info => <span className="text-sm">{formatDateTime(info.getValue())}</span>,
    }),
    columnHelper.accessor('status', {
        header: 'Statut',
        cell: info => {
            const status = info.getValue()
            const isCancelled = status === 'cancelled'
            return (
                <span className={`px-2 py-0.5 rounded text-xs font-medium border ${isCancelled ? 'bg-red-50 dark:bg-red-900/20 text-red-600 dark:text-red-400 border-status-error/20' : 'bg-status-success/10 text-emerald-600 dark:text-emerald-400 border-status-success/20'}`}>
                    {status === 'active' ? 'Actif' : 'Annulé'}
                </span>
            )
        },
    }),
]

export function GlobalTicketList() {
    const [pagination, setPagination] = useState({ pageIndex: 0, pageSize: 20 })

    const { data: ticketsData, isLoading } = useQuery({
        queryKey: ['global_tickets', pagination.pageIndex],
        queryFn: () => getPassengerTickets({ page: pagination.pageIndex + 1, limit: pagination.pageSize }),
        staleTime: 30000,
    })

    const totalPages = ticketsData ? Math.ceil(ticketsData.count / pagination.pageSize) : 1

    return (
        <div className="space-y-6 animate-fade-in relative">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <div>
                    <h1 className="text-2xl font-bold text-slate-900 dark:text-slate-100 flex items-center gap-2">
                        <TicketIcon className="w-6 h-6 text-[#137fec] dark:text-[#60a5fa]" />
                        Tous les Billets Passagers
                    </h1>
                    <p className="text-sm text-slate-400 dark:text-slate-500 mt-1">Consultez et filtrez l'historique complet des tickets vendus.</p>
                </div>
            </div>

            <div className="bg-white dark:bg-[#1a2634] border border-slate-200 dark:border-slate-800 rounded-xl overflow-hidden min-h-[400px]">
                <DataTable
                    data={ticketsData?.results || []}
                    columns={columns}
                    isLoading={isLoading}
                    pageCount={totalPages}
                    pageIndex={pagination.pageIndex}
                    onPageChange={(index) => setPagination(p => ({ ...p, pageIndex: index }))}
                    emptyMessage="Aucun billet trouvé."
                />
            </div>
        </div>
    )
}
