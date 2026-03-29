import type { SettlementPreview } from './settlement'

export type TripStatus = 'scheduled' | 'in_progress' | 'completed' | 'cancelled'

export interface Trip {
    id: number
    origin_office: number
    origin_office_name: string
    destination_office: number
    destination_office_name: string
    bus: number
    bus_plate: string
    route_template: number | null
    route_template_name: string
    conductor: number
    conductor_name: string
    departure_datetime: string
    arrival_datetime: string | null
    status: TripStatus
    passenger_base_price: number
    cargo_small_price: number
    cargo_medium_price: number
    cargo_large_price: number
    currency: string
    passenger_count?: number
    cargo_count?: number
    expense_total?: number
    created_at: string
    updated_at: string
}

export interface RouteTemplateStopSnapshot {
    office_id: number
    office_name: string
    stop_order: number
}

export interface RouteTemplateSegmentSnapshot {
    from_stop_order: number
    to_stop_order: number
    passenger_price: number
    currency: string
}

export interface RouteTemplateRef {
    id: number
    name: string
    code: string
    direction: 'forward' | 'reverse'
    start_office_id: number
    start_office_name: string
    end_office_id: number
    end_office_name: string
    stops: RouteTemplateStopSnapshot[]
    segment_tariffs: RouteTemplateSegmentSnapshot[]
}

export interface TripCreate {
    route_template: number
    bus: number
    conductor: number
    departure_datetime: string
}

export interface TripFilters {
    status?: TripStatus
    origin_office?: number
    destination_office?: number
    date_from?: string
    date_to?: string
    page?: number
    page_size?: number
}

export interface TripActionResponse {
    status: TripStatus
    arrival_datetime?: string | null
    settlement_preview?: SettlementPreview | null
    settlement_preview_error?: string | null
}
