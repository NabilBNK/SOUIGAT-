import client from './client'

export interface TripExpense {
    id: number
    trip: number
    description: string
    amount: number
    currency: string
    category?: string
    created_by?: number
    created_by_name?: string
    created_at: string
    updated_at?: string
}

export async function getTripExpenses(tripId: number): Promise<TripExpense[]> {
    const response = await client.get<{ count?: number; results?: TripExpense[] } | TripExpense[]>('/expenses/', {
        params: { trip: tripId },
    })

    const payload = response.data
    if (Array.isArray(payload)) {
        return payload
    }
    return payload.results ?? []
}

export async function createTripExpense(
    tripId: number,
    data: { description: string; amount: number }
): Promise<TripExpense> {
    const response = await client.post<TripExpense>('/expenses/', {
        ...data,
        trip: tripId,
    })
    return response.data
}

export async function deleteTripExpense(id: number): Promise<void> {
    await client.delete(`/expenses/${id}/`)
}
