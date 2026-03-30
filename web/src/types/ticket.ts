export type TicketStatus = 'active' | 'cancelled' | 'refunded'
export type PaymentSource = 'cash' | 'prepaid'

export interface PassengerTicket {
    id: number
    trip: number
    ticket_number: string
    passenger_name: string
    price: number
    currency: string
    payment_source: PaymentSource
    seat_number: string | null
    boarding_point: string | null
    alighting_point: string | null
    status: TicketStatus
    created_by: number
    created_by_name: string
    version: number
    synced_at: string | null
    created_at: string
    updated_at: string
}

export type CargoStatus =
    | 'created'
    | 'in_transit'
    | 'arrived'
    | 'delivered'
    | 'paid'
    | 'refused'
    | 'lost'
    | 'cancelled'
    | 'refunded'

export type CargoTier = 'small' | 'medium' | 'large'
export type CargoPaymentSource = 'prepaid' | 'pay_on_delivery'

export interface CargoTicket {
    id: number
    trip: number
    trip_destination_office_id: number
    ticket_number: string
    sender_name: string
    sender_phone: string | null
    receiver_name: string
    receiver_phone: string | null
    cargo_tier: CargoTier
    description: string | null
    price: number
    currency: string
    payment_source: CargoPaymentSource
    status: CargoStatus
    status_override_reason: string | null
    status_override_by: number | null
    delivered_at: string | null
    delivered_by: number | null
    created_by: number
    created_by_name: string
    version: number
    synced_at: string | null
    created_at: string
    updated_at: string
}

export const CARGO_STATUS_LABELS: Record<CargoStatus, string> = {
    created: 'Créé',
    in_transit: 'En transit',
    arrived: 'Arrivé',
    delivered: 'Livré',
    paid: 'Payé',
    refused: 'Refusé',
    lost: 'Perdu',
    cancelled: 'Annulé',
    refunded: 'Remboursé',
}

export const VALID_TRANSITIONS: Record<CargoStatus, CargoStatus[]> = {
    created: ['in_transit', 'cancelled'],
    in_transit: ['arrived', 'lost'],
    arrived: ['delivered', 'refused'],
    delivered: ['paid', 'refunded'],
    paid: [],
    refused: ['cancelled'],
    lost: [],
    cancelled: [],
    refunded: [],
}
