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
}): Promise<{ task_id: string }> {
    const response = await client.post<{ task_id: string }>('/exports/', payload)
    return response.data
}

export async function getExportStatus(taskId: string): Promise<ExportStatus> {
    const response = await client.get<ExportStatus>(`/exports/${taskId}/status/`)
    return response.data
}

export function getExportDownloadUrl(taskId: string): string {
    return `/api/exports/${taskId}/download/`
}
