import type { Department, Role } from '../types/auth'
import type { SettlementStatus } from '../types/settlement'
import type { TripStatus } from '../types/trip'
import type { CargoStatus } from '../types/ticket'

export const ROLE_LABELS: Record<Role, string> = {
    admin: 'Administrateur',
    office_staff: 'Personnel Bureau',
    conductor: 'Conducteur',
    driver: 'Chauffeur',
}

export const DEPARTMENT_LABELS: Record<Exclude<Department, null>, string> = {
    all: 'Tous services',
    cargo: 'Cargo',
}

export const TRIP_STATUS_LABELS: Record<TripStatus, string> = {
    scheduled: 'Programmé',
    in_progress: 'En cours',
    completed: 'Terminé',
    cancelled: 'Annulé',
}

export const TRIP_STATUS_COLORS: Record<TripStatus, string> = {
    scheduled: 'bg-surface-600/50 text-text-secondary border border-surface-500',
    in_progress: 'bg-brand-500/15 text-brand-400 border border-brand-500/20',
    completed: 'bg-emerald-500/15 text-emerald-400 border border-emerald-500/20',
    cancelled: 'bg-red-500/15 text-red-400 border border-red-500/20',
}

export const CARGO_STATUS_COLORS: Record<CargoStatus, string> = {
    created: 'bg-surface-600/50 text-text-secondary border border-surface-500',
    in_transit: 'bg-brand-500/15 text-brand-400 border border-brand-500/20',
    arrived: 'bg-accent-500/15 text-accent-400 border border-accent-500/20',
    delivered: 'bg-emerald-500/15 text-emerald-400 border border-emerald-500/20',
    paid: 'bg-emerald-500/15 text-emerald-400 border border-emerald-500/20',
    refused: 'bg-red-500/15 text-red-400 border border-red-500/20',
    lost: 'bg-red-500/15 text-red-400 border border-red-500/20',
    cancelled: 'bg-surface-700 text-text-muted border border-surface-600',
    refunded: 'bg-yellow-500/15 text-yellow-500 border border-yellow-500/20',
}

export const SETTLEMENT_STATUS_LABELS: Record<SettlementStatus, string> = {
    pending: 'En attente',
    partial: 'Partiel',
    settled: 'Regle',
    disputed: 'Litige',
}

export const SETTLEMENT_STATUS_COLORS: Record<SettlementStatus, string> = {
    pending: 'bg-surface-600/50 text-text-secondary border border-surface-500',
    partial: 'bg-yellow-500/15 text-yellow-500 border border-yellow-500/20',
    settled: 'bg-emerald-500/15 text-emerald-400 border border-emerald-500/20',
    disputed: 'bg-red-500/15 text-red-400 border border-red-500/20',
}
