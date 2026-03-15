import { useState, useEffect, useMemo } from 'react'
import { useQuery, useMutation } from '@tanstack/react-query'
import { getDailyReport, triggerExport, getExportStatus, getExportDownloadUrl } from '../../api/reports'
import { getOffices } from '../../api/admin'
import { useAuth } from '../../hooks/useAuth'
import { formatCurrency } from '../../utils/formatters'
import { Button } from '../../components/ui/Button'
import { DataTable } from '../../components/ui/DataTable'
import { createColumnHelper } from '@tanstack/react-table'
import { AlertCircle, FileSpreadsheet, Loader2, Calendar, LayoutDashboard } from 'lucide-react'

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
        cell: info => {
            const val = info.getValue()
            return (
                <span className={`font-semibold ${val > 0 ? 'text-status-success' : 'text-text-primary'}`}>
                    {formatCurrency(val)}
                </span>
            )
        },
    }),
]

export function ReportsPage() {
    const { user } = useAuth()
    const today = new Date().toISOString().split('T')[0]
    const [dateFrom, setDateFrom] = useState(today)
    const [dateTo, setDateTo] = useState(today)
    const [selectedOffice, setSelectedOffice] = useState<string>('all')

    // Export state
    const [exportTaskId, setExportTaskId] = useState<string | null>(null)
    const [exportDownloadToken, setExportDownloadToken] = useState<string | null>(null)
    const [exportError, setExportError] = useState<string | null>(null)
    const [exportStartTime, setExportStartTime] = useState<number | null>(null)

    // Admin: Fetch offices for dropdown
    const { data: officesData } = useQuery({
        queryKey: ['offices'],
        queryFn: () => getOffices({ limit: 100 }),
        enabled: user?.role === 'admin'
    })
    const offices = officesData?.results || []

    const queryParams: any = { date_from: dateFrom, date_to: dateTo }
    if (user?.role === 'admin' && selectedOffice !== 'all') {
        queryParams.office_id = Number(selectedOffice)
    }

    const { data: reports, isLoading, isError, refetch } = useQuery({
        queryKey: ['dailyReports', dateFrom, dateTo, selectedOffice],
        queryFn: () => getDailyReport(queryParams),
    })

    // KPI Calculations
    const kpis = useMemo(() => {
        if (!reports) return { trips: 0, pRev: 0, cRev: 0, net: 0 }
        return reports.reduce((acc, row) => ({
            trips: acc.trips + (row.total_trips || 0),
            pRev: acc.pRev + (row.passenger_revenue || 0),
            cRev: acc.cRev + (row.cargo_revenue || 0),
            net: acc.net + (row.net_revenue || 0),
        }), { trips: 0, pRev: 0, cRev: 0, net: 0 })
    }, [reports])

    // Poll export status if we have a taskId
    const { data: exportStatus } = useQuery({
        queryKey: ['exportStatus', exportTaskId],
        queryFn: () => getExportStatus(exportTaskId!),
        enabled: !!exportTaskId,
        refetchInterval: (query) => {
            const status = query.state.data?.status
            if (status === 'success' || status === 'failure') return false
            return 2000
        }
    })

    const exportMutation = useMutation({
        mutationFn: () => triggerExport({
            report_type: 'daily',
            filters: queryParams
        }),
        onSuccess: (data) => {
            setExportTaskId(data.task_id)
            setExportDownloadToken(data.download_token)
            setExportError(null)
            setExportStartTime(Date.now())
        },
        onError: () => {
            setExportError("Erreur lors du lancement de l'export.")
        }
    })

    const isExporting = !!exportTaskId && exportStatus?.status !== 'success' && exportStatus?.status !== 'failure'

    // Handle export completion
    useEffect(() => {
        if (exportStatus?.status === 'success' && exportStatus.task_id && exportDownloadToken) {
            // Give user a brief moment to see 100% then redirect to download
            const timer = setTimeout(() => {
                window.location.href = getExportDownloadUrl(exportStatus.task_id, exportDownloadToken)
                // Reset state after download starts
                setTimeout(() => {
                    setExportTaskId(null)
                    setExportDownloadToken(null)
                    setExportStartTime(null)
                }, 2000)
            }, 1000)
            return () => clearTimeout(timer)
        } else if (exportStatus?.status === 'failure') {
            setExportError("L'export a échoué. Veuillez réessayer.")
            setExportTaskId(null)
            setExportDownloadToken(null)
            setExportStartTime(null)
        }
    }, [exportDownloadToken, exportStatus])

    // Handle export polling timeout
    useEffect(() => {
        if (exportStartTime && isExporting) {
            const interval = setInterval(() => {
                if (Date.now() - exportStartTime > 60000) {
                    setExportError("Délai d'exportation dépassé.")
                    setExportTaskId(null)
                    setExportDownloadToken(null)
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
                {user?.role === 'admin' && (
                    <div className="flex-1 w-full">
                        <label className="block text-xs font-medium text-text-secondary mb-1">Agence</label>
                        <div className="relative">
                            <LayoutDashboard className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-text-muted" />
                            <select
                                value={selectedOffice}
                                onChange={(e) => setSelectedOffice(e.target.value)}
                                className="w-full bg-surface-700 border border-surface-600/50 rounded-lg pl-10 pr-3 py-2 text-sm text-text-primary focus:outline-none focus:ring-2 focus:ring-brand-500/50 appearance-none"
                            >
                                <option value="all">Toutes les agences</option>
                                {offices.map((office: any) => (
                                    <option key={office.id} value={office.id}>{office.name}</option>
                                ))}
                            </select>
                        </div>
                    </div>
                )}
                <Button type="submit" variant="primary">Filtrer</Button>
            </form>

            {/* KPI Cards */}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                {[
                    { label: 'Total Voyages', value: kpis.trips, format: (v: number) => v.toString() },
                    { label: 'Revenus Passagers', value: kpis.pRev, format: formatCurrency },
                    { label: 'Revenus Colis', value: kpis.cRev, format: formatCurrency },
                    {
                        label: 'Net à Percevoir',
                        value: kpis.net,
                        format: formatCurrency,
                        highlightPositive: true
                    },
                ].map((kpi, idx) => (
                    <div key={idx} className="bg-surface-800 border border-surface-600/50 p-4 rounded-xl flex flex-col justify-center">
                        <span className="text-xs font-medium text-text-secondary truncate">{kpi.label}</span>
                        {isLoading ? (
                            <div className="h-7 mt-1 w-24 bg-surface-700 rounded animate-pulse" />
                        ) : (
                            <span className={`text-xl font-bold mt-1 ${kpi.highlightPositive && kpi.value > 0 ? 'text-status-success' : 'text-brand-400'}`}>
                                {kpi.format(kpi.value)}
                            </span>
                        )}
                    </div>
                ))}
            </div>

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
