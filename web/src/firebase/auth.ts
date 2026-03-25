import { getAuth, signInWithCustomToken, signOut } from 'firebase/auth'
import { getFirebaseCustomToken } from '../api/auth'
import { getFirebaseApp, isFirebaseConfigured } from './config'

let signInPromise: Promise<boolean> | null = null

export async function ensureFirebaseSession(forceRefresh = false): Promise<boolean> {
    if (!isFirebaseConfigured()) {
        return false
    }

    const app = getFirebaseApp()
    if (!app) {
        return false
    }

    const auth = getAuth(app)

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
            return true
        } catch (error) {
            console.warn('[FIREBASE] Failed to sign in with custom token.', error)
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
