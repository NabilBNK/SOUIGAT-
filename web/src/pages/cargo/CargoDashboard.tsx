import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { createCargoTicket, getCargoTickets } from '../../api/cargo'
import { getTripsForCargoCreation } from '../../api/trips'
import { formatCurrency, formatDateTime } from '../../utils/formatters'
import { DataTable } from '../../components/ui/DataTable'
import { createColumnHelper } from '@tanstack/react-table'
import { Package, Search, AlertCircle, TrendingUp, Navigation, Plus } from 'lucide-react'
import { CARGO_STATUS_LABELS } from '../../types/ticket'
import type { CargoTicket, CargoStatus } from '../../types/ticket'
import { useNavigate } from 'react-router-dom'
import type { ColumnDef } from '@tanstack/react-table'
import { Button } from '../../components/ui/Button'
import { Modal } from '../../components/ui/Modal'
import { useAuth } from '../../hooks/useAuth'

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
            <span className={`text-[10px] uppercase font-bold px-1.5 py-0.5 rounded ${info.getValue() === 'prepaid' ? 'bg-status-success/10 text-emerald-400' : 'bg-status-warning/10 text-yellow-600 dark:text-yellow-400'
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
                <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium border ${['delivered', 'paid'].includes(status) ? 'bg-status-success/10 text-emerald-400 border-status-success/20' :
                    ['created', 'in_transit', 'arrived'].includes(status) ? 'bg-[#137fec]/10 text-brand-400 border-brand-500/20' :
                        'bg-red-500/10 bg-red-500/10 text-red-600 dark:text-red-400 border-status-error/20'
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
    const queryClient = useQueryClient()
    const { user } = useAuth()
    const [page, setPage] = useState(1)
    const [search, setSearch] = useState('')
    const [statusFilter, setStatusFilter] = useState('')
    const [isCreateOpen, setIsCreateOpen] = useState(false)
    const [createError, setCreateError] = useState<string | null>(null)
    const [createForm, setCreateForm] = useState({
        tripId: '',
        senderName: '',
        senderPhone: '',
        receiverName: '',
        receiverPhone: '',
        cargoTier: 'small',
        paymentSource: 'prepaid',
        description: '',
    })

    const canCreateCargo = user?.role === 'admin' || user?.role === 'office_staff'

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

    const {
        data: tripsForCreate,
        isLoading: isTripsLoading,
    } = useQuery({
        queryKey: ['cargo-create-trips'],
        queryFn: getTripsForCargoCreation,
        enabled: isCreateOpen && canCreateCargo,
        staleTime: 60_000,
    })

    useEffect(() => {
        if (!isCreateOpen || !tripsForCreate?.length) {
            return
        }
        if (!createForm.tripId) {
            setCreateForm((prev) => ({
                ...prev,
                tripId: String(tripsForCreate[0].id),
            }))
        }
    }, [isCreateOpen, tripsForCreate, createForm.tripId])

    const createMutation = useMutation({
        mutationFn: async () => {
            const tripId = Number(createForm.tripId)
            return createCargoTicket(tripId, {
                sender_name: createForm.senderName.trim(),
                sender_phone: createForm.senderPhone.trim(),
                receiver_name: createForm.receiverName.trim(),
                receiver_phone: createForm.receiverPhone.trim(),
                cargo_tier: createForm.cargoTier,
                payment_source: createForm.paymentSource,
                description: createForm.description.trim(),
            })
        },
        onSuccess: () => {
            setIsCreateOpen(false)
            setCreateError(null)
            setCreateForm({
                tripId: '',
                senderName: '',
                senderPhone: '',
                receiverName: '',
                receiverPhone: '',
                cargoTier: 'small',
                paymentSource: 'prepaid',
                description: '',
            })
            queryClient.invalidateQueries({ queryKey: ['cargo'] })
        },
        onError: (err: any) => {
            const detail = err?.response?.data?.detail
            const payloadErrors = err?.response?.data
            if (typeof detail === 'string') {
                setCreateError(detail)
                return
            }
            if (payloadErrors && typeof payloadErrors === 'object') {
                const first = Object.values(payloadErrors)[0]
                if (typeof first === 'string') {
                    setCreateError(first)
                    return
                }
                if (Array.isArray(first) && typeof first[0] === 'string') {
                    setCreateError(first[0])
                    return
                }
            }
            setCreateError('Erreur lors de la création du colis.')
        },
    })

    const submitCreateCargo = (e: React.FormEvent<HTMLFormElement>) => {
        e.preventDefault()
        setCreateError(null)

        if (!createForm.tripId) {
            setCreateError('Veuillez sélectionner un trajet.')
            return
        }
        if (!createForm.senderName.trim() || !createForm.receiverName.trim()) {
            setCreateError('Les noms expéditeur et destinataire sont requis.')
            return
        }

        createMutation.mutate()
    }

    const StatCard = ({ title, value, icon, trend }: { title: string, value: string | number, icon: React.ReactNode, trend?: string }) => (
        <div className="bg-surface-800/80 backdrop-blur-md border border-surface-700 rounded-xl p-5 hover:border-brand-500/30 transition-colors">
            <div className="flex items-center justify-between mb-4">
                <div className="p-2 bg-surface-700/50 rounded-lg text-brand-400">
                    {icon}
                </div>
                {trend && <span className="text-xs font-medium text-emerald-400">{trend}</span>}
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
                {canCreateCargo && (
                    <Button
                        icon={<Plus className="w-4 h-4" />}
                        onClick={() => {
                            setCreateError(null)
                            setIsCreateOpen(true)
                        }}
                    >
                        Nouveau colis
                    </Button>
                )}
            </div>

            <Modal
                isOpen={isCreateOpen}
                onClose={() => {
                    if (!createMutation.isPending) {
                        setIsCreateOpen(false)
                    }
                }}
                title="Créer un colis"
                size="md"
            >
                <form className="space-y-4" onSubmit={submitCreateCargo}>
                    <div>
                        <label className="block text-xs text-text-muted mb-1">Trajet</label>
                        <select
                            value={createForm.tripId}
                            onChange={(e) => setCreateForm((prev) => ({ ...prev, tripId: e.target.value }))}
                            className="w-full bg-surface-900 border border-surface-700 rounded-lg px-3 py-2 text-sm text-text-primary focus:outline-none focus:ring-2 focus:ring-brand-500"
                            disabled={isTripsLoading || createMutation.isPending}
                        >
                            <option value="">Sélectionner un trajet</option>
                            {(tripsForCreate ?? []).map((trip) => (
                                <option key={trip.id} value={trip.id}>
                                    #{trip.id} - {trip.origin_office_name} → {trip.destination_office_name} ({trip.status})
                                </option>
                            ))}
                        </select>
                        {!isTripsLoading && (tripsForCreate?.length ?? 0) === 0 && (
                            <p className="text-xs text-yellow-400 mt-1">Aucun trajet planifié/en cours disponible pour votre scope.</p>
                        )}
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div>
                            <label className="block text-xs text-text-muted mb-1">Expéditeur</label>
                            <input
                                value={createForm.senderName}
                                onChange={(e) => setCreateForm((prev) => ({ ...prev, senderName: e.target.value }))}
                                className="w-full bg-surface-900 border border-surface-700 rounded-lg px-3 py-2 text-sm text-text-primary focus:outline-none focus:ring-2 focus:ring-brand-500"
                                placeholder="Nom expéditeur"
                                disabled={createMutation.isPending}
                            />
                        </div>
                        <div>
                            <label className="block text-xs text-text-muted mb-1">Téléphone expéditeur</label>
                            <input
                                value={createForm.senderPhone}
                                onChange={(e) => setCreateForm((prev) => ({ ...prev, senderPhone: e.target.value }))}
                                className="w-full bg-surface-900 border border-surface-700 rounded-lg px-3 py-2 text-sm text-text-primary focus:outline-none focus:ring-2 focus:ring-brand-500"
                                placeholder="Optionnel"
                                disabled={createMutation.isPending}
                            />
                        </div>
                        <div>
                            <label className="block text-xs text-text-muted mb-1">Destinataire</label>
                            <input
                                value={createForm.receiverName}
                                onChange={(e) => setCreateForm((prev) => ({ ...prev, receiverName: e.target.value }))}
                                className="w-full bg-surface-900 border border-surface-700 rounded-lg px-3 py-2 text-sm text-text-primary focus:outline-none focus:ring-2 focus:ring-brand-500"
                                placeholder="Nom destinataire"
                                disabled={createMutation.isPending}
                            />
                        </div>
                        <div>
                            <label className="block text-xs text-text-muted mb-1">Téléphone destinataire</label>
                            <input
                                value={createForm.receiverPhone}
                                onChange={(e) => setCreateForm((prev) => ({ ...prev, receiverPhone: e.target.value }))}
                                className="w-full bg-surface-900 border border-surface-700 rounded-lg px-3 py-2 text-sm text-text-primary focus:outline-none focus:ring-2 focus:ring-brand-500"
                                placeholder="Optionnel"
                                disabled={createMutation.isPending}
                            />
                        </div>
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div>
                            <label className="block text-xs text-text-muted mb-1">Type colis</label>
                            <select
                                value={createForm.cargoTier}
                                onChange={(e) => setCreateForm((prev) => ({ ...prev, cargoTier: e.target.value }))}
                                className="w-full bg-surface-900 border border-surface-700 rounded-lg px-3 py-2 text-sm text-text-primary focus:outline-none focus:ring-2 focus:ring-brand-500"
                                disabled={createMutation.isPending}
                            >
                                <option value="small">Small</option>
                                <option value="medium">Medium</option>
                                <option value="large">Large</option>
                            </select>
                        </div>
                        <div>
                            <label className="block text-xs text-text-muted mb-1">Paiement</label>
                            <select
                                value={createForm.paymentSource}
                                onChange={(e) => setCreateForm((prev) => ({ ...prev, paymentSource: e.target.value }))}
                                className="w-full bg-surface-900 border border-surface-700 rounded-lg px-3 py-2 text-sm text-text-primary focus:outline-none focus:ring-2 focus:ring-brand-500"
                                disabled={createMutation.isPending}
                            >
                                <option value="prepaid">Prépayé</option>
                                <option value="pay_on_delivery">À payer à la livraison</option>
                            </select>
                        </div>
                    </div>

                    <div>
                        <label className="block text-xs text-text-muted mb-1">Description</label>
                        <textarea
                            value={createForm.description}
                            onChange={(e) => setCreateForm((prev) => ({ ...prev, description: e.target.value }))}
                            className="w-full bg-surface-900 border border-surface-700 rounded-lg px-3 py-2 text-sm text-text-primary focus:outline-none focus:ring-2 focus:ring-brand-500 min-h-[88px]"
                            placeholder="Optionnel"
                            disabled={createMutation.isPending}
                        />
                    </div>

                    {createError && (
                        <div className="text-sm text-red-400 bg-red-500/10 border border-red-500/20 rounded-lg px-3 py-2">
                            {createError}
                        </div>
                    )}

                    <div className="flex justify-end gap-2 pt-2">
                        <Button
                            type="button"
                            variant="ghost"
                            onClick={() => setIsCreateOpen(false)}
                            disabled={createMutation.isPending}
                        >
                            Annuler
                        </Button>
                        <Button
                            type="submit"
                            isLoading={createMutation.isPending}
                            disabled={isTripsLoading || (tripsForCreate?.length ?? 0) === 0}
                        >
                            Créer le colis
                        </Button>
                    </div>
                </form>
            </Modal>

            {/* Simulated Metrics (In a real app, these would come from a specific /reports/cargo endpoint) */}
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
                <StatCard title="Colis Aujourd'hui" value={data?.count || 0} icon={<Package className="w-5 h-5" />} trend="+12%" />
                <StatCard title="En transit" value="..." icon={<Navigation className="w-5 h-5" />} />
                <StatCard title="A réceptionner" value="..." icon={<AlertCircle className="w-5 h-5" />} />
                <StatCard title="Revenus" value="..." icon={<TrendingUp className="w-5 h-5" />} />
            </div>

            {/* Filters */}
            <div className="bg-surface-800/80 backdrop-blur-md border border-surface-700 p-4 rounded-xl flex flex-col md:flex-row gap-4">
                <div className="flex-1 relative">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-text-muted" />
                    <input
                        type="text"
                        placeholder="Rechercher (Nom, Réf, Téléphone)..."
                        value={search}
                        onChange={handleSearch}
                        className="w-full bg-surface-900 border border-surface-700 rounded-lg pl-10 pr-3 py-2 text-sm text-text-primary focus:outline-none focus:ring-2 focus:ring-brand-500"
                    />
                </div>
                <div className="w-full md:w-64">
                    <select
                        value={statusFilter}
                        onChange={(e) => {
                            setStatusFilter(e.target.value)
                            setPage(1)
                        }}
                        className="w-full bg-surface-900 border border-surface-700 rounded-lg px-3 py-2 text-sm text-text-primary focus:outline-none focus:ring-2 focus:ring-brand-500 appearance-none"
                    >
                        <option value="">Tous les statuts</option>
                        {Object.entries(CARGO_STATUS_LABELS).map(([key, label]) => (
                            <option key={key} value={key}>{label}</option>
                        ))}
                    </select>
                </div>
            </div>

            {/* Data Table */}
            <div className="bg-surface-800/80 backdrop-blur-md border border-surface-700 rounded-xl overflow-hidden min-h-[400px]">
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
