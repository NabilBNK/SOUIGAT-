import { useState } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
    deleteCargoTicket,
    deliverCargoTicket,
    getCargoTicket,
    transitionCargoStatus,
} from '../../api/cargo'
import { formatCurrency, formatDateTime } from '../../utils/formatters'
import { Button } from '../../components/ui/Button'
import { CARGO_STATUS_LABELS, VALID_TRANSITIONS } from '../../types/ticket'
import type { CargoStatus } from '../../types/ticket'
import { useAuth } from '../../hooks/useAuth'
import { AlertCircle, ArrowLeft, CheckCircle, Clock, MapPin, Package, Trash2, User } from 'lucide-react'

export function CargoDetail() {
    const { id } = useParams<{ id: string }>()
    const navigate = useNavigate()
    const { user } = useAuth()
    const queryClient = useQueryClient()
    const [actionError, setActionError] = useState<string | null>(null)
    const [reason, setReason] = useState('')

    const { data: cargo, isLoading, error } = useQuery({
        queryKey: ['cargoDetail', id],
        queryFn: () => getCargoTicket(Number(id)),
        enabled: !!id,
    })

    const invalidateCargo = () => {
        queryClient.invalidateQueries({ queryKey: ['cargoDetail', id] })
        queryClient.invalidateQueries({ queryKey: ['cargo'] })
    }

    const extractErrorMsg = (err: unknown, defaultMsg: string) => {
        if (err && typeof err === 'object' && 'response' in err) {
            const response = (err as { response?: { data?: { detail?: string } } }).response
            if (response?.data?.detail) return response.data.detail
        }
        return defaultMsg
    }

    const transitionMutation = useMutation({
        mutationFn: ({ status, reqReason }: { status: CargoStatus, reqReason?: string }) => {
            if (status === 'delivered') return deliverCargoTicket(Number(id))
            return transitionCargoStatus(Number(id), status, reqReason)
        },
        onSuccess: () => {
            invalidateCargo()
            setActionError(null)
            setReason('')
        },
        onError: (err) => setActionError(extractErrorMsg(err, "Erreur lors de la mise à jour du statut."))
    })

    const deleteMutation = useMutation({
        mutationFn: () => deleteCargoTicket(Number(id)),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['cargo'] })
            navigate('/cargo')
        },
        onError: (err) => setActionError(extractErrorMsg(err, 'Erreur lors de la suppression du colis.')),
    })

    if (isLoading) {
        return (
            <div className="flex justify-center items-center h-64">
                <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-brand-500"></div>
            </div>
        )
    }

    if (error || !cargo) {
        return (
            <div className="bg-red-500/10 bg-red-500/10 border border-status-error/30 text-red-600 dark:text-red-400 p-4 rounded-lg">
                Erreur de chargement des détails du colis.
            </div>
        )
    }

    const availableTransitions = VALID_TRANSITIONS[cargo.status] || []

    return (
        <div className="space-y-6 animate-fade-in">
            {/* Header section */}
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <div className="flex items-center gap-4">
                    <Link
                        to="/cargo"
                        className="p-2 -ml-2 text-text-muted hover:text-text-primary hover:bg-slate-200 dark:bg-slate-700/50 rounded-lg transition-colors"
                    >
                        <ArrowLeft className="w-5 h-5" />
                    </Link>
                    <div>
                        <div className="flex items-center gap-3">
                            <h1 className="text-2xl font-bold text-text-primary">
                                Colis {cargo.ticket_number}
                            </h1>
                            <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium border ${['delivered', 'paid'].includes(cargo.status) ? 'bg-status-success/10 text-emerald-400 border-status-success/20' :
                                ['created', 'in_transit', 'arrived'].includes(cargo.status) ? 'bg-[#137fec]/10 text-brand-400 border-brand-500/20' :
                                    'bg-red-500/10 bg-red-500/10 text-red-600 dark:text-red-400 border-status-error/20'
                                }`}>
                                {CARGO_STATUS_LABELS[cargo.status] || cargo.status}
                            </span>
                        </div>
                        <p className="text-sm text-text-muted mt-1">Voyage {cargo.trip} associé</p>
                    </div>
                </div>
            </div>

            {actionError && (
                <div className="bg-red-500/10 bg-red-500/10 border border-status-error/20 rounded-lg p-4 flex items-start gap-3">
                    <AlertCircle className="w-5 h-5 text-red-600 dark:text-red-400 shrink-0 mt-0.5" />
                    <p className="text-sm text-red-600 dark:text-red-400">{actionError}</p>
                </div>
            )}

            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                <div className="lg:col-span-2 space-y-6">
                    {/* Information Card */}
                    <div className="bg-surface-800/80 backdrop-blur-md border border-surface-700 rounded-xl p-6">
                        <h3 className="text-lg font-semibold text-text-primary border-b border-surface-700/50 pb-2 mb-4">
                            Informations d'expédition
                        </h3>
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                            <div className="space-y-4">
                                <div>
                                    <p className="text-xs text-text-muted flex items-center gap-1.5 mb-1"><User className="w-3.5 h-3.5" /> Expéditeur</p>
                                    <p className="text-sm font-medium text-text-primary">{cargo.sender_name}</p>
                                    <p className="text-sm text-text-secondary">{cargo.sender_phone || 'Non spécifié'}</p>
                                </div>
                                <div>
                                    <p className="text-xs text-text-muted flex items-center gap-1.5 mb-1"><Package className="w-3.5 h-3.5" /> Type de Colis</p>
                                    <p className="text-sm font-medium text-text-primary capitalize">{cargo.cargo_tier}</p>
                                    <p className="text-sm text-text-secondary">{cargo.description || 'Aucune description'}</p>
                                </div>
                            </div>
                            <div className="space-y-4">
                                <div>
                                    <p className="text-xs text-text-muted flex items-center gap-1.5 mb-1"><User className="w-3.5 h-3.5" /> Destinataire</p>
                                    <p className="text-sm font-medium text-text-primary">{cargo.receiver_name}</p>
                                    <p className="text-sm text-text-secondary">{cargo.receiver_phone || 'Non spécifié'}</p>
                                </div>
                                <div>
                                    <p className="text-xs text-text-muted flex items-center gap-1.5 mb-1"><MapPin className="w-3.5 h-3.5" /> Tarification</p>
                                    <p className="text-sm font-medium text-text-primary">{formatCurrency(cargo.price)}</p>
                                    <p className="text-xs uppercase font-bold text-brand-400 mt-0.5">
                                        {cargo.payment_source === 'prepaid' ? 'Prépayé' : 'Paiement à la livraison'}
                                    </p>
                                </div>
                            </div>
                        </div>

                        <div className="mt-6 pt-4 border-t border-surface-700/50 flex items-center gap-4 text-xs text-text-muted">
                            <span className="flex items-center gap-1.5"><Clock className="w-3.5 h-3.5" /> Créé: {formatDateTime(cargo.created_at)}</span>
                            {cargo.delivered_at && <span className="flex items-center gap-1.5"><CheckCircle className="w-3.5 h-3.5" /> Livré: {formatDateTime(cargo.delivered_at)}</span>}
                        </div>
                    </div>
                </div>

                {/* Actions Sandbox */}
                <div className="space-y-6">
                    <div className="bg-surface-800/80 backdrop-blur-md border border-brand-500/20 rounded-xl p-6">
                        <h3 className="text-lg font-semibold text-text-primary border-b border-surface-700/50 pb-2 mb-4">
                            Actions (Statut)
                        </h3>

                        {availableTransitions.length === 0 ? (
                            <div className="text-sm text-text-muted flex items-center gap-2">
                                <CheckCircle className="w-4 h-4 text-emerald-400" />
                                Aucune transition disponible.
                            </div>
                        ) : (
                            <div className="space-y-4">
                                {(availableTransitions.includes('lost') || availableTransitions.includes('refused') || availableTransitions.includes('cancelled')) && (
                                    <div>
                                        <label className="block text-xs font-medium text-text-secondary mb-1">Motif (si requis)</label>
                                        <input
                                            type="text"
                                            value={reason}
                                            onChange={(e) => setReason(e.target.value)}
                                            className="w-full bg-surface-900 border border-surface-700 rounded-lg px-3 py-2 text-sm text-text-primary focus:outline-none focus:ring-2 focus:ring-brand-500"
                                            placeholder="Ex: endommagé, introuvable..."
                                        />
                                    </div>
                                )}
                                <div className="grid grid-cols-1 gap-2">
                                    {availableTransitions
                                        .filter(status => {
                                            if (status === 'delivered') {
                                                return user?.role === 'admin' || user?.office === cargo.trip_destination_office_id
                                            }
                                            return true
                                        })
                                        .map(status => {
                                            const isNegative = ['lost', 'refused', 'cancelled', 'refunded'].includes(status)
                                            return (
                                                <Button
                                                    key={status}
                                                    variant={isNegative ? 'danger' : 'primary'}
                                                    className="w-full justify-center"
                                                    isLoading={transitionMutation.isPending && transitionMutation.variables?.status === status}
                                                    onClick={() => transitionMutation.mutate({ status, reqReason: reason })}
                                                >
                                                    Passer en: {CARGO_STATUS_LABELS[status]}
                                                </Button>
                                            )
                                        })}
                                </div>
                            </div>
                        )}

                        {user?.role === 'admin' && ['created', 'cancelled', 'refunded'].includes(cargo.status) && (
                            <div className="pt-4 border-t border-surface-700/50 mt-4">
                                <Button
                                    variant="danger"
                                    className="w-full justify-center"
                                    icon={<Trash2 className="w-4 h-4" />}
                                    isLoading={deleteMutation.isPending}
                                    onClick={() => {
                                        if (window.confirm('Supprimer définitivement ce colis ?')) {
                                            deleteMutation.mutate()
                                        }
                                    }}
                                >
                                    Supprimer le colis
                                </Button>
                            </div>
                        )}
                    </div>
                </div>
            </div>
        </div>
    )
}
