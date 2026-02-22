import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getTripPassengerTickets, createPassengerTicket, updatePassengerTicket } from '../../api/tickets'
import { useAuth } from '../../hooks/useAuth'
import { Button } from '../../components/ui/Button'
import { formatCurrency, formatDateTime } from '../../utils/formatters'
import { AlertCircle, Plus, RotateCcw } from 'lucide-react'
import type { Trip } from '../../types/trip'

interface PassengerTicketsProps {
    trip: Trip
}

export function PassengerTickets({ trip }: PassengerTicketsProps) {
    const { user } = useAuth()
    const queryClient = useQueryClient()
    const [actionError, setActionError] = useState<string | null>(null)
    const [isCreating, setIsCreating] = useState(false)

    // Form State
    const [passengerName, setPassengerName] = useState('')
    const [seatNumber, setSeatNumber] = useState('')
    const [paymentSource, setPaymentSource] = useState('cash')

    const { data: tickets = [], isLoading, error } = useQuery({
        queryKey: ['tickets', trip.id],
        queryFn: () => getTripPassengerTickets(trip.id),
    })

    const invalidateTickets = () => {
        queryClient.invalidateQueries({ queryKey: ['tickets', trip.id] })
        queryClient.invalidateQueries({ queryKey: ['trip', trip.id] }) // To update revenue
    }

    const extractErrorMsg = (err: unknown, defaultMsg: string) => {
        if (err && typeof err === 'object' && 'response' in err) {
            const response = (err as { response?: { data?: { detail?: string } } }).response
            if (response?.data?.detail) return response.data.detail
        }
        return defaultMsg
    }

    const createMutation = useMutation({
        mutationFn: () => createPassengerTicket(trip.id, {
            passenger_name: passengerName,
            seat_number: seatNumber || undefined,
            payment_source: paymentSource
        }),
        onSuccess: () => {
            invalidateTickets()
            setIsCreating(false)
            setPassengerName('')
            setSeatNumber('')
            setPaymentSource('cash')
            setActionError(null)
        },
        onError: (err) => setActionError(extractErrorMsg(err, "Erreur lors de la création du billet."))
    })

    const updateStatusMutation = useMutation({
        mutationFn: ({ id, status }: { id: number, status: 'cancelled' | 'refunded' }) =>
            updatePassengerTicket(id, { status }),
        onSuccess: invalidateTickets,
        onError: (err) => setActionError(extractErrorMsg(err, "Erreur lors de la mise à jour du billet."))
    })

    const handleCreateSubmit = (e: React.FormEvent) => {
        e.preventDefault()
        if (!passengerName) {
            setActionError("Le nom du passager est requis.")
            return
        }
        createMutation.mutate()
    }

    if (isLoading) {
        return <div className="animate-pulse h-32 bg-surface-800 rounded-xl" />
    }

    if (error) {
        return (
            <div className="bg-status-error/10 border border-status-error/30 text-status-error p-4 rounded-lg">
                Erreur de chargement des billets.
            </div>
        )
    }

    const canManageTickets = trip.status === 'scheduled' && (user?.role === 'admin' || user?.office === trip.origin_office)

    return (
        <div className="space-y-6 animate-fade-in">
            {actionError && (
                <div className="bg-status-error/10 border border-status-error/20 rounded-lg p-4 flex items-start gap-3">
                    <AlertCircle className="w-5 h-5 text-status-error shrink-0 mt-0.5" />
                    <p className="text-sm text-status-error">{actionError}</p>
                </div>
            )}

            <div className="flex justify-between items-center">
                <div>
                    <h3 className="text-lg font-semibold text-text-primary">Billets Passagers</h3>
                    <p className="text-sm text-text-muted">
                        Total vendu: <span className="font-semibold text-text-primary">{tickets.length}</span> / {trip.passenger_base_price ? formatCurrency(tickets.filter(t => t.status !== 'refunded' && t.status !== 'cancelled').length * trip.passenger_base_price) : '0 DA'}
                    </p>
                </div>
                {canManageTickets && !isCreating && (
                    <Button
                        onClick={() => setIsCreating(true)}
                        icon={<Plus className="w-4 h-4" />}
                        size="sm"
                    >
                        Nouveau Billet
                    </Button>
                )}
            </div>

            {isCreating && (
                <form onSubmit={handleCreateSubmit} className="bg-surface-800/50 border border-brand-500/30 rounded-xl p-5 space-y-4">
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                        <div>
                            <label className="block text-sm font-medium text-text-secondary mb-1">Nom du passager</label>
                            <input
                                type="text"
                                value={passengerName}
                                onChange={(e) => setPassengerName(e.target.value)}
                                className="w-full bg-surface-700 border border-surface-600/50 rounded-lg px-3 py-2 text-text-primary focus:outline-none focus:ring-2 focus:ring-brand-500/50"
                                required
                            />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-text-secondary mb-1">Siège (Optionnel)</label>
                            <input
                                type="text"
                                value={seatNumber}
                                onChange={(e) => setSeatNumber(e.target.value)}
                                className="w-full bg-surface-700 border border-surface-600/50 rounded-lg px-3 py-2 text-text-primary focus:outline-none focus:ring-2 focus:ring-brand-500/50"
                                placeholder="ex: 12A"
                            />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-text-secondary mb-1">Paiement</label>
                            <select
                                value={paymentSource}
                                onChange={(e) => setPaymentSource(e.target.value)}
                                className="w-full bg-surface-700 border border-surface-600/50 rounded-lg px-3 py-2 text-text-primary focus:outline-none focus:ring-2 focus:ring-brand-500/50"
                            >
                                <option value="cash">Espèces</option>
                                <option value="cib">Carte CIB</option>
                                <option value="baridimob">BaridiMob</option>
                            </select>
                        </div>
                    </div>
                    <div className="flex justify-end gap-2 pt-2 border-t border-surface-600/30">
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
                            Enregistrer
                        </Button>
                    </div>
                </form>
            )}

            {tickets.length === 0 ? (
                <div className="bg-surface-800 border border-surface-600/50 rounded-xl py-12 text-center">
                    <p className="text-text-muted">Aucun billet vendu pour ce voyage.</p>
                </div>
            ) : (
                <div className="bg-surface-800 border border-surface-600/50 rounded-xl overflow-hidden">
                    <div className="overflow-x-auto">
                        <table className="w-full text-left text-sm text-text-secondary">
                            <thead className="bg-surface-700/50 text-xs uppercase text-text-muted font-medium border-b border-surface-600/50">
                                <tr>
                                    <th className="px-6 py-3">Réf</th>
                                    <th className="px-6 py-3">Passager</th>
                                    <th className="px-6 py-3">Siège</th>
                                    <th className="px-6 py-3">Prix</th>
                                    <th className="px-6 py-3">Statut</th>
                                    <th className="px-6 py-3">Vendu le</th>
                                    <th className="px-6 py-3 text-right">Actions</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-surface-600/30">
                                {tickets.map((ticket) => (
                                    <tr key={ticket.id} className="hover:bg-surface-700/30 transition-colors">
                                        <td className="px-6 py-4 font-mono text-xs text-text-primary">{ticket.ticket_number}</td>
                                        <td className="px-6 py-4 text-text-primary">{ticket.passenger_name}</td>
                                        <td className="px-6 py-4">{ticket.seat_number || '-'}</td>
                                        <td className="px-6 py-4">{formatCurrency(ticket.price)}</td>
                                        <td className="px-6 py-4">
                                            <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium border ${ticket.status === 'active' ? 'bg-status-success/10 text-status-success border-status-success/20' :
                                                ticket.status === 'refunded' ? 'bg-status-error/10 text-status-error border-status-error/20' :
                                                    'bg-surface-600/50 text-text-muted border-surface-600'
                                                }`}>
                                                {ticket.status === 'active' ? 'Actif' : ticket.status === 'refunded' ? 'Remboursé' : 'Annulé'}
                                            </span>
                                        </td>
                                        <td className="px-6 py-4 text-xs">{formatDateTime(ticket.created_at)}</td>
                                        <td className="px-6 py-4 text-right space-x-2">
                                            {canManageTickets && ticket.status === 'active' && (
                                                <Button
                                                    variant="danger"
                                                    size="sm"
                                                    className="px-2 py-1 h-7 text-xs"
                                                    onClick={() => {
                                                        if (window.confirm('Confirmer le remboursement ? Cette action est irréversible.')) {
                                                            updateStatusMutation.mutate({ id: ticket.id, status: 'refunded' })
                                                        }
                                                    }}
                                                >
                                                    <RotateCcw className="w-3 h-3 mr-1" />
                                                    Rembourser
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
