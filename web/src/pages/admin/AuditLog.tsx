import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { getAuditLogs, getUsers } from '../../api/admin'
import { formatDateTime } from '../../utils/formatters'
import { DataTable } from '../../components/ui/DataTable'
import { Button } from '../../components/ui/Button'
import { Modal } from '../../components/ui/Modal'
import { createColumnHelper } from '@tanstack/react-table'
import { Activity, Eye, Filter } from 'lucide-react'
import type { AuditLogEntry } from '../../types/admin'
import type { ColumnDef } from '@tanstack/react-table'

const columnHelper = createColumnHelper<AuditLogEntry>()

export function AuditLog() {
    const [page, setPage] = useState(1)

    // Filters
    const [dateFrom, setDateFrom] = useState('')
    const [dateTo, setDateTo] = useState('')
    const [selectedUser, setSelectedUser] = useState('')
    const [tableName, setTableName] = useState('')
    const [action, setAction] = useState('')

    // View Modal
    const [viewingLog, setViewingLog] = useState<AuditLogEntry | null>(null)

    const { data: logsData, isLoading: isLogsLoading } = useQuery({
        queryKey: ['audit_logs', page, dateFrom, dateTo, selectedUser, tableName, action],
        queryFn: () => getAuditLogs({
            page,
            date_from: dateFrom || undefined,
            date_to: dateTo || undefined,
            user: selectedUser ? Number(selectedUser) : undefined,
            table_name: tableName || undefined,
            action: action || undefined,
        }),
    })

    const { data: usersData } = useQuery({
        queryKey: ['users_list_all'],
        queryFn: () => getUsers({ limit: 100 }), // Assume limit 100 is enough for a dropdown
    })

    const getActionColor = (act: string) => {
        switch (act) {
            case 'create': return 'text-status-success bg-status-success/10 border-status-success/20'
            case 'update': return 'text-brand-400 bg-brand-500/10 border-brand-500/20'
            case 'delete': return 'text-status-error bg-status-error/10 border-status-error/20'
            case 'override': return 'text-status-warning bg-status-warning/10 border-status-warning/20'
            default: return 'text-text-muted bg-surface-600/50 border-surface-600/50'
        }
    }

    const columns: ColumnDef<AuditLogEntry, any>[] = [
        columnHelper.accessor('created_at', {
            header: 'Date d\'action',
            cell: info => <span className="text-sm font-medium">{formatDateTime(info.getValue())}</span>,
        }),
        columnHelper.accessor('user_name', {
            header: 'Utilisateur',
            cell: info => info.getValue() || <span className="italic text-text-muted">Système</span>,
        }),
        columnHelper.accessor('action', {
            header: 'Action',
            cell: info => (
                <span className={`px-2 py-0.5 rounded text-xs font-medium border uppercase ${getActionColor(info.getValue())}`}>
                    {info.getValue()}
                </span>
            ),
        }),
        columnHelper.accessor('table_name', {
            header: 'Table affectée',
            cell: info => <span className="font-mono text-xs px-2 py-1 bg-surface-700/50 rounded">{info.getValue()}</span>,
        }),
        columnHelper.accessor('record_id', {
            header: 'ID Record',
            cell: info => <span className="font-mono text-xs">{info.getValue()}</span>,
        }),
        columnHelper.accessor('ip_address', {
            header: 'IP Source',
            cell: info => info.getValue() || '-',
        }),
        columnHelper.display({
            id: 'actions',
            header: 'Détails',
            cell: info => (
                <Button variant="ghost" className="p-1.5 h-auto text-brand-400 hover:bg-brand-500/10" onClick={() => setViewingLog(info.row.original)} title="Voir les détails">
                    <Eye className="w-4 h-4" />
                </Button>
            ),
        }),
    ]

    return (
        <div className="space-y-6 animate-fade-in">
            <div>
                <h1 className="text-2xl font-bold text-text-primary flex items-center gap-2">
                    <Activity className="w-6 h-6 text-brand-400" />
                    Journal d'Audit
                </h1>
                <p className="text-sm text-text-muted mt-1">Traçabilité complète des actions critiques sur le système.</p>
            </div>

            <div className="bg-surface-800 border border-surface-600/50 p-4 rounded-xl space-y-4">
                <div className="flex items-center gap-2 text-sm font-medium text-text-primary mb-2">
                    <Filter className="w-4 h-4" /> Filtres de recherche
                </div>
                <div className="grid grid-cols-1 md:grid-cols-5 gap-4">
                    <div>
                        <label className="block text-xs text-text-muted mb-1">Du</label>
                        <input
                            type="date"
                            value={dateFrom}
                            onChange={e => { setDateFrom(e.target.value); setPage(1); }}
                            className="w-full bg-surface-700 border border-surface-600/50 rounded-lg px-3 py-2 text-sm text-text-primary focus:ring-2 focus:ring-brand-500"
                        />
                    </div>
                    <div>
                        <label className="block text-xs text-text-muted mb-1">Au</label>
                        <input
                            type="date"
                            value={dateTo}
                            onChange={e => { setDateTo(e.target.value); setPage(1); }}
                            className="w-full bg-surface-700 border border-surface-600/50 rounded-lg px-3 py-2 text-sm text-text-primary focus:ring-2 focus:ring-brand-500"
                        />
                    </div>
                    <div>
                        <label className="block text-xs text-text-muted mb-1">Utilisateur</label>
                        <select
                            value={selectedUser}
                            onChange={e => { setSelectedUser(e.target.value); setPage(1); }}
                            className="w-full bg-surface-700 border border-surface-600/50 rounded-lg px-3 py-2 text-sm text-text-primary focus:ring-2 focus:ring-brand-500"
                        >
                            <option value="">Tous les utilisateurs</option>
                            {usersData?.results?.map(u => (
                                <option key={u.id} value={u.id}>{u.first_name} {u.last_name}</option>
                            ))}
                        </select>
                    </div>
                    <div>
                        <label className="block text-xs text-text-muted mb-1">Table</label>
                        <input
                            type="text"
                            placeholder="Ex: trip, user, cargo_ticket"
                            value={tableName}
                            onChange={e => { setTableName(e.target.value); setPage(1); }}
                            className="w-full bg-surface-700 border border-surface-600/50 rounded-lg px-3 py-2 text-sm text-text-primary focus:ring-2 focus:ring-brand-500"
                        />
                    </div>
                    <div>
                        <label className="block text-xs text-text-muted mb-1">Action</label>
                        <select
                            value={action}
                            onChange={e => { setAction(e.target.value); setPage(1); }}
                            className="w-full bg-surface-700 border border-surface-600/50 rounded-lg px-3 py-2 text-sm text-text-primary focus:ring-2 focus:ring-brand-500"
                        >
                            <option value="">Toutes les actions</option>
                            <option value="create">Création (CREATE)</option>
                            <option value="update">Mise à jour (UPDATE)</option>
                            <option value="delete">Suppression (DELETE)</option>
                            <option value="override">Forçage (OVERRIDE)</option>
                        </select>
                    </div>
                </div>
            </div>

            <div className="bg-surface-800 border border-surface-600/50 rounded-xl overflow-hidden min-h-[400px]">
                <DataTable
                    data={logsData?.results || []}
                    columns={columns}
                    isLoading={isLogsLoading}
                    pageCount={logsData ? Math.ceil(logsData.count / 20) : 1}
                    pageIndex={page - 1}
                    onPageChange={(newIndex) => setPage(newIndex + 1)}
                    emptyMessage="Aucun journal d'audit trouvé pour ces critères."
                />
            </div>

            <Modal
                isOpen={!!viewingLog}
                onClose={() => setViewingLog(null)}
                title="Détails de l'Auditer"
                size="lg"
            >
                {viewingLog && (
                    <div className="space-y-6">
                        <div className="grid grid-cols-2 gap-4 text-sm bg-surface-800 p-4 rounded-lg border border-surface-600/50">
                            <div><span className="text-text-muted block mb-1">Date:</span> <span className="font-medium text-text-primary">{formatDateTime(viewingLog.created_at)}</span></div>
                            <div><span className="text-text-muted block mb-1">Utilisateur:</span> <span className="font-medium text-text-primary">{viewingLog.user_name || 'Système'}</span></div>
                            <div><span className="text-text-muted block mb-1">Action:</span> <span className={`px-2 py-0.5 rounded text-xs font-medium border uppercase ${getActionColor(viewingLog.action)}`}>{viewingLog.action}</span></div>
                            <div><span className="text-text-muted block mb-1">Table affectée:</span> <span className="font-mono text-xs px-2 py-1 bg-surface-700/50 rounded">{viewingLog.table_name} (ID: {viewingLog.record_id})</span></div>
                            <div><span className="text-text-muted block mb-1">IP Source:</span> <span className="font-medium text-text-primary">{viewingLog.ip_address || '-'}</span></div>
                        </div>

                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                            <div>
                                <h3 className="text-sm font-semibold text-text-primary mb-2 flex items-center gap-2">
                                    <div className="w-2 h-2 rounded-full bg-status-error"></div> Ancien État
                                </h3>
                                <div className="bg-surface-900 rounded-lg p-4 border border-surface-600/50 overflow-auto max-h-96">
                                    <pre className="text-xs text-text-secondary whitespace-pre-wrap font-mono">
                                        {viewingLog.old_values ? JSON.stringify(viewingLog.old_values, null, 2) : 'Aucun état précédent.'}
                                    </pre>
                                </div>
                            </div>
                            <div>
                                <h3 className="text-sm font-semibold text-text-primary mb-2 flex items-center gap-2">
                                    <div className="w-2 h-2 rounded-full bg-status-success"></div> Nouvel État
                                </h3>
                                <div className="bg-surface-900 rounded-lg p-4 border border-surface-600/50 overflow-auto max-h-96">
                                    <pre className="text-xs text-text-secondary whitespace-pre-wrap font-mono">
                                        {viewingLog.new_values ? JSON.stringify(viewingLog.new_values, null, 2) : 'Aucun nouvel état.'}
                                    </pre>
                                </div>
                            </div>
                        </div>

                        <div className="flex justify-end pt-4">
                            <Button variant="secondary" onClick={() => setViewingLog(null)}>Fermer</Button>
                        </div>
                    </div>
                )}
            </Modal>
        </div>
    )
}
