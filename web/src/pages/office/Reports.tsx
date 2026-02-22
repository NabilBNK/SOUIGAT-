import { useState, useEffect } from 'react'
import { useQuery, useMutation } from '@tanstack/react-query'
import { getDailyReport, triggerExport, getExportStatus, getExportDownloadUrl } from '../../api/reports'
import { formatCurrency } from '../../utils/formatters'
import { Button } from '../../components/ui/Button'
import { DataTable } from '../../components/ui/DataTable'
import { createColumnHelper } from '@tanstack/react-table'
import { AlertCircle, FileSpreadsheet, Loader2, Calendar } from 'lucide-react'

const columnHelper = createColumnHelper<any>()

const columns = [
    columnHelper.accessor('date', {
        header: 'Date',
        cell: info => info.getValue(),
    }),
    columnHelper.accessor('office_name', {
        header: 'Agence',
        cell: info => info.getValue(),
    }),
    columnHelper.accessor('total_trips', {
        header: 'Voyages',
        cell: info => info.getValue(),
    }),
    columnHelper.accessor('total_passengers', {
        header: 'Passagers',
        cell: info => info.getValue(),
    }),
    columnHelper.accessor('total_cargo', {
        header: 'Colis',
        cell: info => info.getValue(),
    }),
    columnHelper.accessor('passenger_revenue', {
        header: 'Rev. Passagers',
        cell: info => formatCurrency(info.getValue()),
    }),
    columnHelper.accessor('cargo_revenue', {
        header: 'Rev. Colis',
        cell: info => formatCurrency(info.getValue()),
    }),
    columnHelper.accessor('net_revenue', {
        header: 'Net à percevoir',
        cell: info => (
            <span className="font-semibold text-status-success">
                {formatCurrency(info.getValue())}
            </span>
        ),
    }),
]

export function ReportsPage() {
    const today = new Date().toISOString().split('T')[0]
    const [dateFrom, setDateFrom] = useState(today)
    const [dateTo, setDateTo] = useState(today)

    // Export state
    const [exportTaskId, setExportTaskId] = useState<string | null>(null)
    const [exportError, setExportError] = useState<string | null>(null)
    const [exportStartTime, setExportStartTime] = useState<number | null>(null)

    const { data: reports, isLoading, isError, refetch } = useQuery({
        queryKey: ['dailyReports', dateFrom, dateTo],
        queryFn: () => getDailyReport({ date_from: dateFrom, date_to: dateTo }),
    })

    // Poll export status if we have a taskId
    const { data: exportStatus } = useQuery({
        queryKey: ['exportStatus', exportTaskId],
        queryFn: () => getExportStatus(exportTaskId!),
        enabled: !!exportTaskId,
        refetchInterval: (query) => {
            const status = query.state.data?.status
            if (status === 'SUCCESS' || status === 'FAILURE') return false // Stop polling
            return 2000 // Poll every 2s
        }
    })

    const exportMutation = useMutation({
        mutationFn: () => triggerExport({
            type: 'daily_summary',
            date_from: dateFrom,
            date_to: dateTo
        }),
        onSuccess: (data) => {
            setExportTaskId(data.task_id)
            setExportError(null)
            setExportStartTime(Date.now())
        },
        onError: () => {
            setExportError("Erreur lors du lancement de l'export.")
        }
    })

    const isExporting = !!exportTaskId && exportStatus?.status !== 'SUCCESS' && exportStatus?.status !== 'FAILURE'

    // Handle export completion
    useEffect(() => {
        if (exportStatus?.status === 'SUCCESS' && exportStatus.task_id) {
            // Give user a brief moment to see 100% then redirect to download
            const timer = setTimeout(() => {
                window.location.href = getExportDownloadUrl(exportStatus.task_id)
                // Reset state after download starts
                setTimeout(() => {
                    setExportTaskId(null)
                    setExportStartTime(null)
                }, 2000)
            }, 1000)
            return () => clearTimeout(timer)
        } else if (exportStatus?.status === 'FAILURE') {
            setExportError("L'export a échoué. Veuillez réessayer.")
            setExportTaskId(null)
            setExportStartTime(null)
        }
    }, [exportStatus])

    // Handle export polling timeout
    useEffect(() => {
        if (exportStartTime && isExporting) {
            const interval = setInterval(() => {
                if (Date.now() - exportStartTime > 60000) {
                    setExportError("Délai d'exportation dépassé.")
                    setExportTaskId(null)
                    setExportStartTime(null)
                }
            }, 5000)
            return () => clearInterval(interval)
        }
    }, [exportStartTime, isExporting])

    const handleFilterSubmit = (e: React.FormEvent) => {
        e.preventDefault()
        refetch()
    }

    return (
        <div className="space-y-6 animate-fade-in">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <div>
                    <h1 className="text-2xl font-bold text-text-primary">Rapports Financiers</h1>
                    <p className="text-sm text-text-muted mt-1">Consultez et exportez les résumés journaliers par agence.</p>
                </div>

                <div className="flex items-center gap-3">
                    {/* Export Status Banner */}
                    {isExporting && (
                        <div className="flex items-center gap-2 bg-brand-500/10 text-brand-400 px-3 py-1.5 rounded-lg border border-brand-500/20 text-sm font-medium">
                            <Loader2 className="w-4 h-4 animate-spin" />
                            Préparation de l'Excel...
                            {exportStatus?.progress !== undefined && ` ${exportStatus.progress}%`}
                        </div>
                    )}

                    <Button
                        variant="secondary"
                        icon={<FileSpreadsheet className="w-4 h-4" />}
                        onClick={() => exportMutation.mutate()}
                        isLoading={exportMutation.isPending}
                        disabled={isExporting}
                    >
                        Exporter
                    </Button>
                </div>
            </div>

            {exportError && (
                <div className="bg-status-error/10 border border-status-error/20 p-4 rounded-lg flex items-start gap-3">
                    <AlertCircle className="w-5 h-5 text-status-error shrink-0 mt-0.5" />
                    <p className="text-sm text-status-error">{exportError}</p>
                </div>
            )}

            {/* Filters */}
            <form onSubmit={handleFilterSubmit} className="bg-surface-800 border border-surface-600/50 p-4 rounded-xl flex flex-col sm:flex-row gap-4 items-end">
                <div className="flex-1 w-full">
                    <label className="block text-xs font-medium text-text-secondary mb-1">Date de début</label>
                    <div className="relative">
                        <Calendar className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-text-muted" />
                        <input
                            type="date"
                            value={dateFrom}
                            onChange={(e) => setDateFrom(e.target.value)}
                            className="w-full bg-surface-700 border border-surface-600/50 rounded-lg pl-10 pr-3 py-2 text-sm text-text-primary focus:outline-none focus:ring-2 focus:ring-brand-500/50"
                        />
                    </div>
                </div>
                <div className="flex-1 w-full">
                    <label className="block text-xs font-medium text-text-secondary mb-1">Date de fin</label>
                    <div className="relative">
                        <Calendar className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-text-muted" />
                        <input
                            type="date"
                            value={dateTo}
                            onChange={(e) => setDateTo(e.target.value)}
                            className="w-full bg-surface-700 border border-surface-600/50 rounded-lg pl-10 pr-3 py-2 text-sm text-text-primary focus:outline-none focus:ring-2 focus:ring-brand-500/50"
                        />
                    </div>
                </div>
                <Button type="submit" variant="primary">Filtrer</Button>
            </form>

            {/* Data Table */}
            <div className="bg-surface-800 border border-surface-600/50 rounded-xl overflow-hidden min-h-[400px]">
                {isError ? (
                    <div className="p-8 text-center text-status-error">
                        <AlertCircle className="w-8 h-8 mx-auto mb-2 opacity-80" />
                        <p>Erreur lors du chargement des rapports.</p>
                    </div>
                ) : (
                    <DataTable
                        data={reports || []}
                        columns={columns}
                        isLoading={isLoading}
                        emptyMessage="Aucun rapport trouvé pour cette période."
                        pageCount={1}
                        pageIndex={0}
                        onPageChange={() => { }}
                    />
                )}
            </div>
        </div>
    )
}
