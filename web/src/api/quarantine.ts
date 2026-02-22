import client from './client'
import type { QuarantinedSync } from '../types/admin'

export async function getQuarantinedSyncs(params?: {
    status?: string
    page?: number
}): Promise<{ count: number; results: QuarantinedSync[] }> {
    const response = await client.get('/quarantine/', { params })
    return response.data
}

export async function approveQuarantine(id: number): Promise<void> {
    await client.post(`/quarantine/${id}/approve/`)
}

export async function rejectQuarantine(id: number, notes: string): Promise<void> {
    await client.post(`/quarantine/${id}/reject/`, { review_notes: notes })
}
