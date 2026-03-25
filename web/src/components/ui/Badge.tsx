import type { ReactNode } from 'react'

export function Badge({ children, variant = 'default' }: { children: ReactNode; variant?: 'default' | 'success' | 'warning' | 'error' | 'brand' }) {
    const variants = {
        default: 'bg-surface-700 text-text-secondary border border-surface-600 shadow-inner',
        success: 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20',
        warning: 'bg-accent-500/10 text-accent-400 border border-accent-500/20',
        error: 'bg-red-500/10 text-red-400 border border-red-500/20',
        brand: 'bg-brand-500/15 text-brand-400 border border-brand-500/20 shadow-sm',
    }

    return (
        <span className={`inline-flex items-center px-2.5 py-0.5 rounded-md text-[10px] font-bold tracking-wider uppercase ${variants[variant]}`}>
            {children}
        </span>
    )
}
