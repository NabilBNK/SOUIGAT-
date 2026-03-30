import { useState, useEffect, useMemo, useRef } from 'react'
import { useQuery, useMutation } from '@tanstack/react-query'
import { getDailyReport, triggerExport, getExportStatus, downloadExportFile } from '../../api/reports'
import { getOffices } from '../../api/admin'
import { useAuth } from '../../hooks/useAuth'
import { formatCurrency } from '../../utils/formatters'
import { Button } from '../../components/ui/Button'
import { DataTable } from '../../components/ui/DataTable'
import { createColumnHelper } from '@tanstack/react-table'
import { AlertCircle, FileSpreadsheet, Loader2, Calendar, LayoutDashboard } from 'lucide-react'
import type { DailyReport } from '../../types/report'

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
                <span className={`font-semibold ${val > 0 ? 'text-emerald-400' : 'text-text-primary'}`}>
                    {formatCurrency(val)}
                </span>
            )
        },
    }),
]

export function ReportsPage() {
    const { user } = useAuth()
    const today = new Date().toISOString().split('T')[0]
    const defaultFrom = new Date(Date.now() - (6 * 24 * 60 * 60 * 1000)).toISOString().split('T')[0]
    const [dateFrom, setDateFrom] = useState(defaultFrom)
    const [dateTo, setDateTo] = useState(today)
    const [selectedOffice, setSelectedOffice] = useState<string>('all')
    const dateFromInputRef = useRef<HTMLInputElement | null>(null)
    const dateToInputRef = useRef<HTMLInputElement | null>(null)

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

    const { data: reports, isLoading, isError, error, refetch } = useQuery({
        queryKey: ['dailyReports', dateFrom, dateTo, selectedOffice],
        queryFn: () => getDailyReport(queryParams),
    })

    const rows = useMemo<DailyReport[]>(() => reports || [], [reports])

    const visibleRows = useMemo<DailyReport[]>(() => {
        return rows.filter((row) => (row.total_trips ?? 0) > 0)
    }, [rows])

    const reportErrorMessage = useMemo(() => {
        if (!error || typeof error !== 'object') {
            return 'Erreur lors du chargement des rapports.'
        }

        const response = (error as { response?: { data?: unknown } }).response
        const data = response?.data
        if (data && typeof data === 'object') {
            const detail = (data as { detail?: unknown }).detail
            if (typeof detail === 'string' && detail.trim().length > 0) {
                return detail
            }
        }

        return 'Erreur lors du chargement des rapports.'
    }, [error])

    // KPI Calculations
    const kpis = useMemo(() => {
        if (visibleRows.length === 0) return { trips: 0, pRev: 0, cRev: 0, net: 0 }
        return visibleRows.reduce((acc, row) => ({
            trips: acc.trips + (row.total_trips || 0),
            pRev: acc.pRev + (row.passenger_revenue || 0),
            cRev: acc.cRev + (row.cargo_revenue || 0),
            net: acc.net + (row.net_revenue || 0),
        }), { trips: 0, pRev: 0, cRev: 0, net: 0 })
    }, [visibleRows])

    const applyQuickRange = (days: number) => {
        const end = new Date()
        const start = new Date(Date.now() - ((days - 1) * 24 * 60 * 60 * 1000))
        setDateFrom(start.toISOString().split('T')[0])
        setDateTo(end.toISOString().split('T')[0])
    }

    const openDatePicker = (input: HTMLInputElement | null) => {
        if (!input) {
            return
        }

        if ('showPicker' in input && typeof input.showPicker === 'function') {
            input.showPicker()
            return
        }

        input.focus()
        input.click()
    }

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
            let cancelled = false
            const timer = window.setTimeout(() => {
                void downloadExportFile(exportStatus.task_id, exportDownloadToken)
                    .catch(() => {
                        if (!cancelled) {
                            setExportError('Le telechargement du fichier a echoue.')
                        }
                    })
                    .finally(() => {
                        if (!cancelled) {
                            setExportTaskId(null)
                            setExportDownloadToken(null)
                            setExportStartTime(null)
                        }
                    })
            }, 300)
            return () => {
                cancelled = true
                window.clearTimeout(timer)
            }
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
                        <div className="flex items-center gap-2 bg-[#137fec]/10 text-brand-400 px-3 py-1.5 rounded-lg border border-brand-500/20 text-sm font-medium">
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
                <div className="bg-red-500/10 bg-red-500/10 border border-status-error/20 p-4 rounded-lg flex items-start gap-3">
                    <AlertCircle className="w-5 h-5 text-red-600 dark:text-red-400 shrink-0 mt-0.5" />
                    <p className="text-sm text-red-600 dark:text-red-400">{exportError}</p>
                </div>
            )}

            {/* Filters */}
            <form onSubmit={handleFilterSubmit} className="bg-surface-800/80 backdrop-blur-md border border-surface-700 p-4 rounded-xl flex flex-col sm:flex-row gap-4 items-end">
                <div className="flex-1 w-full">
                    <label className="block text-xs font-medium text-text-secondary mb-1">Date de début</label>
                    <div className="relative">
                        <input
                            ref={dateFromInputRef}
                            type="date"
                            value={dateFrom}
                            onChange={(e) => setDateFrom(e.target.value)}
                            className="w-full bg-surface-900 border border-surface-700 rounded-lg pl-3 pr-10 py-2 text-sm text-text-primary focus:outline-none focus:ring-2 focus:ring-brand-500"
                        />
                        <button
                            type="button"
                            onClick={() => openDatePicker(dateFromInputRef.current)}
                            className="absolute right-2 top-1/2 -translate-y-1/2 p-1.5 rounded-md text-text-secondary hover:text-text-primary hover:bg-surface-700"
                            aria-label="Choisir la date de début"
                        >
                            <Calendar className="w-4 h-4" />
                        </button>
                    </div>
                </div>
                <div className="flex-1 w-full">
                    <label className="block text-xs font-medium text-text-secondary mb-1">Date de fin</label>
                    <div className="relative">
                        <input
                            ref={dateToInputRef}
                            type="date"
                            value={dateTo}
                            onChange={(e) => setDateTo(e.target.value)}
                            className="w-full bg-surface-900 border border-surface-700 rounded-lg pl-3 pr-10 py-2 text-sm text-text-primary focus:outline-none focus:ring-2 focus:ring-brand-500"
                        />
                        <button
                            type="button"
                            onClick={() => openDatePicker(dateToInputRef.current)}
                            className="absolute right-2 top-1/2 -translate-y-1/2 p-1.5 rounded-md text-text-secondary hover:text-text-primary hover:bg-surface-700"
                            aria-label="Choisir la date de fin"
                        >
                            <Calendar className="w-4 h-4" />
                        </button>
                    </div>
                </div>
                <div className="w-full sm:w-auto flex items-center gap-2">
                    <button
                        type="button"
                        onClick={() => applyQuickRange(1)}
                        className="px-2.5 py-2 rounded-md text-xs bg-surface-900 border border-surface-700 text-text-secondary hover:text-text-primary"
                    >
                        Aujourd'hui
                    </button>
                    <button
                        type="button"
                        onClick={() => applyQuickRange(7)}
                        className="px-2.5 py-2 rounded-md text-xs bg-surface-900 border border-surface-700 text-text-secondary hover:text-text-primary"
                    >
                        7 jours
                    </button>
                    <button
                        type="button"
                        onClick={() => applyQuickRange(30)}
                        className="px-2.5 py-2 rounded-md text-xs bg-surface-900 border border-surface-700 text-text-secondary hover:text-text-primary"
                    >
                        30 jours
                    </button>
                </div>
                {user?.role === 'admin' && (
                    <div className="flex-1 w-full">
                        <label className="block text-xs font-medium text-text-secondary mb-1">Agence</label>
                        <div className="relative">
                            <LayoutDashboard className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-text-muted" />
                            <select
                                value={selectedOffice}
                                onChange={(e) => setSelectedOffice(e.target.value)}
                                className="w-full bg-surface-900 border border-surface-700 rounded-lg pl-10 pr-3 py-2 text-sm text-text-primary focus:outline-none focus:ring-2 focus:ring-brand-500 appearance-none"
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
                    <div key={idx} className="bg-surface-800/80 backdrop-blur-md border border-surface-700 p-4 rounded-xl flex flex-col justify-center">
                        <span className="text-xs font-medium text-text-secondary truncate">{kpi.label}</span>
                        {isLoading ? (
                            <div className="h-7 mt-1 w-24 bg-surface-900 rounded animate-pulse" />
                        ) : (
                            <span className={`text-xl font-bold mt-1 ${kpi.highlightPositive && kpi.value > 0 ? 'text-emerald-400' : 'text-brand-400'}`}>
                                {kpi.format(kpi.value)}
                            </span>
                        )}
                    </div>
                ))}
            </div>

            {/* Data Table */}
            <div className="bg-surface-800/80 backdrop-blur-md border border-surface-700 rounded-xl overflow-hidden min-h-[400px]">
                {isError ? (
                    <div className="p-8 text-center text-red-600 dark:text-red-400">
                        <AlertCircle className="w-8 h-8 mx-auto mb-2 opacity-80" />
                        <p>{reportErrorMessage}</p>
                    </div>
                ) : (
                    <DataTable
                        data={visibleRows}
                        columns={columns}
                        isLoading={isLoading}
                        emptyMessage='Aucun voyage trouvé pour cette période.'
                        pageCount={1}
                        pageIndex={0}
                        onPageChange={() => { }}
                    />
                )}
            </div>
        </div>
    )
}
