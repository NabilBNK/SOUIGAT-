import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getOffices, createOffice, updateOffice } from '../../api/admin'
import { DataTable } from '../../components/ui/DataTable'
import { Button } from '../../components/ui/Button'
import { Modal } from '../../components/ui/Modal'
import { createColumnHelper } from '@tanstack/react-table'
import { Plus, ShieldAlert, Edit, Power, Building2, MapPin, Search } from 'lucide-react'
import type { Office } from '../../types/admin'

const columnHelper = createColumnHelper<any>()

export function OfficeManagement() {
    const queryClient = useQueryClient()
    const [page, setPage] = useState(1)
    const [search, setSearch] = useState('')

    // Modal state
    const [isModalOpen, setIsModalOpen] = useState(false)
    const [editingOffice, setEditingOffice] = useState<Office | null>(null)
    const [formData, setFormData] = useState<Partial<Office>>({})

    const handleSearch = (e: React.ChangeEvent<HTMLInputElement>) => {
        setSearch(e.target.value)
        setPage(1)
    }

    const { data: officesData, isLoading: isOfficesLoading } = useQuery({
        queryKey: ['offices', page, search],
        queryFn: () => getOffices({ page, search: search || undefined }),
    })

    const invalidateOffices = () => {
        queryClient.invalidateQueries({ queryKey: ['offices'] })
        queryClient.invalidateQueries({ queryKey: ['offices_list'] })
        queryClient.invalidateQueries({ queryKey: ['admin_offices_count'] })
    }

    const officeMutation = useMutation({
        mutationFn: (data: Partial<Office>) => {
            if (editingOffice) return updateOffice(editingOffice.id, data)
            return createOffice(data)
        },
        onSuccess: () => {
            setIsModalOpen(false)
            setEditingOffice(null)
            setFormData({})
            invalidateOffices()
        }
    })

    const unbindMutation = useMutation({
        mutationFn: ({ id, isActive }: { id: number, isActive: boolean }) => updateOffice(id, { is_active: isActive }),
        onSuccess: () => invalidateOffices(),
    })

    const openCreateModal = () => {
        setEditingOffice(null)
        setFormData({ is_active: true })
        setIsModalOpen(true)
    }

    const openEditModal = (office: Office) => {
        setEditingOffice(office)
        setFormData({ ...office })
        setIsModalOpen(true)
    }

    const columns = [
        columnHelper.accessor('name', {
            header: 'Nom de l\'agence',
            cell: info => <span className="font-semibold text-text-primary">{info.getValue()}</span>,
        }),
        columnHelper.accessor('city', {
            header: 'Ville',
            cell: info => info.getValue(),
        }),
        columnHelper.accessor('address', {
            header: 'Adresse',
            cell: info => (
                <span className="flex items-center gap-1.5 text-sm text-text-primary">
                    <MapPin className="w-3.5 h-3.5 text-text-muted" />
                    {info.getValue() || <span className="text-text-muted italic">Non spécifiée</span>}
                </span>
            ),
        }),
        columnHelper.accessor('phone', {
            header: 'Téléphone',
            cell: info => info.getValue() || '-',
        }),
        columnHelper.accessor('is_active', {
            header: 'Statut',
            cell: info => (
                <span className={`px-2 py-0.5 rounded text-xs font-medium border ${info.getValue() ? 'bg-status-success/10 text-emerald-400 border-status-success/20' : 'bg-red-500/10 bg-red-500/10 text-red-600 dark:text-red-400 border-status-error/20'}`}>
                    {info.getValue() ? 'Active' : 'Fermée'}
                </span>
            ),
        }),
        columnHelper.display({
            id: 'actions',
            header: 'Actions',
            cell: info => {
                const office = info.row.original
                return (
                    <div className="flex items-center gap-2">
                        <Button variant="ghost" className="p-1.5 h-auto text-brand-400 hover:bg-[#137fec]/10" onClick={() => openEditModal(office)} title="Modifier">
                            <Edit className="w-4 h-4" />
                        </Button>
                        <Button
                            variant={office.is_active ? 'danger' : 'primary'}
                            className="p-1.5 h-auto"
                            onClick={() => {
                                if (confirm(office.is_active ? "Désactiver cette agence ?" : "Réactiver cette agence ?")) {
                                    unbindMutation.mutate({ id: office.id, isActive: !office.is_active })
                                }
                            }}
                            title={office.is_active ? "Fermer (Désactiver)" : "Ouvrir (Activer)"}
                            isLoading={unbindMutation.isPending && unbindMutation.variables?.id === office.id}
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
                    <h1 className="text-2xl font-bold text-text-primary flex items-center gap-2">
                        <Building2 className="w-6 h-6 text-brand-400" />
                        Agences
                    </h1>
                    <p className="text-sm text-text-muted mt-1">Gérez vos bureaux et points de vente.</p>
                </div>
                <Button onClick={openCreateModal} className="shrink-0 flex items-center gap-2">
                    <Plus className="w-4 h-4" />
                    Ajouter une Agence
                </Button>
            </div>

            <div className="bg-surface-800/80 backdrop-blur-md border border-surface-700 p-4 rounded-xl flex flex-col md:flex-row gap-4">
                <div className="flex-1 relative">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-text-muted" />
                    <input
                        type="text"
                        placeholder="Rechercher (Nom, Ville)..."
                        value={search}
                        onChange={handleSearch}
                        className="w-full bg-surface-900 border border-surface-700 rounded-lg pl-10 pr-3 py-2 text-sm text-text-primary focus:outline-none focus:ring-2 focus:ring-brand-500"
                    />
                </div>
            </div>

            <div className="bg-surface-800/80 backdrop-blur-md border border-surface-700 rounded-xl overflow-hidden min-h-[400px]">
                <DataTable
                    data={officesData?.results || []}
                    columns={columns}
                    isLoading={isOfficesLoading}
                    pageCount={officesData ? Math.ceil(officesData.count / 10) : 1}
                    pageIndex={page - 1}
                    onPageChange={(newIndex) => setPage(newIndex + 1)}
                    emptyMessage="Aucune agence trouvée."
                />
            </div>

            <Modal
                isOpen={isModalOpen}
                onClose={() => !officeMutation.isPending && setIsModalOpen(false)}
                title={editingOffice ? "Modifier l'agence" : "Nouvelle agence"}
            >
                <form onSubmit={(e) => { e.preventDefault(); officeMutation.mutate(formData) }} className="space-y-4">
                    {officeMutation.error && (
                        <div className="bg-red-500/10 bg-red-500/10 border border-status-error/30 text-red-600 dark:text-red-400 p-3 rounded-lg text-sm flex items-start gap-2">
                            <ShieldAlert className="w-5 h-5 shrink-0" />
                            <p>{(officeMutation.error as any)?.response?.data?.detail || (officeMutation.error as Error).message || "Une erreur est survenue."}</p>
                        </div>
                    )}

                    <div className="grid grid-cols-2 gap-4">
                        <div>
                            <label className="block text-sm font-medium text-text-primary mb-1">Nom du point de vente *</label>
                            <input
                                required
                                type="text"
                                placeholder="Ex: Agence Principale Alger"
                                className="w-full bg-surface-900 border border-surface-700 rounded-lg px-3 py-2 text-text-primary focus:ring-2 focus:ring-brand-500"
                                value={formData.name || ''}
                                onChange={(e) => setFormData(p => ({ ...p, name: e.target.value }))}
                            />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-text-primary mb-1">Ville *</label>
                            <input
                                required
                                type="text"
                                className="w-full bg-surface-900 border border-surface-700 rounded-lg px-3 py-2 text-text-primary focus:ring-2 focus:ring-brand-500"
                                value={formData.city || ''}
                                onChange={(e) => setFormData(p => ({ ...p, city: e.target.value }))}
                            />
                        </div>
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-text-primary mb-1">Adresse</label>
                        <input
                            type="text"
                            placeholder="Adresse complète"
                            className="w-full bg-surface-900 border border-surface-700 rounded-lg px-3 py-2 text-text-primary focus:ring-2 focus:ring-brand-500"
                            value={formData.address || ''}
                            onChange={(e) => setFormData(p => ({ ...p, address: e.target.value }))}
                        />
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-text-primary mb-1">Téléphone de l'agence</label>
                        <input
                            type="text"
                            placeholder="0555..."
                            className="w-full bg-surface-900 border border-surface-700 rounded-lg px-3 py-2 text-text-primary focus:ring-2 focus:ring-brand-500"
                            value={formData.phone || ''}
                            onChange={(e) => setFormData(p => ({ ...p, phone: e.target.value }))}
                        />
                    </div>

                    <div className="flex justify-end gap-3 mt-6">
                        <Button type="button" variant="secondary" onClick={() => setIsModalOpen(false)}>Annuler</Button>
                        <Button type="submit" isLoading={officeMutation.isPending}>{editingOffice ? 'Enregistrer' : 'Créer'}</Button>
                    </div>
                </form>
            </Modal>
        </div>
    )
}
