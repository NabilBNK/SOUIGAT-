import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getBuses, createBus, updateBus, getOffices } from '../../api/admin'
import { DataTable } from '../../components/ui/DataTable'
import { Button } from '../../components/ui/Button'
import { Modal } from '../../components/ui/Modal'
import { createColumnHelper } from '@tanstack/react-table'
import { Search, Plus, ShieldAlert, Edit, Power, Users } from 'lucide-react'
import type { Bus } from '../../types/admin'

const columnHelper = createColumnHelper<any>()

export function BusManagement() {
    const queryClient = useQueryClient()
    const [page, setPage] = useState(1)
    const [search, setSearch] = useState('')

    // Modal state
    const [isModalOpen, setIsModalOpen] = useState(false)
    const [editingBus, setEditingBus] = useState<Bus | null>(null)
    const [formData, setFormData] = useState<Partial<Bus>>({})

    const handleSearch = (e: React.ChangeEvent<HTMLInputElement>) => {
        setSearch(e.target.value)
        setPage(1)
    }

    const { data: busesData, isLoading: isBusesLoading } = useQuery({
        queryKey: ['buses', page, search],
        queryFn: () => getBuses({ page, search: search || undefined }),
    })

    const { data: officesData } = useQuery({
        queryKey: ['offices_list'],
        queryFn: () => getOffices({ limit: 100 }),
    })

    const invalidateBuses = () => {
        queryClient.invalidateQueries({ queryKey: ['buses'] })
        queryClient.invalidateQueries({ queryKey: ['admin_buses_count'] })
    }

    const busMutation = useMutation({
        mutationFn: (data: Partial<Bus>) => {
            if (editingBus) return updateBus(editingBus.id, data)
            return createBus(data)
        },
        onSuccess: () => {
            setIsModalOpen(false)
            setEditingBus(null)
            setFormData({})
            invalidateBuses()
        }
    })

    const unbindMutation = useMutation({
        mutationFn: ({ busId, isActive }: { busId: number, isActive: boolean }) => updateBus(busId, { is_active: isActive }),
        onSuccess: () => invalidateBuses(),
    })

    const openCreateModal = () => {
        setEditingBus(null)
        setFormData({ is_active: true, capacity: 55 })
        setIsModalOpen(true)
    }

    const openEditModal = (bus: Bus) => {
        setEditingBus(bus)
        setFormData({ ...bus })
        setIsModalOpen(true)
    }

    const columns = [
        columnHelper.accessor('plate_number', {
            header: 'Immatriculation',
            cell: info => <span className="font-mono font-medium text-text-primary">{info.getValue()}</span>,
        }),
        columnHelper.accessor('model', {
            header: 'Modèle',
            cell: info => info.getValue() || '-',
        }),
        columnHelper.accessor('capacity', {
            header: 'Capacité',
            cell: info => (
                <span className="flex items-center gap-1.5 text-sm text-text-primary">
                    <Users className="w-3.5 h-3.5 text-text-muted" /> {info.getValue()} places
                </span>
            ),
        }),
        columnHelper.accessor('office_name', {
            header: 'Agence d\'attachement',
            cell: info => info.getValue() || <span className="text-text-muted italic">Non assigné</span>,
        }),
        columnHelper.accessor('is_active', {
            header: 'Statut',
            cell: info => (
                <span className={`px-2 py-0.5 rounded text-xs font-medium border ${info.getValue() ? 'bg-status-success/10 text-status-success border-status-success/20' : 'bg-status-error/10 text-status-error border-status-error/20'}`}>
                    {info.getValue() ? 'En service' : 'Hors service'}
                </span>
            ),
        }),
        columnHelper.display({
            id: 'actions',
            header: 'Actions',
            cell: info => {
                const bus = info.row.original
                return (
                    <div className="flex items-center gap-2">
                        <Button variant="ghost" className="p-1.5 h-auto text-brand-400 hover:bg-brand-500/10" onClick={() => openEditModal(bus)} title="Modifier">
                            <Edit className="w-4 h-4" />
                        </Button>
                        <Button
                            variant={bus.is_active ? 'danger' : 'primary'}
                            className="p-1.5 h-auto"
                            onClick={() => {
                                if (confirm(bus.is_active ? "Mettre ce bus hors service ?" : "Remettre ce bus en service ?")) {
                                    unbindMutation.mutate({ busId: bus.id, isActive: !bus.is_active })
                                }
                            }}
                            title={bus.is_active ? "Désactiver" : "Activer"}
                            isLoading={unbindMutation.isPending && unbindMutation.variables?.busId === bus.id}
                        >
                            <Power className="w-4 h-4" />
                        </Button>
                    </div>
                )
            },
        }),
    ]

    return (
        <div className="space-y-6 animate-fade-in">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <div>
                    <h1 className="text-2xl font-bold text-text-primary">Flotte de Bus</h1>
                    <p className="text-sm text-text-muted mt-1">Gérez l'inventaire des bus et leur affectation aux agences.</p>
                </div>
                <Button onClick={openCreateModal} className="shrink-0 flex items-center gap-2">
                    <Plus className="w-4 h-4" />
                    Ajouter un Bus
                </Button>
            </div>

            <div className="bg-surface-800 border border-surface-600/50 p-4 rounded-xl flex flex-col md:flex-row gap-4">
                <div className="flex-1 relative">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-text-muted" />
                    <input
                        type="text"
                        placeholder="Rechercher (Immatriculation, Modèle)..."
                        value={search}
                        onChange={handleSearch}
                        className="w-full bg-surface-700 border border-surface-600/50 rounded-lg pl-10 pr-3 py-2 text-sm text-text-primary focus:outline-none focus:ring-2 focus:ring-brand-500/50"
                    />
                </div>
            </div>

            <div className="bg-surface-800 border border-surface-600/50 rounded-xl overflow-hidden min-h-[400px]">
                <DataTable
                    data={busesData?.results || []}
                    columns={columns}
                    isLoading={isBusesLoading}
                    pageCount={busesData ? Math.ceil(busesData.count / 10) : 1}
                    pageIndex={page - 1}
                    onPageChange={(newIndex) => setPage(newIndex + 1)}
                    emptyMessage="Aucun bus trouvé."
                />
            </div>

            <Modal
                isOpen={isModalOpen}
                onClose={() => !busMutation.isPending && setIsModalOpen(false)}
                title={editingBus ? "Modifier le bus" : "Nouveau bus"}
            >
                <form onSubmit={(e) => { e.preventDefault(); busMutation.mutate(formData) }} className="space-y-4">
                    {busMutation.error && (
                        <div className="bg-status-error/10 border border-status-error/30 text-status-error p-3 rounded-lg text-sm flex items-start gap-2">
                            <ShieldAlert className="w-5 h-5 shrink-0" />
                            <p>{(busMutation.error as any)?.response?.data?.detail || (busMutation.error as Error).message || "Une erreur est survenue."}</p>
                        </div>
                    )}

                    <div className="grid grid-cols-2 gap-4">
                        <div>
                            <label className="block text-sm font-medium text-text-primary mb-1">Immatriculation *</label>
                            <input
                                required
                                type="text"
                                placeholder="12345 124 16"
                                className="w-full bg-surface-700 border border-surface-600/50 rounded-lg px-3 py-2 text-text-primary font-mono focus:ring-2 focus:ring-brand-500"
                                value={formData.plate_number || ''}
                                onChange={(e) => setFormData(p => ({ ...p, plate_number: e.target.value }))}
                            />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-text-primary mb-1">Capacité *</label>
                            <input
                                required
                                type="number"
                                min="1"
                                className="w-full bg-surface-700 border border-surface-600/50 rounded-lg px-3 py-2 text-text-primary focus:ring-2 focus:ring-brand-500"
                                value={formData.capacity || ''}
                                onChange={(e) => setFormData(p => ({ ...p, capacity: Number(e.target.value) }))}
                            />
                        </div>
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-text-primary mb-1">Modèle de bus</label>
                        <input
                            type="text"
                            placeholder="Ex: Higer 55"
                            className="w-full bg-surface-700 border border-surface-600/50 rounded-lg px-3 py-2 text-text-primary focus:ring-2 focus:ring-brand-500"
                            value={formData.model || ''}
                            onChange={(e) => setFormData(p => ({ ...p, model: e.target.value }))}
                        />
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-text-primary mb-1">Agence d'attachement *</label>
                        <select
                            required
                            className="w-full bg-surface-700 border border-surface-600/50 rounded-lg px-3 py-2 text-text-primary focus:ring-2 focus:ring-brand-500"
                            value={formData.office || ''}
                            onChange={(e) => setFormData(p => ({ ...p, office: Number(e.target.value) }))}
                        >
                            <option value="">Sélectionnez une agence</option>
                            {officesData?.results?.map(o => (
                                <option key={o.id} value={o.id}>{o.name}</option>
                            ))}
                        </select>
                    </div>

                    <div className="flex justify-end gap-3 mt-6">
                        <Button type="button" variant="secondary" onClick={() => setIsModalOpen(false)}>Annuler</Button>
                        <Button type="submit" isLoading={busMutation.isPending}>{editingBus ? 'Enregistrer' : 'Créer'}</Button>
                    </div>
                </form>
            </Modal>
        </div>
    )
}
