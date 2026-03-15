export type SettlementStatus = 'pending' | 'partial' | 'settled' | 'disputed'

export interface SettlementPreview {
    settlement_id: number
    status: SettlementStatus
    office_name: string
    expected_total_cash: number
    expenses_to_reimburse: number
    net_cash_expected: number
    agency_presale_total: number
    outstanding_cargo_delivery: number
}

export interface Settlement {
    id: number
    trip: number
    trip_status: string
    office: number
    office_name: string
    conductor: number
    conductor_name: string
    settled_by: number | null
    settled_by_name: string | null
    origin_name: string
    destination_name: string
    expected_passenger_cash: number
    expected_cargo_cash: number
    expected_total_cash: number
    agency_presale_total: number
    outstanding_cargo_delivery: number
    expenses_to_reimburse: number
    net_cash_expected: number
    actual_cash_received: number | null
    actual_expenses_reimbursed: number
    discrepancy_amount: number | null
    reimbursement_gap: number
    status: SettlementStatus
    notes: string
    dispute_reason: string
    calculation_snapshot: Record<string, unknown>
    created_at: string
    updated_at: string
    settled_at: string | null
    disputed_at: string | null
    resolved_at: string | null
}

export interface SettlementListItem {
    id: number
    trip: number
    office: number
    office_name: string
    conductor: number
    conductor_name: string
    origin_name: string
    destination_name: string
    status: SettlementStatus
    expected_total_cash: number
    expenses_to_reimburse: number
    net_cash_expected: number
    actual_cash_received: number | null
    actual_expenses_reimbursed: number
    discrepancy_amount: number | null
    created_at: string
    settled_at: string | null
    disputed_at: string | null
    resolved_at: string | null
}

export interface PaginatedResponse<T> {
    count: number
    next: string | null
    previous: string | null
    results: T[]
}
