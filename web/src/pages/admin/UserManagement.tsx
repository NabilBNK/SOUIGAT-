import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getUsers, createUser, updateUser, revokeDevice, getOffices } from '../../api/admin'
import { DataTable } from '../../components/ui/DataTable'
import { Button } from '../../components/ui/Button'
import { Modal } from '../../components/ui/Modal'
import { createColumnHelper } from '@tanstack/react-table'
import { Search, Plus, ShieldAlert, Edit, Smartphone, Power } from 'lucide-react'
import type { User, Role, Department } from '../../types/auth'

const columnHelper = createColumnHelper<any>()

export function UserManagement() {
    const queryClient = useQueryClient()
    const [page, setPage] = useState(1)
    const [search, setSearch] = useState('')

    // Modal state
    const [isModalOpen, setIsModalOpen] = useState(false)
    const [editingUser, setEditingUser] = useState<User | null>(null)
    const [formData, setFormData] = useState<Partial<User> & { password?: string }>({})

    const handleSearch = (e: React.ChangeEvent<HTMLInputElement>) => {
        setSearch(e.target.value)
        setPage(1)
    }

    const { data: usersData, isLoading: isUsersLoading } = useQuery({
        queryKey: ['users', page, search],
        queryFn: () => getUsers({ page, search: search || undefined }),
    })

    const { data: officesData } = useQuery({
        queryKey: ['offices_list'],
        queryFn: () => getOffices({ limit: 100 }),
    })

    const invalidateUsers = () => {
        queryClient.invalidateQueries({ queryKey: ['users'] })
        queryClient.invalidateQueries({ queryKey: ['admin_users_count'] })
    }

    const userMutation = useMutation({
        mutationFn: (data: Partial<User> & { password?: string }) => {
            // Strip null/empty/undefined values to avoid DRF validation errors
            const cleaned = Object.fromEntries(
                Object.entries(data).filter(([, v]) => v !== null && v !== undefined && v !== '')
            )
            if (editingUser) return updateUser(editingUser.id, cleaned)
            if (!cleaned.password) throw new Error("Le mot de passe est requis.")
            return createUser(cleaned as Partial<User> & { password: string })
        },
        onSuccess: () => {
            setIsModalOpen(false)
            setEditingUser(null)
            setFormData({})
            invalidateUsers()
        }
    })

    const revokeMutation = useMutation({
        mutationFn: (userId: number) => revokeDevice(userId),
        onSuccess: () => invalidateUsers(),
    })

    const unbindMutation = useMutation({
        mutationFn: ({ userId, isActive }: { userId: number, isActive: boolean }) => updateUser(userId, { is_active: isActive }),
        onSuccess: () => invalidateUsers(),
    })

    const openCreateModal = () => {
        setEditingUser(null)
        setFormData({ role: 'office_staff', is_active: true, department: null, office: null })
        setIsModalOpen(true)
    }

    const openEditModal = (user: User) => {
        setEditingUser(user)
        setFormData({ ...user, password: '' })
        setIsModalOpen(true)
    }

    const columns = [
        columnHelper.accessor(row => `${row.first_name} ${row.last_name}`, {
            id: 'name',
            header: 'Nom',
            cell: info => <span className="font-medium text-text-primary capitalize">{info.getValue()}</span>,
        }),
        columnHelper.accessor('phone', {
            header: 'Téléphone',
            cell: info => <span className="font-mono text-sm">{info.getValue()}</span>,
        }),
        columnHelper.accessor('role', {
            header: 'Rôle',
            cell: info => (
                <span className="capitalize px-2 py-0.5 rounded text-xs font-medium bg-surface-700/50 text-brand-400 border border-brand-500/20">
                    {info.getValue().replace('_', ' ')}
                </span>
            ),
        }),
        columnHelper.accessor('office_name', {
            header: 'Agence',
            cell: info => info.getValue() || '-',
        }),
        columnHelper.accessor('device_id', {
            header: 'Appareil',
            cell: info => info.getValue() ? (
                <span className="flex items-center gap-1.5 text-xs text-emerald-400">
                    <Smartphone className="w-3.5 h-3.5" /> Lié
                </span>
            ) : (
                <span className="text-xs text-text-muted">Aucun</span>
            ),
        }),
        columnHelper.accessor('is_active', {
            header: 'Statut',
            cell: info => (
                <span className={`px-2 py-0.5 rounded text-xs font-medium border ${info.getValue() ? 'bg-status-success/10 text-emerald-400 border-status-success/20' : 'bg-red-500/10 bg-red-500/10 text-red-600 dark:text-red-400 border-status-error/20'}`}>
                    {info.getValue() ? 'Actif' : 'Désactivé'}
                </span>
            ),
        }),
        columnHelper.display({
            id: 'actions',
            header: 'Actions',
            cell: info => {
                const user = info.row.original
                return (
                    <div className="flex items-center gap-2">
                        <Button variant="ghost" className="p-1.5 h-auto text-brand-400 hover:bg-[#137fec]/10" onClick={() => openEditModal(user)} title="Modifier">
                            <Edit className="w-4 h-4" />
                        </Button>
                        {user.device_id && (
                            <Button
                                variant="danger"
                                className="p-1.5 h-auto"
                                onClick={() => {
                                    if (confirm("Révoquer l'appareil ? Le conducteur devra se reconnecter et toutes ses données non synchronisées seront perdues (ou mise en quarantaine).")) {
                                        revokeMutation.mutate(user.id)
                                    }
                                }}
                                title="Révoquer Appareil"
                                isLoading={revokeMutation.isPending && revokeMutation.variables === user.id}
                            >
                                <Smartphone className="w-4 h-4" />
                            </Button>
                        )}
                        <Button
                            variant={user.is_active ? 'danger' : 'primary'}
                            className="p-1.5 h-auto"
                            onClick={() => {
                                if (confirm(user.is_active ? "Désactiver cet utilisateur ?" : "Réactiver cet utilisateur ?")) {
                                    unbindMutation.mutate({ userId: user.id, isActive: !user.is_active })
                                }
                            }}
                            title={user.is_active ? "Désactiver" : "Activer"}
                            isLoading={unbindMutation.isPending && unbindMutation.variables?.userId === user.id}
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
                    <h1 className="text-2xl font-bold text-text-primary">Employés & Utilisateurs</h1>
                    <p className="text-sm text-text-muted mt-1">Gérez les accès, les agences d'attachement, et les appareils.</p>
                </div>
                <Button onClick={openCreateModal} className="shrink-0 flex items-center gap-2">
                    <Plus className="w-4 h-4" />
                    Créer un employé
                </Button>
            </div>

            <div className="bg-surface-800/80 backdrop-blur-md border border-surface-700 p-4 rounded-xl flex flex-col md:flex-row gap-4">
                <div className="flex-1 relative">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-text-muted" />
                    <input
                        type="text"
                        placeholder="Rechercher (Nom, Téléphone)..."
                        value={search}
                        onChange={handleSearch}
                        className="w-full bg-surface-900 border border-surface-700 rounded-lg pl-10 pr-3 py-2 text-sm text-text-primary focus:outline-none focus:ring-2 focus:ring-brand-500"
                    />
                </div>
            </div>

            <div className="bg-surface-800/80 backdrop-blur-md border border-surface-700 rounded-xl overflow-hidden min-h-[400px]">
                <DataTable
                    data={usersData?.results || []}
                    columns={columns}
                    isLoading={isUsersLoading}
                    pageCount={usersData ? Math.ceil(usersData.count / 10) : 1}
                    pageIndex={page - 1}
                    onPageChange={(newIndex) => setPage(newIndex + 1)}
                    emptyMessage="Aucun utilisateur trouvé."
                />
            </div>

            <Modal
                isOpen={isModalOpen}
                onClose={() => !userMutation.isPending && setIsModalOpen(false)}
                title={editingUser ? "Modifier l'utilisateur" : "Nouvel utilisateur"}
            >
                <form onSubmit={(e) => { e.preventDefault(); userMutation.mutate(formData) }} className="space-y-4">
                    {userMutation.error && (
                        <div className="bg-red-500/10 bg-red-500/10 border border-status-error/30 text-red-600 dark:text-red-400 p-3 rounded-lg text-sm flex items-start gap-2">
                            <ShieldAlert className="w-5 h-5 shrink-0" />
                            <p>{(userMutation.error as any)?.response?.data?.detail || (userMutation.error as Error).message || "Une erreur est survenue."}</p>
                        </div>
                    )}

                    <div className="grid grid-cols-2 gap-4">
                        <div>
                            <label className="block text-sm font-medium text-text-primary mb-1">Prénom *</label>
                            <input
                                required
                                type="text"
                                className="w-full bg-surface-900 border border-surface-700 rounded-lg px-3 py-2 text-text-primary focus:ring-2 focus:ring-brand-500"
                                value={formData.first_name || ''}
                                onChange={(e) => setFormData(p => ({ ...p, first_name: e.target.value }))}
                            />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-text-primary mb-1">Nom *</label>
                            <input
                                required
                                type="text"
                                className="w-full bg-surface-900 border border-surface-700 rounded-lg px-3 py-2 text-text-primary focus:ring-2 focus:ring-brand-500"
                                value={formData.last_name || ''}
                                onChange={(e) => setFormData(p => ({ ...p, last_name: e.target.value }))}
                            />
                        </div>
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-text-primary mb-1">Téléphone *</label>
                        <input
                            required
                            type="text"
                            placeholder="Ex: 0555001122"
                            className="w-full bg-surface-900 border border-surface-700 rounded-lg px-3 py-2 text-text-primary focus:ring-2 focus:ring-brand-500"
                            value={formData.phone || ''}
                            onChange={(e) => setFormData(p => ({ ...p, phone: e.target.value }))}
                        />
                    </div>

                    {!editingUser && (
                        <div>
                            <label className="block text-sm font-medium text-text-primary mb-1">Mot de passe *</label>
                            <input
                                required
                                type="password"
                                autoComplete="new-password"
                                className="w-full bg-surface-900 border border-surface-700 rounded-lg px-3 py-2 text-text-primary focus:ring-2 focus:ring-brand-500"
                                value={formData.password || ''}
                                onChange={(e) => setFormData(p => ({ ...p, password: e.target.value }))}
                            />
                        </div>
                    )}

                    <div className="grid grid-cols-2 gap-4">
                        <div>
                            <label className="block text-sm font-medium text-text-primary mb-1">Rôle *</label>
                            <select
                                required
                                className="w-full bg-surface-900 border border-surface-700 rounded-lg px-3 py-2 text-text-primary focus:ring-2 focus:ring-brand-500"
                                value={formData.role || ''}
                                onChange={(e) => setFormData(p => ({ ...p, role: e.target.value as Role }))}
                            >
                                <option value="office_staff">Guichetier (Office Staff)</option>
                                <option value="conductor">Receveur (Conductor)</option>
                                <option value="driver">Chauffeur (Driver)</option>
                                <option value="admin">Administrateur</option>
                            </select>
                        </div>

                        {(formData.role === 'office_staff' || formData.role === 'conductor') && (
                            <div>
                                <label className="block text-sm font-medium text-text-primary mb-1">Agence (Optionnel)</label>
                                <select
                                    className="w-full bg-surface-900 border border-surface-700 rounded-lg px-3 py-2 text-text-primary focus:ring-2 focus:ring-brand-500"
                                    value={formData.office || ''}
                                    onChange={(e) => setFormData(p => ({ ...p, office: e.target.value ? Number(e.target.value) : null }))}
                                >
                                    <option value="">Aucune agence</option>
                                    {officesData?.results?.map(o => (
                                        <option key={o.id} value={o.id}>{o.name}</option>
                                    ))}
                                </select>
                            </div>
                        )}
                    </div>

                    {formData.role === 'office_staff' && (
                        <div>
                            <label className="block text-sm font-medium text-text-primary mb-1">Département (Guichetier)</label>
                            <select
                                className="w-full bg-surface-900 border border-surface-700 rounded-lg px-3 py-2 text-text-primary focus:ring-2 focus:ring-brand-500"
                                value={formData.department || ''}
                                onChange={(e) => setFormData(p => ({ ...p, department: e.target.value ? e.target.value as Department : null }))}
                            >
                                <option value="">Tous les départements</option>
                                <option value="passenger">Passagers (Guichet)</option>
                                <option value="cargo">Colis (Messagerie)</option>
                            </select>
                        </div>
                    )}

                    {editingUser && (
                        <div className="mt-4 pt-4 border-t border-surface-700">
                            <label className="block text-sm font-medium text-text-primary mb-1">Changer le mot de passe</label>
                            <input
                                type="password"
                                autoComplete="new-password"
                                placeholder="Laisser vide pour ne pas changer"
                                className="w-full bg-surface-900 border border-surface-700 rounded-lg px-3 py-2 text-text-primary focus:ring-2 focus:ring-brand-500"
                                value={formData.password || ''}
                                onChange={(e) => setFormData(p => ({ ...p, password: e.target.value }))}
                            />
                        </div>
                    )}

                    <div className="flex justify-end gap-3 mt-6">
                        <Button type="button" variant="secondary" onClick={() => setIsModalOpen(false)}>Annuler</Button>
                        <Button type="submit" isLoading={userMutation.isPending}>{editingUser ? 'Enregistrer' : 'Créer'}</Button>
                    </div>
                </form>
            </Modal>
        </div>
    )
}
