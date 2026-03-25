import { Link } from 'react-router-dom'
import { ShieldAlert } from 'lucide-react'

export function NotFound() {
    return (
        <div className="min-h-screen bg-surface-900 flex items-center justify-center p-4">
            <div className="text-center animate-fade-in">
                <ShieldAlert className="w-16 h-16 mx-auto text-surface-500 mb-4" />
                <h1 className="text-4xl font-bold text-text-primary">404</h1>
                <p className="text-text-muted mt-2 mb-6">Page introuvable</p>
                <Link
                    to="/"
                    className="px-4 py-2.5 bg-[#137fec] hover:bg-[#0b5ed7] text-white text-sm font-semibold rounded-md transition-colors"
                >
                    Retour à l'accueil
                </Link>
            </div>
        </div>
    )
}

export function Unauthorized() {
    return (
        <div className="flex items-center justify-center py-20">
            <div className="text-center animate-fade-in">
                <ShieldAlert className="w-16 h-16 mx-auto text-red-600 dark:text-red-400/50 mb-4" />
                <h1 className="text-2xl font-bold text-text-primary">Accès refusé</h1>
                <p className="text-text-muted mt-2 mb-6">Vous n'avez pas les permissions nécessaires</p>
                <Link
                    to="/"
                    className="px-4 py-2.5 bg-[#137fec] hover:bg-[#0b5ed7] text-white text-sm font-semibold rounded-md transition-colors"
                >
                    Retour à l'accueil
                </Link>
            </div>
        </div>
    )
}
