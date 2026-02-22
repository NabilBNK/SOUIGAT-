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
    const baseClass = "inline-flex items-center justify-center font-semibold transition-colors focus:outline-none rounded-lg disabled:opacity-50 disabled:cursor-not-allowed"

    const variantClasses = {
        primary: "bg-brand-500 hover:bg-brand-600 text-white shadow-sm shadow-brand-500/20",
        secondary: "bg-surface-700 hover:bg-surface-600 text-text-primary border border-surface-600/50 shadow-sm",
        danger: "bg-status-error hover:bg-status-error/90 text-white shadow-sm shadow-status-error/20",
        ghost: "bg-transparent hover:bg-surface-700 text-text-secondary hover:text-text-primary",
    }

    const sizeClasses = {
        sm: "px-3 py-1.5 text-xs gap-1.5",
        md: "px-4 py-2.5 text-sm gap-2",
        lg: "px-6 py-3 text-base gap-2.5",
    }

    return (
        <button
            className={`${baseClass} ${variantClasses[variant]} ${sizeClasses[size]} ${className}`}
            disabled={disabled || isLoading}
            {...props}
        >
            {isLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : icon}
            {children}
        </button>
    )
}
