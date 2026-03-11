import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { getUsers, getBuses, getOffices, getPricingConfigs } from '../../api/admin'
import { Users, Bus, Building2, CircleDollarSign, ShieldAlert, Activity } from 'lucide-react'

export function AdminDashboard() {
    const { data: users } = useQuery({
        queryKey: ['admin_users_count'],
        queryFn: () => getUsers({ limit: 1 }),
    })

    const { data: buses } = useQuery({
        queryKey: ['admin_buses_count'],
        queryFn: () => getBuses({ limit: 1 }),
    })

    const { data: offices } = useQuery({
        queryKey: ['admin_offices_count'],
        queryFn: () => getOffices({ limit: 1 }),
    })

    const { data: pricing } = useQuery({
        queryKey: ['admin_pricing_count'],
        queryFn: () => getPricingConfigs(),
    })

    const StatCard = ({ title, value, icon, to, description }: { title: string, value: string | number, icon: React.ReactNode, to: string, description: string }) => (
        <Link to={to} className="block group">
            <div className="bg-surface-800 border border-surface-600/50 rounded-xl p-6 h-full transition-all duration-200 hover:border-brand-500/50 hover:bg-surface-700/50">
                <div className="flex items-start justify-between mb-4">
                    <div className="p-3 bg-surface-700/50 rounded-xl text-brand-400 group-hover:scale-110 group-hover:bg-brand-500/10 group-hover:text-brand-300 transition-all">
                        {icon}
                    </div>
                </div>
                <div>
                    <h3 className="text-2xl font-bold text-text-primary mb-1">{value}</h3>
                    <p className="text-base font-medium text-text-primary mb-1">{title}</p>
                    <p className="text-sm text-text-muted">{description}</p>
                </div>
            </div>
        </Link>
    )

    return (
        <div className="space-y-6 animate-fade-in">
            <div>
                <h1 className="text-2xl font-bold text-text-primary">Panneau d'Administration</h1>
                <p className="text-sm text-text-muted mt-1">Supervision globale de l'entreprise SOUIGAT.</p>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                <StatCard
                    title="Employés & Utilisateurs"
                    value={users?.count ?? '...'}
                    icon={<Users className="w-6 h-6" />}
                    to="/admin/users"
                    description="Gérer les accès, révoquer les appareils."
                />
                <StatCard
                    title="Flotte de Bus"
                    value={buses?.count ?? '...'}
                    icon={<Bus className="w-6 h-6" />}
                    to="/admin/buses"
                    description="Ajouter, modifier ou assigner les bus."
                />
                <StatCard
                    title="Agences"
                    value={offices?.count ?? '...'}
                    icon={<Building2 className="w-6 h-6" />}
                    to="/admin/offices"
                    description="Paramétrage des bureaux."
                />
                <StatCard
                    title="Tarifications"
                    value={pricing?.count ?? '...'}
                    icon={<CircleDollarSign className="w-6 h-6" />}
                    to="/admin/pricing"
                    description="Prix par type de trajet et colis."
                />
                <StatCard
                    title="Journal d'Audit"
                    value="Sécurité"
                    icon={<Activity className="w-6 h-6" />}
                    to="/admin/audit"
                    description="Traces des modifications critiques."
                />
                <StatCard
                    title="Quarantaine"
                    value="Sync"
                    icon={<ShieldAlert className="w-6 h-6 text-status-warning" />}
                    to="/admin/quarantine"
                    description="Vérifier les données bloquées (T28)."
                />
            </div>
        </div>
    )
}
