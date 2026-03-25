import { type ReactNode } from 'react'
import { Loader2 } from 'lucide-react'

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
    children: ReactNode
    variant?: 'primary' | 'secondary' | 'danger' | 'ghost'
    size?: 'sm' | 'md' | 'lg'
    isLoading?: boolean
    icon?: ReactNode
}

export function Button({
    children,
    variant = 'primary',
    size = 'md',
    isLoading = false,
    icon,
    className = '',
    disabled,
    ...props
}: ButtonProps) {
    const baseClass = "inline-flex items-center justify-center font-bold tracking-wide transition-all duration-200 outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-offset-surface-900 rounded-lg disabled:opacity-50 disabled:cursor-not-allowed"

    const variantClasses = {
        primary: "bg-accent-500 hover:bg-accent-400 text-surface-900 shadow-lg shadow-accent-500/20 uppercase focus-visible:ring-accent-500",
        secondary: "bg-brand-600 hover:bg-brand-500 text-white shadow-md shadow-brand-900/30 border border-brand-500/50 focus-visible:ring-brand-500",
        danger: "bg-red-500 hover:bg-red-400 text-white shadow-md shadow-red-500/20 border border-red-400/50 focus-visible:ring-red-500",
        ghost: "bg-transparent hover:bg-surface-700 text-text-secondary hover:text-text-primary focus-visible:ring-surface-600",
    }

    const sizeClasses = {
        sm: "px-3 py-1.5 text-xs gap-1.5",
        md: "px-5 py-2.5 text-sm gap-2",
        lg: "px-8 py-3.5 text-base gap-2.5 uppercase",
    }

    return (
        <button
            className={`${baseClass} ${variantClasses[variant]} ${sizeClasses[size]} ${className}`}
            disabled={disabled || isLoading}
            {...props}
        >
            {isLoading ? <Loader2 className="w-4 h-4 animate-spin shrink-0" /> : icon}
            {children}
        </button>
    )
}
