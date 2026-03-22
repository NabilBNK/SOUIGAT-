import { Loader2 } from 'lucide-react'

export function LoadingSpinner({ size = 'md' }: { size?: 'sm' | 'md' | 'lg' }) {
    const sizes = { sm: 'w-4 h-4', md: 'w-6 h-6', lg: 'w-8 h-8' }
    return <Loader2 className={`${sizes[size]} animate-spin text-[#137fec]`} />
}

export function PageLoader() {
    return (
        <div className="flex items-center justify-center py-20">
            <LoadingSpinner size="lg" />
        </div>
    )
}
