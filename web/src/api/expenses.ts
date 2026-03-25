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
    const response = await client.get<TripExpense[]>(`/trips/${tripId}/expenses/`)
    return response.data
}

export async function createTripExpense(
    tripId: number,
    data: { description: string; amount: number }
): Promise<TripExpense> {
    const response = await client.post<TripExpense>(`/trips/${tripId}/expenses/`, data)
    return response.data
}

export async function deleteTripExpense(id: number): Promise<void> {
    await client.delete(`/expenses/${id}/`)
}
