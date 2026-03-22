import type { ReactNode } from 'react'

export function Badge({ children, variant = 'default' }: { children: ReactNode; variant?: 'default' | 'success' | 'warning' | 'error' | 'brand' }) {
    const variants = {
        default: 'bg-slate-200 dark:bg-slate-700 text-slate-600 dark:text-slate-400',
        success: 'bg-emerald-100 dark:bg-emerald-900/30 text-emerald-600 dark:text-emerald-400',
        warning: 'bg-yellow-100 dark:bg-yellow-900/30 text-yellow-600 dark:text-yellow-400',
        error: 'bg-red-100 dark:bg-red-900/30 text-red-600 dark:text-red-400',
        brand: 'bg-[#137fec]/15 dark:bg-[#137fec]/20 text-[#137fec] dark:text-[#60a5fa]',
    }

    return (
        <span className={`inline-flex items-center px-2 py-0.5 rounded-sm text-[11px] font-semibold tracking-wide uppercase ${variants[variant]}`}>
            {children}
        </span>
    )
}
