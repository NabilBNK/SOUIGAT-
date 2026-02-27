import client from './client'
import type { CargoTicket, CargoStatus } from '../types/ticket'

export async function getTripCargoTickets(tripId: number): Promise<CargoTicket[]> {
    const response = await client.get<{ count: number; results: CargoTicket[] }>(`/cargo/`, { params: { trip: tripId } })
    return response.data.results
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
