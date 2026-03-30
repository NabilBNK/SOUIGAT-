import client from './client'
import type {
    Office,
    Bus,
    AuditLogEntry,
    QuarantinedSync,
    RouteTemplate,
    RouteTemplateStop,
    RouteTemplateSegmentTariff,
} from '../types/admin'
import type { User } from '../types/auth'

const ROUTE_TEMPLATE_BASES = ['/admin/route-templates/', '/admin/route_templates/'] as const
const ROUTE_TEMPLATE_STOP_BASES = ['/admin/route-template-stops/', '/admin/route_template_stops/'] as const
const ROUTE_TEMPLATE_SEGMENT_BASES = ['/admin/route-template-segment-tariffs/', '/admin/route_template_segment_tariffs/'] as const

async function requestWithFallback<T>(
    candidates: readonly string[],
    method: 'get' | 'post' | 'patch' | 'delete',
    paramsOrData?: Record<string, unknown>,
): Promise<T> {
    let lastError: unknown
    for (let index = 0; index < candidates.length; index += 1) {
        const path = candidates[index]
        try {
            if (method === 'get') {
                const response = await client.get<T>(path, { params: paramsOrData })
                return response.data
            }
            if (method === 'post') {
                const response = await client.post<T>(path, paramsOrData)
                return response.data
            }
            if (method === 'patch') {
                const response = await client.patch<T>(path, paramsOrData)
                return response.data
            }
            await client.delete(path)
            return undefined as T
        } catch (error: any) {
            lastError = error
            if (error?.response?.status !== 404 || index === candidates.length - 1) {
                throw error
            }
        }
    }
    throw lastError
}

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

export async function bulkDeleteUsers(
    ids: number[],
    options: { hard?: boolean } = { hard: true },
): Promise<{ processed: number; deleted: number; deactivated: number; errors: Array<{ id: number; detail: string }> }> {
    const response = await client.post('/admin/users/bulk-delete/', {
        ids,
        hard: options.hard ?? true,
    })
    return response.data
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

export async function bulkDeleteOffices(
    ids: number[],
): Promise<{ processed: number; deleted: number; errors: Array<{ id: number; detail: string }> }> {
    const response = await client.post('/admin/offices/bulk-delete/', { ids })
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

// Route templates
export async function getRouteTemplates(params?: Record<string, unknown>): Promise<{ count: number; results: RouteTemplate[] }> {
    return requestWithFallback<{ count: number; results: RouteTemplate[] }>(
        ROUTE_TEMPLATE_BASES,
        'get',
        params
    )
}

export async function getRouteTemplate(id: number): Promise<RouteTemplate> {
    return requestWithFallback<RouteTemplate>(
        ROUTE_TEMPLATE_BASES.map((base) => `${base}${id}/`),
        'get'
    )
}

export async function createRouteTemplate(data: Partial<RouteTemplate>): Promise<RouteTemplate> {
    return requestWithFallback<RouteTemplate>(ROUTE_TEMPLATE_BASES, 'post', data as Record<string, unknown>)
}

export async function updateRouteTemplate(id: number, data: Partial<RouteTemplate>): Promise<RouteTemplate> {
    return requestWithFallback<RouteTemplate>(
        ROUTE_TEMPLATE_BASES.map((base) => `${base}${id}/`),
        'patch',
        data as Record<string, unknown>
    )
}

export async function deleteRouteTemplate(id: number): Promise<void> {
    await requestWithFallback<void>(
        ROUTE_TEMPLATE_BASES.map((base) => `${base}${id}/`),
        'delete'
    )
}

export async function createReverseRouteTemplate(id: number): Promise<RouteTemplate> {
    return requestWithFallback<RouteTemplate>(
        ROUTE_TEMPLATE_BASES.map((base) => `${base}${id}/create-reverse/`),
        'post',
        {}
    )
}

export async function syncRouteTemplateToFirebase(
    id: number,
): Promise<{ detail: string; template_id: number; document_id: string; collection: string; source_updated_at: string }> {
    return requestWithFallback<{ detail: string; template_id: number; document_id: string; collection: string; source_updated_at: string }>(
        ROUTE_TEMPLATE_BASES.map((base) => `${base}${id}/sync-firebase/`),
        'post',
        {}
    )
}

export async function createRouteTemplateStop(data: Partial<RouteTemplateStop>): Promise<RouteTemplateStop> {
    return requestWithFallback<RouteTemplateStop>(
        ROUTE_TEMPLATE_STOP_BASES,
        'post',
        data as Record<string, unknown>
    )
}

export async function updateRouteTemplateStop(id: number, data: Partial<RouteTemplateStop>): Promise<RouteTemplateStop> {
    return requestWithFallback<RouteTemplateStop>(
        ROUTE_TEMPLATE_STOP_BASES.map((base) => `${base}${id}/`),
        'patch',
        data as Record<string, unknown>
    )
}

export async function deleteRouteTemplateStop(id: number): Promise<void> {
    await requestWithFallback<void>(
        ROUTE_TEMPLATE_STOP_BASES.map((base) => `${base}${id}/`),
        'delete'
    )
}

export async function createRouteTemplateSegmentTariff(
    data: Partial<RouteTemplateSegmentTariff>
): Promise<RouteTemplateSegmentTariff> {
    return requestWithFallback<RouteTemplateSegmentTariff>(
        ROUTE_TEMPLATE_SEGMENT_BASES,
        'post',
        data as Record<string, unknown>
    )
}

export async function updateRouteTemplateSegmentTariff(
    id: number,
    data: Partial<RouteTemplateSegmentTariff>
): Promise<RouteTemplateSegmentTariff> {
    return requestWithFallback<RouteTemplateSegmentTariff>(
        ROUTE_TEMPLATE_SEGMENT_BASES.map((base) => `${base}${id}/`),
        'patch',
        data as Record<string, unknown>
    )
}

export async function deleteRouteTemplateSegmentTariff(id: number): Promise<void> {
    await requestWithFallback<void>(
        ROUTE_TEMPLATE_SEGMENT_BASES.map((base) => `${base}${id}/`),
        'delete'
    )
}
