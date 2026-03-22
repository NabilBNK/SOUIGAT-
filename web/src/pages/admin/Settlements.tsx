import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getOffices, getUsers } from '../../api/admin'
import { getSettlements, resolveSettlement } from '../../api/settlements'
import { Button } from '../../components/ui/Button'
import { Modal } from '../../components/ui/Modal'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { formatCurrency, formatDateTime } from '../../utils/formatters'

export function SettlementsPage() {
    const queryClient = useQueryClient()
    const [officeId, setOfficeId] = useState('all')
    const [conductorId, setConductorId] = useState('all')
    const [status, setStatus] = useState('all')
    const [dateFrom, setDateFrom] = useState('')
    const [dateTo, setDateTo] = useState('')
    const [selectedSettlement, setSelectedSettlement] = useState<any | null>(null)
    const [resolveForm, setResolveForm] = useState({
        actual_cash_received: '',
        actual_expenses_reimbursed: '',
        notes: '',
    })

    const filters = useMemo(() => ({
        office_id: officeId !== 'all' ? Number(officeId) : undefined,
        conductor_id: conductorId !== 'all' ? Number(conductorId) : undefined,
        status: status !== 'all' ? status : undefined,
        date_from: dateFrom || undefined,
        date_to: dateTo || undefined,
        page_size: 50,
    }), [conductorId, dateFrom, dateTo, officeId, status])

    const { data: officesData } = useQuery({
        queryKey: ['offices', 'settlement-filters'],
        queryFn: () => getOffices({ limit: 100 }),
    })
    const { data: conductorsData } = useQuery({
        queryKey: ['users', 'conductors', 'settlement-filters'],
        queryFn: () => getUsers({ role: 'conductor', page_size: 100 }),
    })
    const { data: settlementsData, isLoading } = useQuery({
        queryKey: ['settlements', filters],
        queryFn: () => getSettlements(filters),
    })

    const resolveMutation = useMutation({
        mutationFn: (payload: { tripId: number; actual_cash_received?: number; actual_expenses_reimbursed?: number; notes: string }) =>
            resolveSettlement(payload.tripId, {
                actual_cash_received: payload.actual_cash_received,
                actual_expenses_reimbursed: payload.actual_expenses_reimbursed,
                notes: payload.notes,
            }),
        onSuccess: () => {
            setSelectedSettlement(null)
            queryClient.invalidateQueries({ queryKey: ['settlements'] })
            queryClient.invalidateQueries({ queryKey: ['settlement'] })
            queryClient.invalidateQueries({ queryKey: ['pending-settlements'] })
        },
    })

    const settlements = settlementsData?.results || []
    const offices = officesData?.results || []
    const conductors = conductorsData?.results || []

    const openResolveModal = (settlement: any) => {
        setSelectedSettlement(settlement)
        setResolveForm({
            actual_cash_received: settlement.actual_cash_received?.toString() || settlement.expected_total_cash.toString(),
            actual_expenses_reimbursed: settlement.actual_expenses_reimbursed?.toString() || settlement.expenses_to_reimburse.toString(),
            notes: '',
        })
    }

    const handleResolveSubmit = (event: React.FormEvent) => {
        event.preventDefault()
        if (!selectedSettlement) return

        resolveMutation.mutate({
            tripId: selectedSettlement.trip,
            actual_cash_received: resolveForm.actual_cash_received === '' ? undefined : Number(resolveForm.actual_cash_received),
            actual_expenses_reimbursed: resolveForm.actual_expenses_reimbursed === '' ? undefined : Number(resolveForm.actual_expenses_reimbursed),
            notes: resolveForm.notes,
        })
    }

    return (
        <>
            <Modal
                isOpen={!!selectedSettlement}
                onClose={() => !resolveMutation.isPending && setSelectedSettlement(null)}
                title="Resoudre le litige"
            >
                <form onSubmit={handleResolveSubmit} className="space-y-4">
                    <div>
                        <label className="block text-sm font-medium text-slate-600 dark:text-slate-400 mb-1">
                            Especes recues (DA)
                        </label>
                        <input
                            type="number"
                            min={0}
                            value={resolveForm.actual_cash_received}
                            onChange={(event) => setResolveForm((current) => ({ ...current, actual_cash_received: event.target.value }))}
                            className="w-full bg-slate-100 dark:bg-[#1e293b] border border-slate-200 dark:border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-900 dark:text-slate-100"
                        />
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-slate-600 dark:text-slate-400 mb-1">
                            Depenses remboursees (DA)
                        </label>
                        <input
                            type="number"
                            min={0}
                            value={resolveForm.actual_expenses_reimbursed}
                            onChange={(event) => setResolveForm((current) => ({ ...current, actual_expenses_reimbursed: event.target.value }))}
                            className="w-full bg-slate-100 dark:bg-[#1e293b] border border-slate-200 dark:border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-900 dark:text-slate-100"
                        />
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-slate-600 dark:text-slate-400 mb-1">
                            Note de resolution
                        </label>
                        <textarea
                            value={resolveForm.notes}
                            onChange={(event) => setResolveForm((current) => ({ ...current, notes: event.target.value }))}
                            rows={4}
                            className="w-full bg-slate-100 dark:bg-[#1e293b] border border-slate-200 dark:border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-900 dark:text-slate-100"
                            required
                        />
                    </div>
                    <div className="flex justify-end gap-3">
                        <Button type="button" variant="secondary" onClick={() => setSelectedSettlement(null)}>
                            Annuler
                        </Button>
                        <Button type="submit" isLoading={resolveMutation.isPending}>
                            Resoudre
                        </Button>
                    </div>
                </form>
            </Modal>

            <div className="space-y-6 animate-fade-in">
                <div>
                    <h1 className="text-2xl font-bold text-slate-900 dark:text-slate-100">Reglements</h1>
                    <p className="text-sm text-slate-400 dark:text-slate-500 mt-1">
                        Vue d'ensemble des remises de caisse et des litiges.
                    </p>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-5 gap-4 bg-white dark:bg-[#1a2634] border border-slate-200 dark:border-slate-800 rounded-xl p-4">
                    <div>
                        <label className="block text-xs font-medium text-slate-600 dark:text-slate-400 mb-1">Agence</label>
                        <select
                            value={officeId}
                            onChange={(event) => setOfficeId(event.target.value)}
                            className="w-full bg-slate-100 dark:bg-[#1e293b] border border-slate-200 dark:border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-900 dark:text-slate-100"
                        >
                            <option value="all">Toutes</option>
                            {offices.map((office: any) => (
                                <option key={office.id} value={office.id}>{office.name}</option>
                            ))}
                        </select>
                    </div>

                    <div>
                        <label className="block text-xs font-medium text-slate-600 dark:text-slate-400 mb-1">Conducteur</label>
                        <select
                            value={conductorId}
                            onChange={(event) => setConductorId(event.target.value)}
                            className="w-full bg-slate-100 dark:bg-[#1e293b] border border-slate-200 dark:border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-900 dark:text-slate-100"
                        >
                            <option value="all">Tous</option>
                            {conductors.map((conductor: any) => (
                                <option key={conductor.id} value={conductor.id}>
                                    {conductor.first_name} {conductor.last_name}
                                </option>
                            ))}
                        </select>
                    </div>

                    <div>
                        <label className="block text-xs font-medium text-slate-600 dark:text-slate-400 mb-1">Statut</label>
                        <select
                            value={status}
                            onChange={(event) => setStatus(event.target.value)}
                            className="w-full bg-slate-100 dark:bg-[#1e293b] border border-slate-200 dark:border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-900 dark:text-slate-100"
                        >
                            <option value="all">Tous</option>
                            <option value="pending">En attente</option>
                            <option value="partial">Partiel</option>
                            <option value="settled">Regle</option>
                            <option value="disputed">Litige</option>
                        </select>
                    </div>

                    <div>
                        <label className="block text-xs font-medium text-slate-600 dark:text-slate-400 mb-1">Du</label>
                        <input
                            type="date"
                            value={dateFrom}
                            onChange={(event) => setDateFrom(event.target.value)}
                            className="w-full bg-slate-100 dark:bg-[#1e293b] border border-slate-200 dark:border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-900 dark:text-slate-100"
                        />
                    </div>

                    <div>
                        <label className="block text-xs font-medium text-slate-600 dark:text-slate-400 mb-1">Au</label>
                        <input
                            type="date"
                            value={dateTo}
                            onChange={(event) => setDateTo(event.target.value)}
                            className="w-full bg-slate-100 dark:bg-[#1e293b] border border-slate-200 dark:border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-900 dark:text-slate-100"
                        />
                    </div>
                </div>

                <div className="bg-white dark:bg-[#1a2634] border border-slate-200 dark:border-slate-800 rounded-xl overflow-hidden">
                    <div className="px-5 py-4 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between">
                        <h2 className="text-sm font-semibold text-slate-900 dark:text-slate-100">Liste des reglements</h2>
                        <span className="text-xs text-slate-400 dark:text-slate-500">{settlementsData?.count || 0} resultats</span>
                    </div>

                    <div className="overflow-x-auto">
                        <table className="w-full">
                            <thead>
                                <tr className="border-b border-slate-200 dark:border-slate-700/30">
                                    <th className="text-left px-5 py-3 text-[11px] font-semibold text-slate-400 dark:text-slate-500 uppercase tracking-wider">Voyage</th>
                                    <th className="text-left px-5 py-3 text-[11px] font-semibold text-slate-400 dark:text-slate-500 uppercase tracking-wider">Agence</th>
                                    <th className="text-left px-5 py-3 text-[11px] font-semibold text-slate-400 dark:text-slate-500 uppercase tracking-wider">Conducteur</th>
                                    <th className="text-left px-5 py-3 text-[11px] font-semibold text-slate-400 dark:text-slate-500 uppercase tracking-wider">Statut</th>
                                    <th className="text-right px-5 py-3 text-[11px] font-semibold text-slate-400 dark:text-slate-500 uppercase tracking-wider">Net attendu</th>
                                    <th className="text-right px-5 py-3 text-[11px] font-semibold text-slate-400 dark:text-slate-500 uppercase tracking-wider">Ecart</th>
                                    <th className="text-left px-5 py-3 text-[11px] font-semibold text-slate-400 dark:text-slate-500 uppercase tracking-wider">Cree le</th>
                                    <th className="text-right px-5 py-3 text-[11px] font-semibold text-slate-400 dark:text-slate-500 uppercase tracking-wider">Action</th>
                                </tr>
                            </thead>
                            <tbody>
                                {settlements.map((settlement) => (
                                    <tr key={settlement.id} className="border-b border-slate-200 dark:border-slate-700/20">
                                        <td className="px-5 py-3 text-sm text-slate-900 dark:text-slate-100">
                                            #{settlement.trip} {settlement.origin_name} &rarr; {settlement.destination_name}
                                        </td>
                                        <td className="px-5 py-3 text-sm text-slate-600 dark:text-slate-400">{settlement.office_name}</td>
                                        <td className="px-5 py-3 text-sm text-slate-600 dark:text-slate-400">{settlement.conductor_name}</td>
                                        <td className="px-5 py-3">
                                            <StatusBadge status={settlement.status} type="settlement" />
                                        </td>
                                        <td className="px-5 py-3 text-right text-sm text-slate-900 dark:text-slate-100">
                                            {formatCurrency(settlement.net_cash_expected)}
                                        </td>
                                        <td className="px-5 py-3 text-right text-sm text-slate-900 dark:text-slate-100">
                                            {settlement.discrepancy_amount !== null ? formatCurrency(settlement.discrepancy_amount) : 'En attente'}
                                        </td>
                                        <td className="px-5 py-3 text-sm text-slate-600 dark:text-slate-400">
                                            {formatDateTime(settlement.created_at)}
                                        </td>
                                        <td className="px-5 py-3 text-right">
                                            {settlement.status === 'disputed' ? (
                                                <Button size="sm" onClick={() => openResolveModal(settlement)}>
                                                    Resoudre
                                                </Button>
                                            ) : (
                                                <span className="text-xs text-slate-400 dark:text-slate-500">Aucune action</span>
                                            )}
                                        </td>
                                    </tr>
                                ))}
                                {!isLoading && settlements.length === 0 && (
                                    <tr>
                                        <td colSpan={8} className="px-5 py-8 text-center text-sm text-slate-400 dark:text-slate-500">
                                            Aucun reglement trouve.
                                        </td>
                                    </tr>
                                )}
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>
        </>
    )
}
