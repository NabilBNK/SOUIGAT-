export interface DailyReport {
    date: string
    office_name: string
    office_id: number
    total_trips: number
    total_passengers: number
    total_cargo: number
    passenger_revenue: number
    cargo_revenue: number
    expense_total: number
    net_revenue: number
}

export interface TripReport {
    trip: {
        id: number
        origin: string
        destination: string
        status: string
        departure: string
        arrival: string | null
        conductor: string
        bus: string
    }
    passenger_tickets: Array<{
        ticket_number: string
        passenger_name: string
        price: number
        status: string
    }>
    cargo_tickets: Array<{
        ticket_number: string
        sender_name: string
        receiver_name: string
        cargo_tier: string
        price: number
        status: string
    }>
    expenses: Array<{
        description: string
        amount: number
        created_by: string
    }>
    summary: {
        passenger_revenue: number
        cargo_revenue: number
        total_expenses: number
        net_revenue: number
        currency: string
    }
}

export interface ExportStatus {
    task_id: string
    status: 'PENDING' | 'STARTED' | 'SUCCESS' | 'FAILURE'
    progress?: number
    download_url?: string
    error?: string
}
