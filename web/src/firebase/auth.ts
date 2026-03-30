import { getAuth, signInWithCustomToken, signOut } from 'firebase/auth'
import { getFirebaseCustomToken } from '../api/auth'
import { getFirebaseApp, isFirebaseConfigured } from './config'

const BLOCKED_UNTIL_STORAGE_KEY = 'souigat_firebase_auth_blocked_until'
const TOKEN_REQUEST_COOLDOWN_503_MS = 5 * 1000
const TOKEN_REQUEST_COOLDOWN_DEFAULT_MS = 3 * 1000
const FAILURE_LOG_THROTTLE_MS = 60 * 1000
const MAX_PERSISTED_BLOCK_MS = 10 * 1000

let signInPromise: Promise<boolean> | null = null
let tokenRequestBlockedUntil = readPersistedBlockedUntil()
let lastFailureLogAt = 0

function readPersistedBlockedUntil(): number {
    try {
        const raw = sessionStorage.getItem(BLOCKED_UNTIL_STORAGE_KEY)
        const parsed = raw ? Number(raw) : 0
        if (!Number.isFinite(parsed) || parsed <= 0) {
            return 0
        }
        const now = Date.now()
        return Math.min(parsed, now + MAX_PERSISTED_BLOCK_MS)
    } catch {
        return 0
    }
}

function persistBlockedUntil(value: number): void {
    tokenRequestBlockedUntil = value
    try {
        if (value > 0) {
            sessionStorage.setItem(BLOCKED_UNTIL_STORAGE_KEY, String(value))
        } else {
            sessionStorage.removeItem(BLOCKED_UNTIL_STORAGE_KEY)
        }
    } catch {
        // ignore storage failures
    }
}

function getErrorStatusCode(error: unknown): number | null {
    if (!error || typeof error !== 'object') {
        return null
    }
    const response = (error as { response?: { status?: unknown } }).response
    if (!response || typeof response.status !== 'number') {
        return null
    }
    return response.status
}

function maybeLogFirebaseFailure(error: unknown): void {
    const now = Date.now()
    if ((now - lastFailureLogAt) < FAILURE_LOG_THROTTLE_MS) {
        return
    }
    lastFailureLogAt = now
    console.warn('[FIREBASE] Failed to sign in with custom token.', error)
}

export async function ensureFirebaseSession(forceRefresh = false): Promise<boolean> {
    if (!isFirebaseConfigured()) {
        return false
    }

    const app = getFirebaseApp()
    if (!app) {
        return false
    }

    const auth = getAuth(app)

    const now = Date.now()
    if (now < tokenRequestBlockedUntil) {
        return false
    }

    if (forceRefresh && auth.currentUser) {
        await signOut(auth)
    }

    if (auth.currentUser) {
        return true
    }

    if (signInPromise) {
        return signInPromise
    }

    signInPromise = (async () => {
        try {
            const { token } = await getFirebaseCustomToken('web')
            await signInWithCustomToken(auth, token)
            persistBlockedUntil(0)
            return true
        } catch (error) {
            const statusCode = getErrorStatusCode(error)
            const cooldownMs = statusCode === 503
                ? TOKEN_REQUEST_COOLDOWN_503_MS
                : TOKEN_REQUEST_COOLDOWN_DEFAULT_MS
            persistBlockedUntil(Date.now() + cooldownMs)
            maybeLogFirebaseFailure(error)
            return false
        }
    })().finally(() => {
        signInPromise = null
    })

    return signInPromise
}

export async function disconnectFirebaseSession(): Promise<void> {
    if (!isFirebaseConfigured()) {
        return
    }

    const app = getFirebaseApp()
    if (!app) {
        return
    }

    const auth = getAuth(app)
    if (!auth.currentUser) {
        return
    }

    await signOut(auth)
}
