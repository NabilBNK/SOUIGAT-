import type { ReactNode } from 'react'

interface CardProps {
    children: ReactNode
    className?: string
}

export function Card({ children, className = '' }: CardProps) {
    return (
        <div className={`bg-surface-800 border border-surface-600/50 rounded-lg ${className}`}>
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
        <Card className="p-5 animate-fade-in">
            <div className="flex items-start justify-between">
                <div>
                    <p className="text-[12px] font-medium text-text-muted uppercase tracking-wide">{label}</p>
                    <p className="text-2xl font-bold text-text-primary mt-1">{value}</p>
                    {trend && (
                        <p className={`text-[12px] mt-1 font-medium ${trend.positive ? 'text-status-success' : 'text-status-error'}`}>
                            {trend.value}
                        </p>
                    )}
                </div>
                {icon && (
                    <div className="w-10 h-10 rounded-md bg-brand-500/10 flex items-center justify-center text-brand-400">
                        {icon}
                    </div>
                )}
            </div>
        </Card>
    )
}
