import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getQuarantinedSyncs, reviewQuarantinedSync, bulkReviewQuarantinedSyncs } from '../../api/admin'
import { formatDateTime } from '../../utils/formatters'
import { DataTable } from '../../components/ui/DataTable'
import { Button } from '../../components/ui/Button'
import { Modal } from '../../components/ui/Modal'
import { createColumnHelper } from '@tanstack/react-table'
import { ShieldAlert, CheckCircle, XCircle, Search, Eye } from 'lucide-react'
import type { QuarantinedSync } from '../../types/admin'

const columnHelper = createColumnHelper<any>()

export function QuarantineReview() {
    const queryClient = useQueryClient()
    const [page, setPage] = useState(1)

    // Filters
    const [statusFilter, setStatusFilter] = useState<'pending' | 'approved' | 'rejected'>('pending')
    const [search, setSearch] = useState('')

    // Modals & Selection
    const [viewingItem, setViewingItem] = useState<QuarantinedSync | null>(null)
    const [reviewReason, setReviewReason] = useState('')
    const [selectedRows, setSelectedRows] = useState<Record<string, boolean>>({})

    const { data: qData, isLoading } = useQuery({
        queryKey: ['quarantine', page, statusFilter, search],
        queryFn: () => getQuarantinedSyncs({
            page,
            status: statusFilter,
            search: search || undefined
        }),
    })

    const invalidateQuarantine = () => {
        queryClient.invalidateQueries({ queryKey: ['quarantine'] })
    }

    const reviewMutation = useMutation({
        mutationFn: ({ id, status, notes }: { id: number, status: 'approved' | 'rejected', notes?: string }) =>
            reviewQuarantinedSync(id, { status, review_notes: notes }),
        onSuccess: () => {
            setViewingItem(null)
            setReviewReason('')
            setSelectedRows({})
            invalidateQuarantine()
        }
    })

    const bulkReviewMutation = useMutation({
        mutationFn: ({ ids, action, notes }: { ids: number[], action: 'approve' | 'reject', notes?: string }) =>
            bulkReviewQuarantinedSyncs({ ids, action, review_notes: notes }),
        onSuccess: () => {
            setViewingItem(null)
            setReviewReason('')
            setSelectedRows({})
            invalidateQuarantine()
        }
    })

    // Selected subset
    const selectedIds = Object.keys(selectedRows).filter(k => selectedRows[k]).map(Number)

    const columns = [
        columnHelper.display({
            id: 'select',
            header: ({ table }) => (
                <input
                    type="checkbox"
                    className="rounded border-surface-600/50 bg-surface-700 text-brand-500 focus:ring-brand-500"
                    checked={table.getIsAllRowsSelected()}
                    onChange={table.getToggleAllRowsSelectedHandler()}
                />
            ),
            cell: ({ row }) => (
                <input
                    type="checkbox"
                    className="rounded border-surface-600/50 bg-surface-700 text-brand-500 focus:ring-brand-500"
                    checked={row.getIsSelected()}
                    onChange={row.getToggleSelectedHandler()}
                    disabled={statusFilter !== 'pending'}
                />
            ),
        }),
        columnHelper.accessor('created_at', {
            header: 'Date d\'incident',
            cell: info => <span className="text-sm font-medium">{formatDateTime(info.getValue())}</span>,
        }),
        columnHelper.accessor('conductor_name', {
            header: 'Receveur',
            cell: info => info.getValue() || '-',
        }),
        columnHelper.accessor('trip_info', {
            header: 'Trajet',
            cell: info => info.getValue() || '-',
        }),
        columnHelper.accessor('reason', {
            header: 'Motif du blocage',
            cell: info => <span className="text-sm text-status-error truncate block max-w-xs" title={info.getValue()}>{info.getValue()}</span>,
        }),
        columnHelper.accessor('status', {
            header: 'Statut',
            cell: info => {
                const s = info.getValue()
                if (s === 'pending') return <span className="px-2 py-0.5 rounded text-xs font-medium border text-status-warning bg-status-warning/10 border-status-warning/20">En attente</span>
                if (s === 'approved') return <span className="px-2 py-0.5 rounded text-xs font-medium border text-status-success bg-status-success/10 border-status-success/20">Approuvé (Reprocessé)</span>
                return <span className="px-2 py-0.5 rounded text-xs font-medium border text-status-error bg-status-error/10 border-status-error/20">Rejeté</span>
            },
        }),
        columnHelper.display({
            id: 'actions',
            header: 'Actions',
            cell: info => {
                const item = info.row.original
                return (
                    <div className="flex items-center gap-2">
                        <Button variant="ghost" className="p-1.5 h-auto text-brand-400 hover:bg-brand-500/10" onClick={() => setViewingItem(item)} title="Examiner">
                            <Eye className="w-4 h-4" />
                        </Button>
                        {item.status === 'pending' && (
                            <>
                                <Button
                                    variant="primary"
                                    className="p-1.5 h-auto"
                                    onClick={() => reviewMutation.mutate({ id: item.id, status: 'approved' })}
                                    title="Approuver & Reprocesser"
                                    isLoading={reviewMutation.isPending && reviewMutation.variables?.id === item.id && reviewMutation.variables?.status === 'approved'}
                                >
                                    <CheckCircle className="w-4 h-4" />
                                </Button>
                                <Button
                                    variant="danger"
                                    className="p-1.5 h-auto"
                                    onClick={() => reviewMutation.mutate({ id: item.id, status: 'rejected' })}
                                    title="Rejeter (Ignorer)"
                                    isLoading={reviewMutation.isPending && reviewMutation.variables?.id === item.id && reviewMutation.variables?.status === 'rejected'}
                                >
                                    <XCircle className="w-4 h-4" />
                                </Button>
                            </>
                        )}
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
                        <ShieldAlert className="w-6 h-6 text-status-warning" />
                        Quarantaine & Conflits
                    </h1>
                    <p className="text-sm text-text-muted mt-1">Gérez les données hors ligne désynchronisées ou en conflit.</p>
                </div>
            </div>

            <div className="bg-surface-800 border border-surface-600/50 p-4 rounded-xl flex flex-col md:flex-row gap-4 items-center justify-between">
                <div className="flex items-center gap-4 w-full md:w-auto">
                    <div className="relative flex-1 md:w-64">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-text-muted" />
                        <input
                            type="text"
                            placeholder="Rechercher (Trajet, Receveur)..."
                            value={search}
                            onChange={e => { setSearch(e.target.value); setPage(1); }}
                            className="w-full bg-surface-700 border border-surface-600/50 rounded-lg pl-10 pr-3 py-2 text-sm text-text-primary focus:outline-none focus:ring-2 focus:ring-brand-500/50"
                        />
                    </div>
                    <div>
                        <select
                            value={statusFilter}
                            onChange={e => { setStatusFilter(e.target.value as any); setPage(1); setSelectedRows({}); }}
                            className="w-full bg-surface-700 border border-surface-600/50 rounded-lg px-3 py-2 text-sm text-text-primary focus:outline-none focus:ring-2 focus:ring-brand-500/50"
                        >
                            <option value="pending">En attente (Conflit)</option>
                            <option value="approved">Approuvé (Résolu)</option>
                            <option value="rejected">Rejeté (Ignoré)</option>
                        </select>
                    </div>
                </div>

                {selectedIds.length > 0 && statusFilter === 'pending' && (
                    <div className="flex items-center gap-2">
                        <span className="text-sm text-text-muted mr-2">{selectedIds.length} sélectionné(s)</span>
                        <Button
                            onClick={() => bulkReviewMutation.mutate({ ids: selectedIds, action: 'approve' })}
                            isLoading={bulkReviewMutation.isPending && bulkReviewMutation.variables?.action === 'approve'}
                        >
                            Approuver la sélection
                        </Button>
                        <Button
                            variant="danger"
                            onClick={() => bulkReviewMutation.mutate({ ids: selectedIds, action: 'reject' })}
                            isLoading={bulkReviewMutation.isPending && bulkReviewMutation.variables?.action === 'reject'}
                        >
                            Rejeter la sélection
                        </Button>
                    </div>
                )}
            </div>

            <div className="bg-surface-800 border border-surface-600/50 rounded-xl overflow-hidden min-h-[400px]">
                <DataTable
                    data={qData?.results || []}
                    columns={columns}
                    isLoading={isLoading}
                    pageCount={qData ? Math.ceil(qData.count / 20) : 1}
                    pageIndex={page - 1}
                    onPageChange={(newIndex) => setPage(newIndex + 1)}
                    emptyMessage="Aucune donnée en quarantaine."
                    rowSelection={selectedRows}
                    onRowSelectionChange={setSelectedRows}
                />
            </div>

            <Modal
                isOpen={!!viewingItem}
                onClose={() => !reviewMutation.isPending && setViewingItem(null)}
                title="Détail du conflit"
                size="lg"
            >
                {viewingItem && (
                    <div className="space-y-6">
                        <div className="bg-status-error/10 border border-status-error/30 p-4 rounded-lg flex items-start gap-3">
                            <ShieldAlert className="w-5 h-5 text-status-error shrink-0 mt-0.5" />
                            <div>
                                <h4 className="text-sm font-semibold text-status-error mb-1">Motif de la quarantaine</h4>
                                <p className="text-sm text-status-error/90">{viewingItem.reason}</p>
                            </div>
                        </div>

                        <div className="grid grid-cols-2 gap-4 text-sm bg-surface-800 p-4 rounded-lg border border-surface-600/50">
                            <div><span className="text-text-muted block mb-1">Date:</span> <span className="font-medium text-text-primary">{formatDateTime(viewingItem.created_at)}</span></div>
                            <div><span className="text-text-muted block mb-1">Receveur:</span> <span className="font-medium text-text-primary">{viewingItem.conductor_name}</span></div>
                            <div className="col-span-2"><span className="text-text-muted block mb-1">Trajet:</span> <span className="font-medium text-text-primary">{viewingItem.trip_info}</span></div>
                        </div>

                        <div>
                            <h3 className="text-sm font-semibold text-text-primary mb-2 flex items-center gap-2">
                                Données orphelines / rejetées par le backend
                            </h3>
                            <div className="bg-surface-900 rounded-lg p-4 border border-surface-600/50 overflow-auto max-h-96">
                                <pre className="text-xs text-text-secondary whitespace-pre-wrap font-mono">
                                    {JSON.stringify(viewingItem.original_data, null, 2)}
                                </pre>
                            </div>
                        </div>

                        {viewingItem.status !== 'pending' && (
                            <div className="text-sm border-t border-surface-600/50 pt-4 mt-4">
                                <p className="mb-1"><span className="text-text-muted">Révisé par:</span> <strong>{viewingItem.reviewed_by_name}</strong> le {formatDateTime(viewingItem.reviewed_at!)}</p>
                                {viewingItem.review_notes && <p className="italic text-text-muted">"{viewingItem.review_notes}"</p>}
                            </div>
                        )}

                        {viewingItem.status === 'pending' && (
                            <div className="border-t border-surface-600/50 pt-6">
                                <label className="block text-sm font-medium text-text-primary mb-2">Notes de révision (Optionnel)</label>
                                <textarea
                                    className="w-full bg-surface-700 border border-surface-600/50 rounded-lg px-3 py-2 text-text-primary focus:ring-2 focus:ring-brand-500 min-h-[80px] mb-4"
                                    placeholder="Explication pour l'approbation ou le rejet..."
                                    value={reviewReason}
                                    onChange={e => setReviewReason(e.target.value)}
                                />
                                <div className="flex justify-end gap-3">
                                    <Button variant="secondary" onClick={() => setViewingItem(null)}>Annuler</Button>
                                    <Button
                                        variant="danger"
                                        onClick={() => reviewMutation.mutate({ id: viewingItem.id, status: 'rejected', notes: reviewReason })}
                                        isLoading={reviewMutation.isPending && reviewMutation.variables?.status === 'rejected'}
                                    >
                                        Rejeter (Ignorer)
                                    </Button>
                                    <Button
                                        variant="primary"
                                        onClick={() => reviewMutation.mutate({ id: viewingItem.id, status: 'approved', notes: reviewReason })}
                                        isLoading={reviewMutation.isPending && reviewMutation.variables?.status === 'approved'}
                                    >
                                        Approuver (Reprocesser test)
                                    </Button>
                                </div>
                            </div>
                        )}
                        {viewingItem.status !== 'pending' && (
                            <div className="flex justify-end pt-4">
                                <Button variant="secondary" onClick={() => setViewingItem(null)}>Fermer</Button>
                            </div>
                        )}
                    </div>
                )}
            </Modal>
        </div>
    )
}
