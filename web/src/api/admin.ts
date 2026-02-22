import client from './client'
import type { Office, Bus, PricingConfig, AuditLogEntry, QuarantinedSync } from '../types/admin'
import type { User } from '../types/auth'

// Users
export async function getUsers(params?: Record<string, unknown>): Promise<{ count: number; results: User[] }> {
    const response = await client.get('/admin/users/', { params })
    return response.data
}

export async function createUser(data: Partial<User> & { password: string }): Promise<User> {
    const response = await client.post<User>('/admin/users/', data)
    return response.data
}

export async function updateUser(id: number, data: Partial<User>): Promise<User> {
    const response = await client.patch<User>(`/admin/users/${id}/`, data)
    return response.data
}

export async function deleteUser(id: number): Promise<void> {
    await client.delete(`/admin/users/${id}/`)
}

export async function revokeDevice(userId: number): Promise<void> {
    await client.post(`/admin/users/${userId}/revoke-device/`)
}

// Buses
export async function getBuses(params?: Record<string, unknown>): Promise<{ count: number; results: Bus[] }> {
    const response = await client.get('/admin/buses/', { params })
    return response.data
}

export async function createBus(data: Partial<Bus>): Promise<Bus> {
    const response = await client.post<Bus>('/admin/buses/', data)
    return response.data
}

export async function updateBus(id: number, data: Partial<Bus>): Promise<Bus> {
    const response = await client.patch<Bus>(`/admin/buses/${id}/`, data)
    return response.data
}

export async function deleteBus(id: number): Promise<void> {
    await client.delete(`/admin/buses/${id}/`)
}

// Offices
export async function getOffices(params?: Record<string, unknown>): Promise<{ count: number; results: Office[] }> {
    const response = await client.get('/admin/offices/', { params })
    return response.data
}

export async function createOffice(data: Partial<Office>): Promise<Office> {
    const response = await client.post<Office>('/admin/offices/', data)
    return response.data
}

export async function updateOffice(id: number, data: Partial<Office>): Promise<Office> {
    const response = await client.patch<Office>(`/admin/offices/${id}/`, data)
    return response.data
}

// Pricing
export async function getPricingConfigs(): Promise<PricingConfig[]> {
    const response = await client.get<PricingConfig[]>('/admin/pricing/')
    return response.data
}

export async function createPricing(data: Partial<PricingConfig>): Promise<PricingConfig> {
    const response = await client.post<PricingConfig>('/admin/pricing/', data)
    return response.data
}

export async function updatePricing(
    id: number,
    data: Partial<PricingConfig>
): Promise<PricingConfig> {
    const response = await client.patch<PricingConfig>(`/admin/pricing/${id}/`, data)
    return response.data
}

// Audit Log
export async function getAuditLogs(params?: {
    date_from?: string
    date_to?: string
    user?: number
    table_name?: string
    action?: string
    page?: number
}): Promise<{ count: number; results: AuditLogEntry[] }> {
    const response = await client.get('/admin/audit-log/', { params })
    return response.data
}

// Quarantine
export async function getQuarantinedSyncs(params?: Record<string, unknown>): Promise<{ count: number; results: QuarantinedSync[] }> {
    const response = await client.get('/quarantine/', { params })
    return response.data
}

export async function reviewQuarantinedSync(
    id: number,
    data: { status: 'approved' | 'rejected'; review_notes?: string }
): Promise<QuarantinedSync> {
    const response = await client.post<QuarantinedSync>(`/quarantine/${id}/review/`, data)
    return response.data
}

export async function bulkReviewQuarantinedSyncs(
    data: { ids: number[]; action: 'approve' | 'reject'; review_notes?: string }
): Promise<{ processed: number; reprocess_errors: number; skipped: number }> {
    const response = await client.post('/quarantine/bulk_review/', data)
    return response.data
}
