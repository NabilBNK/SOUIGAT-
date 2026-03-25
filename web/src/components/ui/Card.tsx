import type { ReactNode } from 'react'

interface CardProps {
    children: ReactNode
    className?: string
}

export function Card({ children, className = '' }: CardProps) {
    return (
        <div className={`bg-surface-800/60 backdrop-blur-xl border border-surface-700 shadow-xl rounded-2xl ${className}`}>
            {children}
        </div>
    )
}

interface MetricCardProps {
    label: string
    value: string | number
    icon?: ReactNode
    trend?: { value: string; positive: boolean }
}

export function MetricCard({ label, value, icon, trend }: MetricCardProps) {
    return (
        <Card className="p-6 animate-fade-in relative overflow-hidden group hover:border-surface-600 transition-colors duration-300">
            {/* Soft decorative glow */}
            <div className="absolute top-0 right-0 p-8 opacity-20 bg-brand-500 blur-3xl rounded-full translate-x-1/2 -translate-y-1/2 group-hover:opacity-40 transition-opacity duration-500 pointer-events-none" />
            
            <div className="flex items-start justify-between relative z-10">
                <div>
                    <p className="text-xs font-bold text-text-secondary uppercase tracking-widest">{label}</p>
                    <p className="text-3xl font-bold text-text-primary mt-2">{value}</p>
                    {trend && (
                        <p className={`text-xs mt-2 font-bold tracking-wide ${trend.positive ? 'text-emerald-400' : 'text-red-400'}`}>
                            {trend.value}
                        </p>
                    )}
                </div>
                {icon && (
                    <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-surface-700 to-surface-800 border border-surface-600 shadow-inner flex items-center justify-center text-accent-400">
                        {icon}
                    </div>
                )}
            </div>
        </Card>
    )
}
