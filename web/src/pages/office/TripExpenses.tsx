import { formatCurrency, formatDateTime } from '../../utils/formatters'
import { useTripExpenseMirror } from '../../hooks/useTripMirrorData'

interface TripExpensesProps {
    tripId: number
}

export function TripExpenses({ tripId }: TripExpensesProps) {
    const {
        data: expenses,
        isLoading,
        error,
    } = useTripExpenseMirror(tripId)

    if (isLoading) {
        return <div className="animate-pulse h-32 bg-surface-800/80 backdrop-blur-md rounded-xl" />
    }

    if (error) {
        return (
            <div className="bg-red-500/10 border border-status-error/30 text-red-600 dark:text-red-400 p-4 rounded-lg">
                Erreur de chargement des dépenses Firebase.
            </div>
        )
    }

    if (expenses.length === 0) {
        return (
            <div className="bg-surface-800/80 backdrop-blur-md border border-surface-700 rounded-xl py-12 text-center">
                <p className="text-text-muted">Aucune dépense enregistrée pour ce voyage.</p>
            </div>
        )
    }

    const total = expenses.reduce((sum, expense) => sum + expense.amount, 0)

    return (
        <div className="space-y-4">
            <div className="bg-surface-800/80 backdrop-blur-md border border-surface-700 rounded-xl p-4">
                <p className="text-sm text-text-muted">Total dépenses</p>
                <p className="text-xl font-semibold text-text-primary">{formatCurrency(total)}</p>
            </div>

            <div className="bg-surface-800/80 backdrop-blur-md border border-surface-700 rounded-xl overflow-hidden">
                <div className="overflow-x-auto">
                    <table className="w-full text-left text-sm text-text-secondary">
                        <thead className="bg-surface-700/50 text-xs uppercase text-text-muted font-medium border-b border-surface-700">
                            <tr>
                                <th className="px-6 py-3">Catégorie</th>
                                <th className="px-6 py-3">Description</th>
                                <th className="px-6 py-3">Montant</th>
                                <th className="px-6 py-3">Créé par</th>
                                <th className="px-6 py-3">Créé le</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-surface-600/30">
                            {expenses.map((expense) => (
                                <tr key={expense.id} className="hover:bg-surface-900/30 transition-colors">
                                    <td className="px-6 py-4 capitalize">{expense.category || '-'}</td>
                                    <td className="px-6 py-4 text-text-primary">{expense.description || '-'}</td>
                                    <td className="px-6 py-4 font-medium text-text-primary">
                                        {formatCurrency(expense.amount)}
                                    </td>
                                    <td className="px-6 py-4">{expense.created_by_name || '-'}</td>
                                    <td className="px-6 py-4 text-xs">{formatDateTime(expense.created_at)}</td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    )
}
