import type { ReactNode } from 'react'

export function Badge({ children, variant = 'default' }: { children: ReactNode; variant?: 'default' | 'success' | 'warning' | 'error' | 'brand' }) {
    const variants = {
        default: 'bg-surface-600 text-text-secondary',
        success: 'bg-status-success/15 text-status-success',
        warning: 'bg-status-warning/15 text-status-warning',
        error: 'bg-status-error/15 text-status-error',
        brand: 'bg-brand-500/15 text-brand-400',
    }

    return (
        <span className={`inline-flex items-center px-2 py-0.5 rounded-sm text-[11px] font-semibold tracking-wide uppercase ${variants[variant]}`}>
            {children}
        </span>
    )
}
