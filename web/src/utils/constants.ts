import type { Role } from '../types/auth'
import type { TripStatus } from '../types/trip'
import type { CargoStatus } from '../types/ticket'

export const ROLE_LABELS: Record<Role, string> = {
    admin: 'Administrateur',
    office_staff: 'Personnel Bureau',
    conductor: 'Conducteur',
    driver: 'Chauffeur',
}

export const TRIP_STATUS_LABELS: Record<TripStatus, string> = {
    scheduled: 'Programmé',
    in_progress: 'En cours',
    completed: 'Terminé',
    cancelled: 'Annulé',
}

export const TRIP_STATUS_COLORS: Record<TripStatus, string> = {
    scheduled: 'bg-status-info/15 text-status-info',
    in_progress: 'bg-accent-500/15 text-accent-400',
    completed: 'bg-status-success/15 text-status-success',
    cancelled: 'bg-status-error/15 text-status-error',
}

export const CARGO_STATUS_COLORS: Record<CargoStatus, string> = {
    created: 'bg-surface-500/30 text-text-secondary',
    in_transit: 'bg-status-info/15 text-status-info',
    arrived: 'bg-accent-500/15 text-accent-400',
    delivered: 'bg-brand-500/15 text-brand-400',
    paid: 'bg-status-success/15 text-status-success',
    refused: 'bg-status-error/15 text-status-error',
    lost: 'bg-status-error/15 text-status-error',
    cancelled: 'bg-surface-500/30 text-text-muted',
    refunded: 'bg-status-warning/15 text-status-warning',
}

export const DEPARTMENT_LABELS: Record<string, string> = {
    all: 'Tous',
    cargo: 'Colis',
    passenger: 'Passagers',
}
