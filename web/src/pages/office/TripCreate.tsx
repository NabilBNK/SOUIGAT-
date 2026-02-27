import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { createTrip } from '../../api/trips'
import { getOffices, getBuses, getUsers } from '../../api/admin'
import { useAuth } from '../../hooks/useAuth'
import { Button } from '../../components/ui/Button'
import { AlertCircle, ArrowLeft, Calendar, Bus as BusIcon, MapPin, User as UserIcon } from 'lucide-react'
import type { TripCreate } from '../../types/trip'
import type { Office, Bus } from '../../types/admin'
import type { User } from '../../types/auth'
import { Link } from 'react-router-dom'

export function TripCreatePage() {
    // ...
    // Keeping the rest identical up to the map functions, I will use multi_replace for accuracy.
    const navigate = useNavigate()
    const { user } = useAuth()
    const queryClient = useQueryClient()
    const [error, setError] = useState<string | null>(null)

    // Form State
    const [originId, setOriginId] = useState<string>(user?.office?.toString() || '')
    const [destinationId, setDestinationId] = useState<string>('')
    const [busId, setBusId] = useState<string>('')
    const [conductorId, setConductorId] = useState<string>('')

    // Date and Time (combining into ISO string later)
    const [departureDate, setDepartureDate] = useState<string>('')
    const [departureTime, setDepartureTime] = useState<string>('')

    // Reference Data Queries
    const { data: officesData } = useQuery({
        queryKey: ['offices'],
        queryFn: () => getOffices(),
    })
    const offices = officesData?.results || []

    const { data: busesData } = useQuery({
        queryKey: ['buses_for_office', originId],
        queryFn: () => getBuses({ current_office_id: Number(originId) }),
        enabled: !!originId,
    })
    const buses = busesData?.results || []

    const { data: usersData } = useQuery({
        queryKey: ['conductors'],
        // Filter conductors. They don't have to be in the same office.
        queryFn: () => getUsers({ role: 'conductor' }),
    })
    const conductors = usersData?.results || []

    const { mutate, isPending } = useMutation({
        mutationFn: (data: TripCreate) => createTrip(data),
        onSuccess: (newTrip) => {
            queryClient.invalidateQueries({ queryKey: ['trips'] })
            navigate(`/office/trips/${newTrip.id}`)
        },
        onError: (err: unknown) => {
            let msg = 'Erreur lors de la création du voyage.'
            if (err && typeof err === 'object' && 'response' in err) {
                const response = (err as any).response
                if (response?.data) {
                    if (typeof response.data === 'string') {
                        msg = response.data
                    } else if (response.data.detail) {
                        msg = response.data.detail
                    } else if (response.data.route) {
                        msg = Array.isArray(response.data.route) ? response.data.route.join(' ') : response.data.route
                    } else {
                        // Extract first error from values
                        const firstErrorKey = Object.keys(response.data)[0];
                        if (firstErrorKey && Array.isArray(response.data[firstErrorKey])) {
                            msg = `${firstErrorKey}: ${response.data[firstErrorKey].join(' ')}`;
                        } else {
                            msg = JSON.stringify(response.data)
                        }
                    }
                }
            }
            setError(msg)
        },
    })

    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault()
        setError(null)

        if (!originId || !destinationId || !busId || !conductorId || !departureDate || !departureTime) {
            setError('Tous les champs sont requis.')
            return
        }

        if (originId === destinationId) {
            setError('Le point de départ et d\'arrivée doivent être différents.')
            return
        }

        // Determine timezone offset or just send simple ISO if backend handles it
        // Django expects an ISO 8601 string: YYYY-MM-DDTHH:MM:SSZ
        // Here we'll construct a local datetime string that the backend can parse
        const datetimeStr = `${departureDate}T${departureTime}:00`
        const isoDate = new Date(datetimeStr).toISOString()

        mutate({
            origin_office: Number(originId),
            destination_office: Number(destinationId),
            bus: Number(busId),
            conductor: Number(conductorId),
            departure_datetime: isoDate,
        })
    }

    return (
        <div className="max-w-3xl mx-auto space-y-6 animate-fade-in">
            <div className="flex items-center gap-4">
                <Link
                    to="/office/trips"
                    className="p-2 -ml-2 text-text-muted hover:text-text-primary hover:bg-surface-600/50 rounded-lg transition-colors"
                >
                    <ArrowLeft className="w-5 h-5" />
                </Link>
                <div>
                    <h1 className="text-xl font-bold text-text-primary">Nouveau voyage</h1>
                    <p className="text-sm text-text-muted mt-1">
                        Programmez un nouveau départ. Le prix sera figé lors de la création.
                    </p>
                </div>
            </div>

            <form onSubmit={handleSubmit} className="bg-surface-800 border border-surface-600/50 rounded-xl p-6 space-y-8">
                {error && (
                    <div className="bg-status-error/10 border border-status-error/20 rounded-lg p-4 flex items-start gap-3">
                        <AlertCircle className="w-5 h-5 text-status-error shrink-0 mt-0.5" />
                        <p className="text-sm text-status-error">{error}</p>
                    </div>
                )}

                <div className="space-y-6">
                    {/* Itinerary */}
                    <div className="space-y-4">
                        <h3 className="text-sm font-semibold text-text-primary uppercase tracking-wider flex items-center gap-2 border-b border-surface-600/30 pb-2">
                            <MapPin className="w-4 h-4 text-brand-400" />
                            Itinéraire
                        </h3>

                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                            <div>
                                <label className="block text-sm font-medium text-text-secondary mb-1.5">Départ</label>
                                <select
                                    value={originId}
                                    onChange={(e) => {
                                        setOriginId(e.target.value)
                                        setBusId('') // Reset bus when origin changes
                                    }}
                                    className="w-full bg-surface-700 border border-surface-600/50 rounded-lg px-4 py-2.5 text-text-primary focus:outline-none focus:ring-2 focus:ring-brand-500/50 transition-shadow disabled:opacity-50"
                                    required
                                >
                                    <option value="" disabled>Sélectionner un bureau</option>
                                    {offices.map((o: Office) => (
                                        <option key={o.id} value={o.id}>{o.name} ({o.city})</option>
                                    ))}
                                </select>
                            </div>

                            <div>
                                <label className="block text-sm font-medium text-text-secondary mb-1.5">Arrivée</label>
                                <select
                                    value={destinationId}
                                    onChange={(e) => setDestinationId(e.target.value)}
                                    className="w-full bg-surface-700 border border-surface-600/50 rounded-lg px-4 py-2.5 text-text-primary focus:outline-none focus:ring-2 focus:ring-brand-500/50 transition-shadow"
                                    required
                                >
                                    <option value="" disabled>Sélectionner la destination</option>
                                    {offices.map((o: Office) => (
                                        <option key={o.id} value={o.id} disabled={o.id.toString() === originId}>
                                            {o.name} ({o.city})
                                        </option>
                                    ))}
                                </select>
                            </div>
                        </div>
                    </div>

                    {/* Schedule */}
                    <div className="space-y-4">
                        <h3 className="text-sm font-semibold text-text-primary uppercase tracking-wider flex items-center gap-2 border-b border-surface-600/30 pb-2">
                            <Calendar className="w-4 h-4 text-brand-400" />
                            Planification
                        </h3>

                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                            <div>
                                <label className="block text-sm font-medium text-text-secondary mb-1.5">Date de départ</label>
                                <input
                                    type="date"
                                    value={departureDate}
                                    onChange={(e) => setDepartureDate(e.target.value)}
                                    min={new Date().toISOString().split('T')[0]} // Prevents past dates
                                    className="w-full bg-surface-700 border border-surface-600/50 rounded-lg px-4 py-2.5 text-text-primary focus:outline-none focus:ring-2 focus:ring-brand-500/50 transition-shadow [color-scheme:dark]"
                                    required
                                />
                            </div>

                            <div>
                                <label className="block text-sm font-medium text-text-secondary mb-1.5">Heure de départ</label>
                                <input
                                    type="time"
                                    value={departureTime}
                                    onChange={(e) => setDepartureTime(e.target.value)}
                                    className="w-full bg-surface-700 border border-surface-600/50 rounded-lg px-4 py-2.5 text-text-primary focus:outline-none focus:ring-2 focus:ring-brand-500/50 transition-shadow [color-scheme:dark]"
                                    required
                                />
                            </div>
                        </div>
                    </div>

                    {/* Resources */}
                    <div className="space-y-4">
                        <h3 className="text-sm font-semibold text-text-primary uppercase tracking-wider flex items-center gap-2 border-b border-surface-600/30 pb-2">
                            <BusIcon className="w-4 h-4 text-brand-400" />
                            Ressources
                        </h3>

                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                            <div>
                                <label className="block text-sm font-medium text-text-secondary mb-1.5">Bus</label>
                                <select
                                    value={busId}
                                    onChange={(e) => setBusId(e.target.value)}
                                    className="w-full bg-surface-700 border border-surface-600/50 rounded-lg px-4 py-2.5 text-text-primary focus:outline-none focus:ring-2 focus:ring-brand-500/50 transition-shadow"
                                    required
                                    disabled={!originId}
                                >
                                    <option value="" disabled>
                                        {!originId ? 'Sélectionnez un départ d\'abord' : 'Sélectionner un bus (Disponible)'}
                                    </option>
                                    {buses.map((b: Bus) => (
                                        <option key={b.id} value={b.id}>
                                            {b.plate_number} - {b.model} ({b.capacity} places)
                                        </option>
                                    ))}
                                </select>
                                {originId && buses.length === 0 && (
                                    <p className="text-[12px] text-status-warning mt-1">Aucun bus n'est actuellement stationné dans ce bureau.</p>
                                )}
                            </div>

                            <div>
                                <label className="block text-sm font-medium text-text-secondary mb-1.5 flex items-center gap-1">
                                    <UserIcon className="w-3.5 h-3.5" />
                                    Conducteur
                                </label>
                                <select
                                    value={conductorId}
                                    onChange={(e) => setConductorId(e.target.value)}
                                    className="w-full bg-surface-700 border border-surface-600/50 rounded-lg px-4 py-2.5 text-text-primary focus:outline-none focus:ring-2 focus:ring-brand-500/50 transition-shadow"
                                    required
                                >
                                    <option value="" disabled>Sélectionner un conducteur</option>
                                    {conductors.map((c: User) => (
                                        <option key={c.id} value={c.id}>
                                            {c.first_name} {c.last_name} ({c.phone})
                                        </option>
                                    ))}
                                </select>
                            </div>
                        </div>
                    </div>
                </div>

                <div className="pt-6 border-t border-surface-600/30 flex justify-end gap-3">
                    <Link
                        to="/office/trips"
                        className="px-5 py-2.5 rounded-lg text-sm font-medium text-text-secondary hover:text-text-primary hover:bg-surface-700 transition-colors"
                    >
                        Annuler
                    </Link>
                    <Button type="submit" isLoading={isPending}>
                        Programmer le voyage
                    </Button>
                </div>
            </form>
        </div>
    )
}
