import { useState } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getTrip, startTrip, completeTrip, cancelTrip, forceCompleteTrip, deleteTrip } from '../../api/trips'
import { useAuth } from '../../hooks/useAuth'
import { Button } from '../../components/ui/Button'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { formatDateTime, formatCurrency } from '../../utils/formatters'
import {
    AlertCircle,
    ArrowLeft,
    Calendar,
    Bus,
    MapPin,
    User,
    Users,
    Package,
    Banknote,
    Play,
    CheckCircle,
    XCircle,
    Info,
    Trash2
} from 'lucide-react'

import { PassengerTickets } from './PassengerTickets'
import { CargoTickets } from './CargoTickets'

type TabType = 'info' | 'passengers' | 'cargo' | 'expenses'

export function TripDetailPage() {
    const { id } = useParams<{ id: string }>()
    const navigate = useNavigate()
    const { user } = useAuth()
    const queryClient = useQueryClient()
    const [activeTab, setActiveTab] = useState<TabType>('info')
    const [actionError, setActionError] = useState<string | null>(null)

    const { data: trip, isLoading, error } = useQuery({
        queryKey: ['trip', id],
        queryFn: () => getTrip(Number(id)),
        enabled: !!id,
    })

    const invalidateTrip = () => {
        queryClient.invalidateQueries({ queryKey: ['trip', id] })
        queryClient.invalidateQueries({ queryKey: ['trips'] })
    }

    const extractErrorMsg = (err: unknown, defaultMsg: string) => {
        if (err && typeof err === 'object' && 'response' in err) {
            const response = (err as any).response
            if (response?.data?.error_code) {
                if (response.data.error_code === 'CONDUCTOR_BUSY') {
                    return "Le conducteur est déjà assigné à un autre voyage en cours. Un administrateur peut forcer la clôture de l'autre voyage."
                }
                if (response.data.error_code === 'TRIP_HAS_PENDING_SYNC') {
                    return "Impossible de forcer la clôture : des tickets ou dépenses n'ont pas encore été synchronisés par l'appareil du conducteur."
                }
                if (response.data.detail) return response.data.detail
            }
            if (response?.data?.detail) return response.data.detail
        }
        return defaultMsg
    }

    const startMutation = useMutation({
        mutationFn: () => startTrip(Number(id)),
        onSuccess: invalidateTrip,
        onError: (err) => setActionError(extractErrorMsg(err, "Erreur lors du démarrage."))
    })

    const completeMutation = useMutation({
        mutationFn: () => completeTrip(Number(id)),
        onSuccess: invalidateTrip,
        onError: (err) => setActionError(extractErrorMsg(err, "Erreur lors de la clôture."))
    })

    const cancelMutation = useMutation({
        mutationFn: () => cancelTrip(Number(id)),
        onSuccess: invalidateTrip,
        onError: (err) => setActionError(extractErrorMsg(err, "Erreur lors de l'annulation."))
    })

    const forceCompleteMutation = useMutation({
        mutationFn: (reason: string) => forceCompleteTrip(Number(id), reason),
        onSuccess: invalidateTrip,
        onError: (err) => setActionError(extractErrorMsg(err, "Erreur lors de la clôture forcée."))
    })

    const deleteMutation = useMutation({
        mutationFn: () => deleteTrip(Number(id)),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['trips'] })
            navigate('/office/trips')
        },
        onError: (err) => setActionError(extractErrorMsg(err, "Erreur lors de la suppression du voyage."))
    })

    if (isLoading) {
        return (
            <div className="flex justify-center items-center h-64">
                <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-brand-500"></div>
            </div>
        )
    }

    if (error || !trip) {
        return (
            <div className="bg-status-error/10 border border-status-error/30 text-status-error p-4 rounded-lg">
                Erreur de chargement du voyage.
            </div>
        )
    }

    const isOfficeStaff = user?.role === 'office_staff' || user?.role === 'admin'
    const isOriginOffice = user?.office === trip.origin_office || user?.role === 'admin'

    // Action Permissions
    const canStart = trip.status === 'scheduled' && isOfficeStaff && isOriginOffice
    const canCancel = trip.status === 'scheduled' && isOfficeStaff && isOriginOffice
    const canComplete = trip.status === 'in_progress' && isOfficeStaff && (user?.office === trip.destination_office || user?.role === 'admin')
    const canForceComplete = trip.status === 'in_progress' && user?.role === 'admin'
    const canDelete = user?.role === 'admin' && (trip.status === 'scheduled' || trip.status === 'cancelled')

    const tabs: { id: TabType; label: string; icon: React.ReactNode }[] = [
        { id: 'info', label: 'Informations', icon: <AlertCircle className="w-4 h-4" /> },
        { id: 'passengers', label: 'Passagers', icon: <Users className="w-4 h-4" /> },
        { id: 'cargo', label: 'Messagerie', icon: <Package className="w-4 h-4" /> },
        { id: 'expenses', label: 'Dépenses', icon: <Banknote className="w-4 h-4" /> },
    ]

    return (
        <div className="space-y-6 animate-fade-in">
            {/* Header section */}
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <div className="flex items-center gap-4">
                    <Link
                        to="/office/trips"
                        className="p-2 -ml-2 text-text-muted hover:text-text-primary hover:bg-surface-600/50 rounded-lg transition-colors"
                    >
                        <ArrowLeft className="w-5 h-5" />
                    </Link>
                    <div>
                        <div className="flex items-center gap-3">
                            <h1 className="text-2xl font-bold text-text-primary">
                                Voyage #{trip.id}
                            </h1>
                            <StatusBadge status={trip.status} type="trip" />
                        </div>
                        <p className="text-sm text-text-muted mt-1 flex items-center gap-2">
                            <MapPin className="w-3.5 h-3.5" />
                            {trip.origin_office_name} &rarr; {trip.destination_office_name}
                        </p>
                    </div>
                </div>

                <div className="flex items-center gap-2">
                    {canCancel && (
                        <Button
                            variant="danger"
                            size="sm"
                            icon={<XCircle className="w-4 h-4" />}
                            isLoading={cancelMutation.isPending}
                            onClick={() => {
                                if (window.confirm('Voulez-vous vraiment annuler ce voyage ?')) {
                                    cancelMutation.mutate()
                                }
                            }}
                        >
                            Annuler
                        </Button>
                    )}
                    {canStart && (
                        <Button
                            variant="primary"
                            icon={<Play className="w-4 h-4" />}
                            isLoading={startMutation.isPending}
                            onClick={() => startMutation.mutate()}
                        >
                            Démarrer
                        </Button>
                    )}
                    {canComplete && (
                        <Button
                            variant="primary"
                            icon={<CheckCircle className="w-4 h-4" />}
                            isLoading={completeMutation.isPending}
                            onClick={() => completeMutation.mutate()}
                        >
                            Clôturer
                        </Button>
                    )}
                    {canForceComplete && (
                        <Button
                            variant="danger"
                            size="sm"
                            icon={<AlertCircle className="w-4 h-4" />}
                            isLoading={forceCompleteMutation.isPending}
                            onClick={() => {
                                const reason = window.prompt("Raison de la clôture forcée ?")
                                if (reason) {
                                    forceCompleteMutation.mutate(reason)
                                }
                            }}
                        >
                            Forcer Clôture
                        </Button>
                    )}
                    {canDelete && (
                        <Button
                            variant="danger"
                            size="sm"
                            icon={<Trash2 className="w-4 h-4" />}
                            isLoading={deleteMutation.isPending}
                            onClick={() => {
                                if (window.confirm('Supprimer definitivement ce voyage ? Cette action retire le voyage de la base.')) {
                                    deleteMutation.mutate()
                                }
                            }}
                        >
                            Supprimer
                        </Button>
                    )}
                </div>
            </div>

            {actionError && (
                <div className="bg-status-error/10 border border-status-error/20 rounded-lg p-4 flex items-start gap-3">
                    <AlertCircle className="w-5 h-5 text-status-error shrink-0 mt-0.5" />
                    <p className="text-sm text-status-error">{actionError}</p>
                </div>
            )}

            {trip.status === 'in_progress' && (
                <div className="bg-brand-500/10 border border-brand-500/20 rounded-lg p-4 flex items-start gap-3">
                    <Info className="w-5 h-5 text-brand-400 shrink-0 mt-0.5" />
                    <div>
                        <p className="text-sm font-medium text-brand-400">Voyage en cours</p>
                        <p className="text-sm text-brand-400/80">La vente de billets et de colis est clôturée pour ce voyage. Les données sont en lecture seule.</p>
                    </div>
                </div>
            )}

            {trip.status === 'completed' && (
                <div className="bg-surface-700/50 border border-surface-600/50 rounded-lg p-4 flex items-start gap-3 text-text-muted">
                    <CheckCircle className="w-5 h-5 shrink-0 mt-0.5" />
                    <div>
                        <p className="text-sm font-medium text-text-primary">Voyage terminé</p>
                        <p className="text-sm">Ce voyage est archivé. Toutes les informations sont en lecture seule.</p>
                    </div>
                </div>
            )}

            {trip.status === 'cancelled' && (
                <div className="bg-status-error/10 border border-status-error/20 rounded-lg p-4 flex items-start gap-3">
                    <XCircle className="w-5 h-5 text-status-error shrink-0 mt-0.5" />
                    <div>
                        <p className="text-sm font-medium text-status-error">Voyage annulé</p>
                        <p className="text-sm text-status-error/80">Aucune modification n'est permise sur un voyage annulé.</p>
                    </div>
                </div>
            )}

            {/* Navigation Tabs */}
            <div className="border-b border-surface-600/50">
                <nav className="-mb-px flex gap-6">
                    {tabs.map(tab => (
                        <button
                            key={tab.id}
                            onClick={() => setActiveTab(tab.id)}
                            className={`
                                flex items-center gap-2 py-3 px-1 border-b-2 font-medium text-sm transition-colors
                                ${activeTab === tab.id
                                    ? 'border-brand-500 text-brand-400'
                                    : 'border-transparent text-text-muted hover:text-text-primary hover:border-surface-600'}
                            `}
                        >
                            {tab.icon}
                            {tab.label}
                        </button>
                    ))}
                </nav>
            </div>

            {/* Tab content wrapper */}
            <div className="py-4">
                {activeTab === 'info' && (
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                        {/* Info details card */}
                        <div className="bg-surface-800 border border-surface-600/50 rounded-xl p-6 space-y-6">
                            <h3 className="text-lg font-semibold text-text-primary border-b border-surface-600/30 pb-2">
                                Résumé
                            </h3>

                            <div className="space-y-4">
                                <div className="flex items-start gap-3">
                                    <Info className="w-5 h-5 text-brand-400 mt-0.5" />
                                    <div>
                                        <p className="text-sm font-medium text-text-primary">Reference voyage</p>
                                        <p className="text-sm text-text-secondary">TRIP-{trip.id}</p>
                                        <p className="text-xs text-text-muted">Cree le {formatDateTime(trip.created_at)}</p>
                                    </div>
                                </div>

                                <div className="flex items-start gap-3">
                                    <MapPin className="w-5 h-5 text-brand-400 mt-0.5" />
                                    <div>
                                        <p className="text-sm font-medium text-text-primary">Itineraire</p>
                                        <p className="text-sm text-text-secondary">
                                            {trip.origin_office_name} (#{trip.origin_office}) &rarr; {trip.destination_office_name} (#{trip.destination_office})
                                        </p>
                                    </div>
                                </div>

                                <div className="flex items-start gap-3">
                                    <Calendar className="w-5 h-5 text-brand-400 mt-0.5" />
                                    <div>
                                        <p className="text-sm font-medium text-text-primary">Date de depart</p>
                                        <p className="text-sm text-text-secondary">{formatDateTime(trip.departure_datetime)}</p>
                                    </div>
                                </div>

                                {trip.arrival_datetime && (
                                    <div className="flex items-start gap-3">
                                        <CheckCircle className="w-5 h-5 text-status-success mt-0.5" />
                                        <div>
                                            <p className="text-sm font-medium text-text-primary">Date d'arrivee</p>
                                            <p className="text-sm text-text-secondary">{formatDateTime(trip.arrival_datetime)}</p>
                                        </div>
                                    </div>
                                )}

                                <div className="flex items-start gap-3">
                                    <Bus className="w-5 h-5 text-brand-400 mt-0.5" />
                                    <div>
                                        <p className="text-sm font-medium text-text-primary">Bus attribue</p>
                                        <p className="text-sm text-text-secondary">{trip.bus_plate} (ID bus #{trip.bus})</p>
                                    </div>
                                </div>

                                <div className="flex items-start gap-3">
                                    <User className="w-5 h-5 text-brand-400 mt-0.5" />
                                    <div>
                                        <p className="text-sm font-medium text-text-primary">Conducteur</p>
                                        <p className="text-sm text-text-secondary">{trip.conductor_name} (ID #{trip.conductor})</p>
                                        <p className="text-xs text-text-muted">Derniere mise a jour: {formatDateTime(trip.updated_at)}</p>
                                    </div>
                                </div>
                            </div>
                        </div>

                        {/* Financial snapshot card */}
                        <div className="bg-surface-800 border border-surface-600/50 rounded-xl p-6 space-y-6">
                            <h3 className="text-lg font-semibold text-text-primary border-b border-surface-600/30 pb-2">
                                Tarification appliquée
                            </h3>

                            <p className="text-sm text-text-muted mb-4">
                                Ces prix ont été figés lors de la création du voyage et ne changeront pas.
                            </p>

                            <div className="space-y-3">
                                <div className="flex justify-between items-center py-2 border-b border-surface-600/20">
                                    <span className="text-sm text-text-secondary">Billet Passager</span>
                                    <span className="text-sm font-semibold text-text-primary">{formatCurrency(trip.passenger_base_price)}</span>
                                </div>
                                <div className="flex justify-between items-center py-2 border-b border-surface-600/20">
                                    <span className="text-sm text-text-secondary">Colis Petit</span>
                                    <span className="text-sm font-semibold text-text-primary">{formatCurrency(trip.cargo_small_price)}</span>
                                </div>
                                <div className="flex justify-between items-center py-2 border-b border-surface-600/20">
                                    <span className="text-sm text-text-secondary">Colis Moyen</span>
                                    <span className="text-sm font-semibold text-text-primary">{formatCurrency(trip.cargo_medium_price)}</span>
                                </div>
                                <div className="flex justify-between items-center py-2 border-b border-surface-600/20">
                                    <span className="text-sm text-text-secondary">Colis Grand</span>
                                    <span className="text-sm font-semibold text-text-primary">{formatCurrency(trip.cargo_large_price)}</span>
                                </div>
                            </div>
                        </div>
                    </div>
                )}

                {activeTab === 'passengers' && (
                    <PassengerTickets trip={trip} />
                )}

                {activeTab === 'cargo' && (
                    <CargoTickets trip={trip} />
                )}

                {activeTab === 'expenses' && (
                    <div className="bg-surface-800 border border-surface-600/50 rounded-xl p-6 text-center text-text-muted">
                        Gestion des dépenses en cours de développement
                    </div>
                )}
            </div>
        </div>
    )
}
