import { useMemo, useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { createTrip, getTripReferenceData } from '../../api/trips'
import { useAuth } from '../../hooks/useAuth'
import { Button } from '../../components/ui/Button'
import { AlertCircle, ArrowLeft, Calendar, Bus as BusIcon, MapPin, User as UserIcon } from 'lucide-react'
import type { TripCreate, RouteTemplateRef } from '../../types/trip'
import type { Bus } from '../../types/admin'
import type { User } from '../../types/auth'
import { queueTripUpsert } from '../../sync/tripSync'

export function TripCreatePage() {
    const navigate = useNavigate()
    const { user } = useAuth()
    const queryClient = useQueryClient()
    const [error, setError] = useState<string | null>(null)

    const [routeTemplateId, setRouteTemplateId] = useState<string>('')
    const [busId, setBusId] = useState<string>('')
    const [conductorId, setConductorId] = useState<string>('')
    const [departureDate, setDepartureDate] = useState<string>('')
    const [departureTime, setDepartureTime] = useState<string>('')

    const { data: referenceData } = useQuery({
        queryKey: ['trip_reference_data'],
        queryFn: () => getTripReferenceData(),
    })

    const routeTemplates = referenceData?.route_templates || []
    const buses = referenceData?.buses || []
    const conductors = referenceData?.conductors || []

    const filteredTemplates = useMemo(() => {
        if (user?.role !== 'office_staff' || !user.office) {
            return routeTemplates
        }
        return routeTemplates.filter((template) => template.start_office_id === user.office)
    }, [routeTemplates, user?.office, user?.role])

    const selectedTemplate = filteredTemplates.find((template) => template.id.toString() === routeTemplateId)

    const { mutate, isPending } = useMutation({
        mutationFn: (data: TripCreate) => createTrip(data),
        onSuccess: (newTrip) => {
            void queueTripUpsert(newTrip).catch((syncError) => {
                console.warn('[SYNC] Failed to queue trip mirror sync after trip creation.', syncError)
            })
            queryClient.invalidateQueries({ queryKey: ['trips'] })
            navigate(`/office/trips/${newTrip.id}`)
        },
        onError: (err: unknown) => {
            let msg = 'Erreur lors de la creation du voyage.'
            if (err && typeof err === 'object' && 'response' in err) {
                const response = (err as any).response
                if (response?.data) {
                    if (typeof response.data === 'string') {
                        msg = response.data
                    } else if (response.data.detail) {
                        msg = response.data.detail
                    } else if (response.data.route_template) {
                        msg = Array.isArray(response.data.route_template)
                            ? response.data.route_template.join(' ')
                            : response.data.route_template
                    } else {
                        const firstErrorKey = Object.keys(response.data)[0]
                        if (firstErrorKey && Array.isArray(response.data[firstErrorKey])) {
                            msg = `${firstErrorKey}: ${response.data[firstErrorKey].join(' ')}`
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

        if (!routeTemplateId || !busId || !conductorId || !departureDate || !departureTime) {
            setError('Tous les champs sont requis.')
            return
        }

        const datetimeStr = `${departureDate}T${departureTime}:00`
        const isoDate = new Date(datetimeStr).toISOString()

        mutate({
            route_template: Number(routeTemplateId),
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
                    className="p-2 -ml-2 text-text-muted hover:text-text-primary hover:bg-slate-200 dark:bg-slate-700/50 rounded-lg transition-colors"
                >
                    <ArrowLeft className="w-5 h-5" />
                </Link>
                <div>
                    <h1 className="text-xl font-bold text-text-primary">Nouveau voyage</h1>
                    <p className="text-sm text-text-muted mt-1">
                        Programmez un voyage depuis un template de route.
                    </p>
                </div>
            </div>

            <form onSubmit={handleSubmit} className="bg-surface-800/80 backdrop-blur-md border border-surface-700 rounded-xl p-6 space-y-8">
                {error && (
                    <div className="bg-red-500/10 border border-status-error/20 rounded-lg p-4 flex items-start gap-3">
                        <AlertCircle className="w-5 h-5 text-red-600 dark:text-red-400 shrink-0 mt-0.5" />
                        <p className="text-sm text-red-600 dark:text-red-400">{error}</p>
                    </div>
                )}

                <div className="space-y-6">
                    <div className="space-y-4">
                        <h3 className="text-sm font-semibold text-text-primary uppercase tracking-wider flex items-center gap-2 border-b border-surface-700/50 pb-2">
                            <MapPin className="w-4 h-4 text-brand-400" />
                            Itineraire
                        </h3>

                        <div>
                            <label className="block text-sm font-medium text-text-secondary mb-1.5">Template</label>
                            <select
                                value={routeTemplateId}
                                onChange={(e) => {
                                    setRouteTemplateId(e.target.value)
                                    setBusId('')
                                }}
                                className="w-full bg-surface-900 border border-surface-700 rounded-lg px-4 py-2.5 text-text-primary focus:outline-none focus:ring-2 focus:ring-brand-500 transition-shadow"
                                required
                            >
                                <option value="" disabled>Selectionner un template</option>
                                {filteredTemplates.map((template: RouteTemplateRef) => (
                                    <option key={template.id} value={template.id}>
                                        {template.name} ({template.start_office_name} -&gt; {template.end_office_name})
                                    </option>
                                ))}
                            </select>
                        </div>

                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                            <div>
                                <label className="block text-sm font-medium text-text-secondary mb-1.5">Depart</label>
                                <div className="w-full bg-surface-900 border border-surface-700 rounded-lg px-4 py-2.5 text-text-primary">
                                    {selectedTemplate?.start_office_name || 'Template requis'}
                                </div>
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-text-secondary mb-1.5">Arrivee</label>
                                <div className="w-full bg-surface-900 border border-surface-700 rounded-lg px-4 py-2.5 text-text-primary">
                                    {selectedTemplate?.end_office_name || 'Template requis'}
                                </div>
                            </div>
                        </div>
                    </div>

                    <div className="space-y-4">
                        <h3 className="text-sm font-semibold text-text-primary uppercase tracking-wider flex items-center gap-2 border-b border-surface-700/50 pb-2">
                            <Calendar className="w-4 h-4 text-brand-400" />
                            Planification
                        </h3>

                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                            <div>
                                <label className="block text-sm font-medium text-text-secondary mb-1.5">Date de depart</label>
                                <input
                                    type="date"
                                    value={departureDate}
                                    onChange={(e) => setDepartureDate(e.target.value)}
                                    min={new Date().toISOString().split('T')[0]}
                                    className="w-full bg-surface-900 border border-surface-700 rounded-lg px-4 py-2.5 text-text-primary focus:outline-none focus:ring-2 focus:ring-brand-500 transition-shadow [color-scheme:dark]"
                                    required
                                />
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-text-secondary mb-1.5">Heure de depart</label>
                                <input
                                    type="time"
                                    value={departureTime}
                                    onChange={(e) => setDepartureTime(e.target.value)}
                                    className="w-full bg-surface-900 border border-surface-700 rounded-lg px-4 py-2.5 text-text-primary focus:outline-none focus:ring-2 focus:ring-brand-500 transition-shadow [color-scheme:dark]"
                                    required
                                />
                            </div>
                        </div>
                    </div>

                    <div className="space-y-4">
                        <h3 className="text-sm font-semibold text-text-primary uppercase tracking-wider flex items-center gap-2 border-b border-surface-700/50 pb-2">
                            <BusIcon className="w-4 h-4 text-brand-400" />
                            Ressources
                        </h3>

                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                            <div>
                                <label className="block text-sm font-medium text-text-secondary mb-1.5">Bus</label>
                                <select
                                    value={busId}
                                    onChange={(e) => setBusId(e.target.value)}
                                    className="w-full bg-surface-900 border border-surface-700 rounded-lg px-4 py-2.5 text-text-primary focus:outline-none focus:ring-2 focus:ring-brand-500 transition-shadow"
                                    required
                                    disabled={!routeTemplateId}
                                >
                                    <option value="" disabled>
                                        {!routeTemplateId ? 'Selectionnez un template d abord' : 'Selectionner un bus disponible'}
                                    </option>
                                    {buses.map((b: Bus) => (
                                        <option key={b.id} value={b.id}>
                                            {b.plate_number} - {b.model} ({b.capacity} places)
                                        </option>
                                    ))}
                                </select>
                            </div>

                            <div>
                                <label className="block text-sm font-medium text-text-secondary mb-1.5 flex items-center gap-1">
                                    <UserIcon className="w-3.5 h-3.5" />
                                    Conducteur
                                </label>
                                <select
                                    value={conductorId}
                                    onChange={(e) => setConductorId(e.target.value)}
                                    className="w-full bg-surface-900 border border-surface-700 rounded-lg px-4 py-2.5 text-text-primary focus:outline-none focus:ring-2 focus:ring-brand-500 transition-shadow"
                                    required
                                >
                                    <option value="" disabled>Selectionner un conducteur</option>
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

                <div className="pt-6 border-t border-surface-700/50 flex justify-end gap-3">
                    <Link
                        to="/office/trips"
                        className="px-5 py-2.5 rounded-lg text-sm font-medium text-text-secondary hover:text-text-primary hover:bg-surface-900 transition-colors"
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
