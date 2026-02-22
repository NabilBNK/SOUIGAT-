import type { TripStatus } from '../../types/trip'
import type { CargoStatus } from '../../types/ticket'
import { TRIP_STATUS_LABELS, TRIP_STATUS_COLORS, CARGO_STATUS_COLORS } from '../../utils/constants'
import { CARGO_STATUS_LABELS } from '../../types/ticket'

interface StatusBadgeProps {
    status: TripStatus | CargoStatus
    type?: 'trip' | 'cargo'
}

export function StatusBadge({ status, type = 'trip' }: StatusBadgeProps) {
    const labels = type === 'trip' ? TRIP_STATUS_LABELS : CARGO_STATUS_LABELS
    const colors = type === 'trip' ? TRIP_STATUS_COLORS : CARGO_STATUS_COLORS
    const label = labels[status as keyof typeof labels] || status
    const color = colors[status as keyof typeof colors] || 'bg-surface-500/30 text-text-muted'

    return (
        <span className={`inline-flex items-center px-2 py-0.5 rounded-sm text-[11px] font-semibold tracking-wide uppercase ${color}`}>
            {label}
        </span>
    )
}
