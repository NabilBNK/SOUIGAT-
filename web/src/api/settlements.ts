import client from './client'
import type {
    PaginatedResponse,
    Settlement,
    SettlementListItem,
} from '../types/settlement'

export async function initiateSettlement(tripId: number): Promise<Settlement> {
    const response = await client.post<Settlement>(`/settlements/initiate/${tripId}/`)
    return response.data
}

export async function getSettlement(tripId: number): Promise<Settlement> {
    const response = await client.get<Settlement>(`/settlements/${tripId}/`)
    return response.data
}

export async function recordSettlement(
    tripId: number,
    data: {
        actual_cash_received: number
        actual_expenses_reimbursed?: number
        notes?: string
    }
): Promise<Settlement> {
    const response = await client.patch<Settlement>(`/settlements/${tripId}/record/`, data)
    return response.data
}

export async function disputeSettlement(
    tripId: number,
    data: { dispute_reason: string; notes?: string }
): Promise<Settlement> {
    const response = await client.patch<Settlement>(`/settlements/${tripId}/dispute/`, data)
    return response.data
}

export async function resolveSettlement(
    tripId: number,
    data: {
        actual_cash_received?: number
        actual_expenses_reimbursed?: number
        notes: string
    }
): Promise<Settlement> {
    const response = await client.patch<Settlement>(`/settlements/${tripId}/resolve/`, data)
    return response.data
}

export async function getSettlements(params?: {
    office_id?: number
    status?: string
    conductor_id?: number
    date_from?: string
    date_to?: string
    page?: number
    page_size?: number
}): Promise<PaginatedResponse<SettlementListItem>> {
    const response = await client.get<PaginatedResponse<SettlementListItem>>('/settlements/', { params })
    return response.data
}

export async function getPendingSettlements(params?: {
    page?: number
    page_size?: number
}): Promise<PaginatedResponse<SettlementListItem>> {
    const response = await client.get<PaginatedResponse<SettlementListItem>>('/settlements/pending/', { params })
    return response.data
}
