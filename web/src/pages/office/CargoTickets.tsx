import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { createCargoTicket, deleteCargoTicket } from '../../api/cargo'
import { useAuth } from '../../hooks/useAuth'
import { useTripCargoMirror } from '../../hooks/useTripMirrorData'
import { Button } from '../../components/ui/Button'
import { formatCurrency } from '../../utils/formatters'
import { AlertCircle, Package, Plus, Trash2 } from 'lucide-react'
import { CARGO_STATUS_LABELS } from '../../types/ticket'
import type { Trip } from '../../types/trip'

interface CargoTicketsProps {
    trip: Trip
}

export function CargoTickets({ trip }: CargoTicketsProps) {
    const { user } = useAuth()
    const [actionError, setActionError] = useState<string | null>(null)
    const [isCreating, setIsCreating] = useState(false)

    // Form State
    const [senderName, setSenderName] = useState('')
    const [senderPhone, setSenderPhone] = useState('')
    const [receiverName, setReceiverName] = useState('')
    const [receiverPhone, setReceiverPhone] = useState('')
    const [cargoTier, setCargoTier] = useState('small')
    const [description, setDescription] = useState('')
    const [paymentSource, setPaymentSource] = useState('prepaid')

    const {
        data: tickets,
        isLoading,
        error,
    } = useTripCargoMirror(trip.id, {
        enableRealtime: true,
    })

    const extractErrorMsg = (err: unknown, defaultMsg: string) => {
        if (err && typeof err === 'object' && 'response' in err) {
            const response = (err as { response?: { data?: { detail?: string } } }).response
            if (response?.data?.detail) return response.data.detail
        }
        return defaultMsg
    }

    const createMutation = useMutation({
        mutationFn: () => createCargoTicket(trip.id, {
            sender_name: senderName,
            sender_phone: senderPhone || undefined,
            receiver_name: receiverName,
            receiver_phone: receiverPhone || undefined,
            cargo_tier: cargoTier,
            description: description || undefined,
            payment_source: paymentSource
        }),
        onSuccess: () => {
            setIsCreating(false)
            // Reset form
            setSenderName('')
            setSenderPhone('')
            setReceiverName('')
            setReceiverPhone('')
            setCargoTier('small')
            setDescription('')
            setPaymentSource('prepaid')
            setActionError(null)
        },
        onError: (err) => setActionError(extractErrorMsg(err, "Erreur lors de la création du colis."))
    })

    const deleteMutation = useMutation({
        mutationFn: (ticketId: number) => deleteCargoTicket(ticketId),
        onSuccess: () => setActionError(null),
        onError: (err) => setActionError(extractErrorMsg(err, 'Erreur lors de la suppression du colis.')),
    })

    const handleCreateSubmit = (e: React.FormEvent) => {
        e.preventDefault()
        if (!senderName || !receiverName) {
            setActionError("Les noms de l'expéditeur et du destinataire sont requis.")
            return
        }
        createMutation.mutate()
    }

    if (isLoading) {
        return <div className="animate-pulse h-32 bg-surface-800/80 backdrop-blur-md rounded-xl" />
    }

    if (error) {
        return (
            <div className="bg-red-500/10 bg-red-500/10 border border-status-error/30 text-red-600 dark:text-red-400 p-4 rounded-lg">
                Erreur de chargement des colis.
            </div>
        )
    }

    const canManageCargo = trip.status === 'scheduled' && (
        user?.role === 'admin'
        || (
            user?.role === 'office_staff'
            && (user.office === trip.origin_office || user.office === trip.destination_office)
        )
    )

    // Calculate total cargo revenue safely
    const calculateTotalCargoRevenue = () => {
        return tickets
            .filter(t => t.status !== 'refunded' && t.status !== 'cancelled' && t.status !== 'lost')
            .reduce((total, ticket) => total + ticket.price, 0)
    }

    return (
        <div className="space-y-6 animate-fade-in">
            {actionError && (
                <div className="bg-red-500/10 bg-red-500/10 border border-status-error/20 rounded-lg p-4 flex items-start gap-3">
                    <AlertCircle className="w-5 h-5 text-red-600 dark:text-red-400 shrink-0 mt-0.5" />
                    <p className="text-sm text-red-600 dark:text-red-400">{actionError}</p>
                </div>
            )}

            <div className="flex justify-between items-center">
                <div>
                    <h3 className="text-lg font-semibold text-text-primary">Colis et Messagerie</h3>
                    <p className="text-sm text-text-muted">
                        Total expédié: <span className="font-semibold text-text-primary">{tickets.length}</span> / Revenue: {formatCurrency(calculateTotalCargoRevenue())}
                    </p>
                </div>
                {canManageCargo && !isCreating && (
                    <Button
                        onClick={() => setIsCreating(true)}
                        icon={<Plus className="w-4 h-4" />}
                        size="sm"
                    >
                        Nouveau Colis
                    </Button>
                )}
            </div>

            {isCreating && (
                <form onSubmit={handleCreateSubmit} className="bg-surface-800/80 backdrop-blur-md/50 border border-brand-500/30 rounded-xl p-5 space-y-4">
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        {/* Sender info */}
                        <div className="space-y-4">
                            <h4 className="text-sm font-medium text-text-primary border-b border-surface-700 pb-1">Expéditeur</h4>
                            <div>
                                <label className="block text-xs font-medium text-text-secondary mb-1">Nom *</label>
                                <input
                                    type="text"
                                    value={senderName}
                                    onChange={(e) => setSenderName(e.target.value)}
                                    className="w-full bg-surface-900 border border-surface-700 rounded-lg px-3 py-2 text-sm text-text-primary focus:outline-none focus:ring-2 focus:ring-brand-500"
                                    required
                                />
                            </div>
                            <div>
                                <label className="block text-xs font-medium text-text-secondary mb-1">Téléphone</label>
                                <input
                                    type="text"
                                    value={senderPhone}
                                    onChange={(e) => setSenderPhone(e.target.value)}
                                    className="w-full bg-surface-900 border border-surface-700 rounded-lg px-3 py-2 text-sm text-text-primary focus:outline-none focus:ring-2 focus:ring-brand-500"
                                />
                            </div>
                        </div>

                        {/* Receiver Info */}
                        <div className="space-y-4">
                            <h4 className="text-sm font-medium text-text-primary border-b border-surface-700 pb-1">Destinataire</h4>
                            <div>
                                <label className="block text-xs font-medium text-text-secondary mb-1">Nom *</label>
                                <input
                                    type="text"
                                    value={receiverName}
                                    onChange={(e) => setReceiverName(e.target.value)}
                                    className="w-full bg-surface-900 border border-surface-700 rounded-lg px-3 py-2 text-sm text-text-primary focus:outline-none focus:ring-2 focus:ring-brand-500"
                                    required
                                />
                            </div>
                            <div>
                                <label className="block text-xs font-medium text-text-secondary mb-1">Téléphone</label>
                                <input
                                    type="text"
                                    value={receiverPhone}
                                    onChange={(e) => setReceiverPhone(e.target.value)}
                                    className="w-full bg-surface-900 border border-surface-700 rounded-lg px-3 py-2 text-sm text-text-primary focus:outline-none focus:ring-2 focus:ring-brand-500"
                                />
                            </div>
                        </div>
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4 pt-2 border-t border-surface-700/50">
                        <div>
                            <label className="block text-xs font-medium text-text-secondary mb-1">Taille (Tier)</label>
                            <select
                                value={cargoTier}
                                onChange={(e) => setCargoTier(e.target.value)}
                                className="w-full bg-surface-900 border border-surface-700 rounded-lg px-3 py-2 text-sm text-text-primary focus:outline-none focus:ring-2 focus:ring-brand-500"
                            >
                                <option value="small">Petit ({trip.cargo_small_price ? formatCurrency(trip.cargo_small_price) : '-'})</option>
                                <option value="medium">Moyen ({trip.cargo_medium_price ? formatCurrency(trip.cargo_medium_price) : '-'})</option>
                                <option value="large">Grand ({trip.cargo_large_price ? formatCurrency(trip.cargo_large_price) : '-'})</option>
                            </select>
                        </div>
                        <div>
                            <label className="block text-xs font-medium text-text-secondary mb-1">Paiement</label>
                            <select
                                value={paymentSource}
                                onChange={(e) => setPaymentSource(e.target.value)}
                                className="w-full bg-surface-900 border border-surface-700 rounded-lg px-3 py-2 text-sm text-text-primary focus:outline-none focus:ring-2 focus:ring-brand-500"
                            >
                                <option value="prepaid">Prépayé (Expéditeur)</option>
                                <option value="pay_on_delivery">Paiement à la livraison</option>
                            </select>
                        </div>
                        <div>
                            <label className="block text-xs font-medium text-text-secondary mb-1">Description</label>
                            <input
                                type="text"
                                value={description}
                                onChange={(e) => setDescription(e.target.value)}
                                className="w-full bg-surface-900 border border-surface-700 rounded-lg px-3 py-2 text-sm text-text-primary focus:outline-none focus:ring-2 focus:ring-brand-500"
                                placeholder="..."
                            />
                        </div>
                    </div>

                    <div className="flex justify-end gap-2 pt-2 border-t border-surface-700/50">
                        <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            onClick={() => setIsCreating(false)}
                            disabled={createMutation.isPending}
                        >
                            Annuler
                        </Button>
                        <Button
                            type="submit"
                            size="sm"
                            isLoading={createMutation.isPending}
                        >
                            Créer Colis
                        </Button>
                    </div>
                </form>
            )}

            {tickets.length === 0 ? (
                <div className="bg-surface-800/80 backdrop-blur-md border border-surface-700 rounded-xl py-12 text-center">
                    <Package className="w-12 h-12 text-surface-600 mx-auto mb-3" />
                    <p className="text-text-muted">Aucun colis enregistré pour ce voyage.</p>
                </div>
            ) : (
                <div className="bg-surface-800/80 backdrop-blur-md border border-surface-700 rounded-xl overflow-hidden">
                    <div className="overflow-x-auto">
                        <table className="w-full text-left text-sm text-text-secondary">
                            <thead className="bg-surface-700/50 text-xs uppercase text-text-muted font-medium border-b border-surface-700">
                                <tr>
                                    <th className="px-6 py-3">Réf</th>
                                    <th className="px-6 py-3">Expéditeur</th>
                                    <th className="px-6 py-3">Destinataire</th>
                                    <th className="px-6 py-3">Type</th>
                                    <th className="px-6 py-3">Prix</th>
                                    <th className="px-6 py-3">Statut</th>
                                    <th className="px-6 py-3 text-right">Actions</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-surface-600/30">
                                {tickets.map((ticket) => (
                                    <tr key={ticket.id} className="hover:bg-surface-900/30 transition-colors">
                                        <td className="px-6 py-4 font-mono text-xs text-text-primary">{ticket.ticket_number}</td>
                                        <td className="px-6 py-4 text-text-primary">{ticket.sender_name} <span className="text-xs text-text-muted block">{ticket.sender_phone}</span></td>
                                        <td className="px-6 py-4 text-text-primary">{ticket.receiver_name} <span className="text-xs text-text-muted block">{ticket.receiver_phone}</span></td>
                                        <td className="px-6 py-4">
                                            <span className="capitalize">{ticket.cargo_tier}</span>
                                        </td>
                                        <td className="px-6 py-4">
                                            {formatCurrency(ticket.price)}
                                            <span className={`text-[10px] uppercase font-bold px-1.5 py-0.5 rounded ml-2 ${ticket.payment_source === 'prepaid' ? 'bg-status-success/10 text-emerald-400' : 'bg-status-warning/10 text-yellow-600 dark:text-yellow-400'}`}>
                                                {ticket.payment_source === 'prepaid' ? 'Payé' : 'À payer'}
                                            </span>
                                        </td>
                                        <td className="px-6 py-4">
                                            <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium border ${['delivered', 'paid'].includes(ticket.status) ? 'bg-status-success/10 text-emerald-400 border-status-success/20' :
                                                ['created', 'in_transit', 'arrived'].includes(ticket.status) ? 'bg-[#137fec]/10 text-brand-400 border-brand-500/20' :
                                                    'bg-red-500/10 bg-red-500/10 text-red-600 dark:text-red-400 border-status-error/20'
                                                }`}>
                                                {CARGO_STATUS_LABELS[ticket.status] || ticket.status}
                                            </span>
                                        </td>
                                        <td className="px-6 py-4 text-right">
                                            {canManageCargo && ['created', 'cancelled', 'refunded'].includes(ticket.status) && (
                                                <Button
                                                    variant="danger"
                                                    size="sm"
                                                    className="px-2 py-1 h-7 text-xs"
                                                    isLoading={deleteMutation.isPending && deleteMutation.variables === ticket.id}
                                                    onClick={() => {
                                                        if (window.confirm('Supprimer ce colis ? Cette action est définitive.')) {
                                                            deleteMutation.mutate(ticket.id)
                                                        }
                                                    }}
                                                >
                                                    <Trash2 className="w-3 h-3 mr-1" />
                                                    Supprimer
                                                </Button>
                                            )}
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </div>
            )}
        </div>
    )
}
