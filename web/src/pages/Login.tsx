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
                    // Collect field-level errors
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
        <div className="min-h-screen bg-[#101922] flex items-center justify-center p-4">
            {/* Background pattern */}
            <div className="fixed inset-0 overflow-hidden pointer-events-none">
                <div className="absolute -top-40 -right-40 w-[600px] h-[600px] bg-[#137fec]/5 rounded-full blur-3xl" />
                <div className="absolute -bottom-40 -left-40 w-[500px] h-[500px] bg-accent-500/3 rounded-full blur-3xl" />
            </div>

            <div className="w-full max-w-sm relative z-10 animate-fade-in">
                {/* Logo */}
                <div className="text-center mb-8">
                    <div className="w-14 h-14 rounded-lg bg-[#137fec] flex items-center justify-center mx-auto mb-4 shadow-lg shadow-brand-500/20">
                        <Bus className="w-7 h-7 text-white" />
                    </div>
                    <h1 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-slate-100">SOUIGAT</h1>
                    <p className="text-sm text-slate-400 dark:text-slate-500 mt-1">Gestion de transport et colis</p>
                </div>

                {/* Form */}
                <form
                    onSubmit={handleSubmit}
                    className="bg-white dark:bg-[#1a2634] border border-slate-200 dark:border-slate-800 rounded-lg p-6 space-y-5"
                >
                    <div>
                        <label htmlFor="phone" className="block text-[13px] font-medium text-slate-600 dark:text-slate-400 mb-1.5">
                            Numéro de téléphone
                        </label>
                        <input
                            id="phone"
                            type="tel"
                            value={phone}
                            onChange={(e) => setPhone(e.target.value)}
                            placeholder="05XXXXXXXX"
                            pattern="^0[5-79][0-9]{8}$"
                            title="Format attendu: 05, 06, 07 ou 09 suivi de 8 chiffres (ex: 0550123456)"
                            required
                            autoComplete="tel"
                            className="w-full px-3.5 py-2.5 bg-slate-100 dark:bg-[#1e293b] border border-slate-200 dark:border-slate-800 rounded-md text-sm text-slate-900 dark:text-slate-100 placeholder:text-slate-400 dark:text-slate-500 focus:outline-none focus:ring-2 focus:ring-brand-500/50 focus:border-brand-500/50 transition-all"
                        />
                    </div>

                    <div>
                        <label htmlFor="password" className="block text-[13px] font-medium text-slate-600 dark:text-slate-400 mb-1.5">
                            Mot de passe
                        </label>
                        <input
                            id="password"
                            type="password"
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            placeholder="••••••••"
                            required
                            autoComplete="current-password"
                            className="w-full px-3.5 py-2.5 bg-slate-100 dark:bg-[#1e293b] border border-slate-200 dark:border-slate-800 rounded-md text-sm text-slate-900 dark:text-slate-100 placeholder:text-slate-400 dark:text-slate-500 focus:outline-none focus:ring-2 focus:ring-brand-500/50 focus:border-brand-500/50 transition-all"
                        />
                    </div>

                    {error && (
                        <div className="flex items-center gap-2 px-3 py-2.5 bg-red-50 dark:bg-red-900/20 border border-status-error/20 rounded-md">
                            <AlertCircle className="w-4 h-4 text-red-600 dark:text-red-400 shrink-0" />
                            <p className="text-[13px] text-red-600 dark:text-red-400">{error}</p>
                        </div>
                    )}

                    <button
                        type="submit"
                        disabled={loading}
                        className="w-full py-2.5 bg-[#137fec] hover:bg-[#0b5ed7] text-white text-sm font-semibold rounded-md transition-colors duration-150 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
                    >
                        {loading && <Loader2 className="w-4 h-4 animate-spin" />}
                        {loading ? 'Connexion...' : 'Se connecter'}
                    </button>
                </form>

                <p className="text-center text-[11px] text-slate-400 dark:text-slate-500 mt-6">
                    © 2026 SOUIGAT — Tous droits réservés
                </p>
            </div>
        </div>
    )
}
