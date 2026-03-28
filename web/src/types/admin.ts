export interface Office {
    id: number
    name: string
    city: string
    address: string | null
    phone: string | null
    is_active: boolean
}

export interface Bus {
    id: number
    plate_number: string
    model: string
    office: number | null
    office_name: string | null
    capacity: number
    is_active: boolean
}

export interface PricingConfig {
    id: number
    origin_office: number
    origin_office_name: string
    destination_office: number
    destination_office_name: string
    passenger_price: number
    cargo_small_price: number
    cargo_medium_price: number
    cargo_large_price: number
    currency: string
    effective_from: string
    effective_until: string | null
    is_active: boolean
}

export interface AuditLogEntry {
    id: number
    user: number | null
    user_name: string | null
    action: 'create' | 'update' | 'delete' | 'override'
    table_name: string
    record_id: number
    old_values: Record<string, unknown> | null
    new_values: Record<string, unknown> | null
    ip_address: string | null
    created_at: string
}

export interface QuarantinedSync {
    id: number
    conductor: number
    conductor_name: string
    trip: number
    trip_info: string
    original_data: Record<string, unknown>
    reason: string
    status: 'pending' | 'approved' | 'rejected'
    reviewed_by: number | null
    reviewed_by_name: string | null
    reviewed_at: string | null
    review_notes: string | null
    created_at: string
}
