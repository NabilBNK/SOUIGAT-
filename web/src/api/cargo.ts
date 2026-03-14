import client from './client'
import type { CargoTicket, CargoStatus } from '../types/ticket'

interface PaginatedCargoResponse {
    count: number
    next: string | null
    previous?: string | null
    results: CargoTicket[]
}

export async function getTripCargoTickets(tripId: number): Promise<CargoTicket[]> {
    const pageSize = 100
    let page = 1
    const all: CargoTicket[] = []

    while (true) {
        const response = await client.get<PaginatedCargoResponse>('/cargo/', {
            params: { trip: tripId, page, page_size: pageSize },
        })
        all.push(...response.data.results)
        if (!response.data.next || response.data.results.length === 0) break
        page += 1
    }

    return all
}

export async function getCargoTickets(params?: Record<string, string | number>): Promise<{
    count: number
    results: CargoTicket[]
}> {
    const response = await client.get('/cargo/', { params })
    return response.data
}

export async function getCargoTicket(id: number): Promise<CargoTicket> {
    const response = await client.get<CargoTicket>(`/cargo/${id}/`)
    return response.data
}

export async function createCargoTicket(
    tripId: number,
    data: {
        sender_name: string
        sender_phone?: string
        receiver_name: string
        receiver_phone?: string
        cargo_tier: string
        description?: string
        payment_source: string
    }
): Promise<CargoTicket> {
    const response = await client.post<CargoTicket>(`/cargo/`, { ...data, trip: tripId })
    return response.data
}

export async function transitionCargoStatus(
    id: number,
    newStatus: CargoStatus,
    reason?: string
): Promise<CargoTicket> {
    const response = await client.post<CargoTicket>(`/cargo/${id}/transition/`, {
        new_status: newStatus,
        reason,
    })
    return response.data
}

export async function deliverCargoTicket(id: number): Promise<CargoTicket> {
    const response = await client.post<CargoTicket>(`/cargo/${id}/deliver/`)
    return response.data
}
