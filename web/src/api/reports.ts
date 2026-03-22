import client from './client'
import type { DailyReport, TripReport, ExportStatus } from '../types/report'

export async function getDailyReport(params?: {
    date_from?: string
    date_to?: string
    office_id?: number
}): Promise<DailyReport[]> {
    const response = await client.get<DailyReport[]>('/reports/daily/', { params })
    return response.data
}

export async function getTripReport(tripId: number): Promise<TripReport> {
    const response = await client.get<TripReport>(`/reports/trip/${tripId}/`)
    return response.data
}

export async function getRouteAnalysis(params?: {
    date_from?: string
    date_to?: string
}): Promise<unknown> {
    const response = await client.get('/reports/route/', { params })
    return response.data
}

export async function triggerExport(payload: {
    report_type: string
    filters: Record<string, string>
}): Promise<{ task_id: string; download_token: string; status: 'pending' }> {
    const response = await client.post<{ task_id: string; download_token: string; status: 'pending' }>('/exports/', payload)
    return response.data
}

export async function getExportStatus(taskId: string): Promise<ExportStatus> {
    const response = await client.get<ExportStatus>(`/exports/${taskId}/status/`)
    return response.data
}

function getDownloadFilename(contentDisposition?: string, fallback = 'souigat-report.xlsx'): string {
    if (!contentDisposition) {
        return fallback
    }

    const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i)
    if (utf8Match?.[1]) {
        return decodeURIComponent(utf8Match[1])
    }

    const plainMatch = contentDisposition.match(/filename=\"?([^\";]+)\"?/i)
    return plainMatch?.[1] ?? fallback
}

export async function downloadExportFile(taskId: string, downloadToken: string): Promise<void> {
    const response = await client.get<Blob>(`/exports/${taskId}/download/`, {
        params: { token: downloadToken },
        responseType: 'blob',
    })

    const filename = getDownloadFilename(
        response.headers['content-disposition'],
        `souigat-export-${taskId}.xlsx`,
    )
    const downloadUrl = window.URL.createObjectURL(response.data)
    const link = document.createElement('a')
    link.href = downloadUrl
    link.download = filename
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.setTimeout(() => window.URL.revokeObjectURL(downloadUrl), 1000)
}
