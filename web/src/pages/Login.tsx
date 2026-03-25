import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { Bus, Loader2, AlertCircle } from 'lucide-react'

export function Login() {
    const [phone, setPhone] = useState('')
    const [password, setPassword] = useState('')
    const [error, setError] = useState('')
    const [loading, setLoading] = useState(false)
    const { login } = useAuth()
    const navigate = useNavigate()

    async function handleSubmit(e: FormEvent) {
        e.preventDefault()
        setError('')
        setLoading(true)
        try {
            await login(phone, password)
            navigate('/', { replace: true })
        } catch (err: unknown) {
            const resp = (err as { response?: { data?: Record<string, unknown> } })?.response?.data
            let msg = 'Identifiants invalides'
            if (resp) {
                if (typeof resp.detail === 'string') {
                    msg = resp.detail
                } else if (Array.isArray(resp.non_field_errors)) {
                    msg = resp.non_field_errors.join(' ')
                } else {
                    const parts: string[] = []
                    for (const [, val] of Object.entries(resp)) {
                        if (Array.isArray(val)) parts.push(val.join(' '))
                        else if (typeof val === 'string') parts.push(val)
                    }
                    if (parts.length) msg = parts.join(' ')
                }
            }
            setError(msg)
        } finally {
            setLoading(false)
        }
    }

    return (
        <div className="min-h-screen bg-surface-900 flex items-center justify-center p-4">
            {/* Background premium glow */}
            <div className="fixed inset-0 overflow-hidden pointer-events-none">
                <div className="absolute -top-40 -right-40 w-[600px] h-[600px] bg-brand-500/10 rounded-full blur-3xl" />
                <div className="absolute -bottom-40 -left-40 w-[500px] h-[500px] bg-accent-500/10 rounded-full blur-3xl" />
            </div>

            <div className="w-full max-w-sm relative z-10 animate-fade-in">
                {/* Logo */}
                <div className="text-center mb-8">
                    <div className="w-16 h-16 rounded-xl bg-gradient-to-br from-brand-600 to-brand-900 flex items-center justify-center mx-auto mb-5 shadow-xl shadow-brand-900/50 border border-brand-500/20">
                        <Bus className="w-8 h-8 text-accent-400" />
                    </div>
                    <h1 className="text-3xl font-bold tracking-tight text-text-primary font-sans">SOUIGAT</h1>
                    <p className="text-sm text-text-secondary mt-1 tracking-wide uppercase font-semibold">Transport de Voyage</p>
                </div>

                {/* Form */}
                <form
                    onSubmit={handleSubmit}
                    className="bg-surface-800/80 backdrop-blur-md border border-surface-700 shadow-2xl rounded-2xl p-7 space-y-6"
                >
                    <div className="space-y-1.5">
                        <label htmlFor="phone" className="block text-sm font-medium text-text-secondary">
                            Numéro de téléphone
                        </label>
                        <input
                            id="phone"
                            name="phone"
                            type="tel"
                            value={phone}
                            onChange={(e) => setPhone(e.target.value)}
                            placeholder="05XXXXXXXX"
                            pattern="^0[5-79][0-9]{8}$"
                            title="Format attendu: 05, 06, 07 ou 09 suivi de 8 chiffres (ex: 0550123456)"
                            required
                            autoComplete="tel"
                            className="w-full px-4 py-3 bg-surface-900/50 border border-surface-600 rounded-lg text-sm text-text-primary placeholder:text-text-muted focus:outline-none focus:ring-2 focus:ring-brand-500 focus:border-brand-500 transition-all shadow-inner"
                        />
                    </div>

                    <div className="space-y-1.5">
                        <label htmlFor="password" className="block text-sm font-medium text-text-secondary">
                            Mot de passe
                        </label>
                        <input
                            id="password"
                            name="password"
                            type="password"
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            placeholder="••••••••"
                            required
                            autoComplete="current-password"
                            className="w-full px-4 py-3 bg-surface-900/50 border border-surface-600 rounded-lg text-sm text-text-primary placeholder:text-text-muted focus:outline-none focus:ring-2 focus:ring-brand-500 focus:border-brand-500 transition-all shadow-inner"
                        />
                    </div>

                    {error && (
                        <div className="flex items-center gap-3 px-4 py-3 bg-red-500/100/10 border border-red-500/20 rounded-lg">
                            <AlertCircle className="w-5 h-5 text-red-500 shrink-0" />
                            <p className="text-sm text-red-400 font-medium">{error}</p>
                        </div>
                    )}

                    <button
                        type="submit"
                        disabled={loading}
                        className="w-full py-3 bg-accent-500 hover:bg-accent-400 text-surface-900 shadow-lg shadow-accent-500/20 text-sm font-bold rounded-lg transition-all duration-200 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2 uppercase tracking-wide"
                    >
                        {loading && <Loader2 className="w-4 h-4 animate-spin text-surface-900" />}
                        {loading ? 'Connexion…' : 'Se connecter'}
                    </button>
                </form>

                <p className="text-center text-xs text-text-muted mt-8 font-medium">
                    © 2026 SOUIGAT — Tous droits réservés
                </p>
            </div>
        </div>
    )
}
