import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getPricingConfigs, createPricing, updatePricing, getOffices } from '../../api/admin'
import { formatCurrency, formatDateTime } from '../../utils/formatters'
import { DataTable } from '../../components/ui/DataTable'
import { Button } from '../../components/ui/Button'
import { Modal } from '../../components/ui/Modal'
import { createColumnHelper } from '@tanstack/react-table'
import { Plus, ShieldAlert, Edit, ArrowRight, Power, CircleDollarSign } from 'lucide-react'
import type { PricingConfig } from '../../types/admin'

const columnHelper = createColumnHelper<any>()

export function PricingManagement() {
    const queryClient = useQueryClient()

    // Modal state
    const [isModalOpen, setIsModalOpen] = useState(false)
    const [editingPricing, setEditingPricing] = useState<PricingConfig | null>(null)
    const [formData, setFormData] = useState<Partial<PricingConfig>>({})

    const extractErrorMsg = (err: unknown): string => {
        if (!err) return "Une erreur est survenue."
        const erra = err as any
        if (erra.response?.data?.detail) return erra.response.data.detail

        if (erra.response?.data && typeof erra.response.data === 'object') {
            const values = Object.values(erra.response.data).flat()
            if (values.length > 0 && typeof values[0] === 'string') {
                return values.join(' | ')
            }
        }

        return erra.message || "Une erreur est survenue."
    }

    const { data: pricingData, isLoading: isPricingLoading } = useQuery({
        queryKey: ['pricing_configs'],
        queryFn: getPricingConfigs,
        retry: (failureCount, error: any) => failureCount < 3 && error?.response?.status !== 400 && error?.response?.status !== 401,
        retryDelay: (attempt) => Math.min(1000 * 2 ** attempt, 10000),
    })

    const { data: officesData } = useQuery({
        queryKey: ['offices_list'],
        queryFn: () => getOffices({ limit: 100 }),
    })

    const invalidatePricing = () => {
        queryClient.invalidateQueries({ queryKey: ['pricing_configs'] })
        queryClient.invalidateQueries({ queryKey: ['admin_pricing_count'] })
    }

    const pricingMutation = useMutation({
        mutationFn: (data: Partial<PricingConfig>) => {
            if (editingPricing) return updatePricing(editingPricing.id, data)
            return createPricing(data)
        },
        onSuccess: () => {
            setIsModalOpen(false)
            setEditingPricing(null)
            setFormData({})
            invalidatePricing()
        }
    })

    const unbindMutation = useMutation({
        mutationFn: ({ id, isActive }: { id: number, isActive: boolean }) => updatePricing(id, { is_active: isActive }),
        onSuccess: () => invalidatePricing(),
    })

    const openCreateModal = () => {
        setEditingPricing(null)
        setFormData({ is_active: true, passenger_price: 1000, cargo_small_price: 200, cargo_medium_price: 500, cargo_large_price: 1500, currency: 'DZD', effective_from: new Date().toISOString().split('T')[0] })
        setIsModalOpen(true)
    }

    const openEditModal = (pricing: PricingConfig) => {
        setEditingPricing(pricing)
        setFormData({ ...pricing })
        setIsModalOpen(true)
    }

    const columns = [
        columnHelper.accessor(row => row, {
            id: 'route',
            header: 'Trajet',
            cell: info => {
                const p = info.getValue()
                return (
                    <div className="flex items-center gap-2 text-sm font-medium text-slate-900 dark:text-slate-100">
                        <span>{p.origin_name}</span>
                        <ArrowRight className="w-3.5 h-3.5 text-slate-400 dark:text-slate-500" />
                        <span>{p.destination_name}</span>
                    </div>
                )
            },
        }),
        columnHelper.accessor('passenger_price', {
            header: 'Billet Passager',
            cell: info => <span className="font-semibold">{formatCurrency(info.getValue() || 0)}</span>,
        }),
        columnHelper.accessor(row => row, {
            id: 'cargo_prices',
            header: 'Tarifs Colis (S/M/L)',
            cell: info => {
                const p = info.getValue()
                return (
                    <div className="text-sm space-x-2 text-slate-600 dark:text-slate-400">
                        <span>{p.cargo_small_price}</span> /
                        <span>{p.cargo_medium_price}</span> /
                        <span>{p.cargo_large_price}</span>
                    </div>
                )
            },
        }),
        columnHelper.accessor('effective_from', {
            header: 'Applicable depuis',
            cell: info => <span className="text-sm">{formatDateTime(info.getValue())}</span>,
        }),
        columnHelper.accessor('is_active', {
            header: 'Statut',
            cell: info => (
                <span className={`px-2 py-0.5 rounded text-xs font-medium border ${info.getValue() ? 'bg-status-success/10 text-emerald-600 dark:text-emerald-400 border-status-success/20' : 'bg-red-50 dark:bg-red-900/20 text-red-600 dark:text-red-400 border-status-error/20'}`}>
                    {info.getValue() ? 'Actif' : 'Inactif'}
                </span>
            ),
        }),
        columnHelper.display({
            id: 'actions',
            header: 'Actions',
            cell: info => {
                const pricing = info.row.original
                return (
                    <div className="flex items-center gap-2">
                        <Button variant="ghost" className="p-1.5 h-auto text-[#137fec] dark:text-[#60a5fa] hover:bg-[#137fec]/10" onClick={() => openEditModal(pricing)} title="Modifier">
                            <Edit className="w-4 h-4" />
                        </Button>
                        <Button
                            variant={pricing.is_active ? 'danger' : 'primary'}
                            className="p-1.5 h-auto"
                            onClick={() => {
                                if (confirm(pricing.is_active ? "Désactiver ce tarif ?" : "Réactiver ce tarif ?")) {
                                    unbindMutation.mutate({ id: pricing.id, isActive: !pricing.is_active })
                                }
                            }}
                            title={pricing.is_active ? "Désactiver" : "Activer"}
                            isLoading={unbindMutation.isPending && unbindMutation.variables?.id === pricing.id}
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
                    <h1 className="text-2xl font-bold text-slate-900 dark:text-slate-100 flex items-center gap-2">
                        <CircleDollarSign className="w-6 h-6 text-[#137fec] dark:text-[#60a5fa]" />
                        Grille Tarifaire
                    </h1>
                    <p className="text-sm text-slate-400 dark:text-slate-500 mt-1">Paramétrez les prix des billets passagers et tarifs colis par trajet.</p>
                </div>
                <Button onClick={openCreateModal} className="shrink-0 flex items-center gap-2">
                    <Plus className="w-4 h-4" />
                    Nouveau Tarif
                </Button>
            </div>

            <div className="bg-white dark:bg-[#1a2634] border border-slate-200 dark:border-slate-800 rounded-xl overflow-hidden min-h-[400px]">
                <DataTable
                    data={pricingData?.results || []}
                    columns={columns}
                    isLoading={isPricingLoading}
                    pageCount={1}
                    pageIndex={0}
                    onPageChange={() => { }}
                    emptyMessage="Aucune tarification trouvée."
                />
            </div>

            <Modal
                isOpen={isModalOpen}
                onClose={() => !pricingMutation.isPending && setIsModalOpen(false)}
                title={editingPricing ? "Modifier le tarif" : "Nouveau tarif"}
            >
                <form onSubmit={(e) => { e.preventDefault(); pricingMutation.mutate(formData) }} className="space-y-4">
                    {pricingMutation.error && (
                        <div className="bg-red-50 dark:bg-red-900/20 border border-status-error/30 text-red-600 dark:text-red-400 p-3 rounded-lg text-sm flex items-start gap-2">
                            <ShieldAlert className="w-5 h-5 shrink-0" />
                            <p>{extractErrorMsg(pricingMutation.error)}</p>
                        </div>
                    )}

                    <div className="grid grid-cols-2 gap-4">
                        <div>
                            <label className="block text-sm font-medium text-slate-900 dark:text-slate-100 mb-1">Agence Départ *</label>
                            <select
                                required
                                className="w-full bg-slate-100 dark:bg-[#1e293b] border border-slate-200 dark:border-slate-800 rounded-lg px-3 py-2 text-slate-900 dark:text-slate-100 focus:ring-2 focus:ring-brand-500"
                                value={formData.origin_office || ''}
                                onChange={(e) => setFormData(p => ({ ...p, origin_office: Number(e.target.value) }))}
                            >
                                <option value="">Sélectionnez</option>
                                {officesData?.results?.map(o => (
                                    <option key={o.id} value={o.id}>{o.name}</option>
                                ))}
                            </select>
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-slate-900 dark:text-slate-100 mb-1">Agence Arrivée *</label>
                            <select
                                required
                                className="w-full bg-slate-100 dark:bg-[#1e293b] border border-slate-200 dark:border-slate-800 rounded-lg px-3 py-2 text-slate-900 dark:text-slate-100 focus:ring-2 focus:ring-brand-500"
                                value={formData.destination_office || ''}
                                onChange={(e) => setFormData(p => ({ ...p, destination_office: Number(e.target.value) }))}
                            >
                                <option value="">Sélectionnez</option>
                                {officesData?.results?.map(o => (
                                    <option key={o.id} value={o.id}>{o.name}</option>
                                ))}
                            </select>
                        </div>
                    </div>

                    <div className="pt-4 border-t border-slate-200 dark:border-slate-800">
                        <label className="block text-sm font-medium text-slate-900 dark:text-slate-100 mb-3">Date d'application *</label>
                        <input
                            required
                            type="date"
                            className="w-full bg-slate-100 dark:bg-[#1e293b] border border-slate-200 dark:border-slate-800 rounded-lg px-3 py-2 text-slate-900 dark:text-slate-100 focus:ring-2 focus:ring-brand-500 [color-scheme:dark]"
                            value={formData.effective_from || ''}
                            onChange={(e) => setFormData(p => ({ ...p, effective_from: e.target.value }))}
                        />
                    </div>

                    <div className="pt-4 border-t border-slate-200 dark:border-slate-800">
                        <label className="block text-sm font-medium text-slate-900 dark:text-slate-100 mb-3">Prix Billet Passager * (DZD)</label>
                        <input
                            required
                            type="number"
                            min="0"
                            className="w-full bg-slate-100 dark:bg-[#1e293b] border border-slate-200 dark:border-slate-800 rounded-lg px-3 py-2 text-slate-900 dark:text-slate-100 focus:ring-2 focus:ring-brand-500"
                            value={formData.passenger_price || ''}
                            onChange={(e) => setFormData(p => ({ ...p, passenger_price: Number(e.target.value) }))}
                        />
                    </div>

                    <div className="pt-4 border-t border-slate-200 dark:border-slate-800">
                        <label className="block text-sm font-medium text-slate-900 dark:text-slate-100 mb-3">Tarifs Colis (DZD)</label>
                        <div className="grid grid-cols-3 gap-4">
                            <div>
                                <label className="block text-xs text-slate-400 dark:text-slate-500 mb-1">Petit</label>
                                <input
                                    required
                                    type="number"
                                    min="0"
                                    className="w-full bg-slate-100 dark:bg-[#1e293b] border border-slate-200 dark:border-slate-800 rounded-lg px-3 py-2 text-slate-900 dark:text-slate-100 focus:ring-2 focus:ring-brand-500"
                                    value={formData.cargo_small_price || ''}
                                    onChange={(e) => setFormData(p => ({ ...p, cargo_small_price: Number(e.target.value) }))}
                                />
                            </div>
                            <div>
                                <label className="block text-xs text-slate-400 dark:text-slate-500 mb-1">Moyen</label>
                                <input
                                    required
                                    type="number"
                                    min="0"
                                    className="w-full bg-slate-100 dark:bg-[#1e293b] border border-slate-200 dark:border-slate-800 rounded-lg px-3 py-2 text-slate-900 dark:text-slate-100 focus:ring-2 focus:ring-brand-500"
                                    value={formData.cargo_medium_price || ''}
                                    onChange={(e) => setFormData(p => ({ ...p, cargo_medium_price: Number(e.target.value) }))}
                                />
                            </div>
                            <div>
                                <label className="block text-xs text-slate-400 dark:text-slate-500 mb-1">Grand</label>
                                <input
                                    required
                                    type="number"
                                    min="0"
                                    className="w-full bg-slate-100 dark:bg-[#1e293b] border border-slate-200 dark:border-slate-800 rounded-lg px-3 py-2 text-slate-900 dark:text-slate-100 focus:ring-2 focus:ring-brand-500"
                                    value={formData.cargo_large_price || ''}
                                    onChange={(e) => setFormData(p => ({ ...p, cargo_large_price: Number(e.target.value) }))}
                                />
                            </div>
                        </div>
                    </div>

                    <div className="flex justify-end gap-3 mt-6">
                        <Button type="button" variant="secondary" onClick={() => setIsModalOpen(false)}>Annuler</Button>
                        <Button type="submit" isLoading={pricingMutation.isPending}>{editingPricing ? 'Enregistrer' : 'Créer'}</Button>
                    </div>
                </form>
            </Modal>
        </div>
    )
}
