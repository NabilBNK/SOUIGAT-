import { getApp, getApps, initializeApp, type FirebaseApp, type FirebaseOptions } from 'firebase/app'

function readEnv(key: string): string {
    const value = import.meta.env[key] as string | undefined
    return value?.trim() ?? ''
}

const firebaseOptions: FirebaseOptions = {
    apiKey: readEnv('VITE_FIREBASE_API_KEY'),
    authDomain: readEnv('VITE_FIREBASE_AUTH_DOMAIN'),
    projectId: readEnv('VITE_FIREBASE_PROJECT_ID'),
    storageBucket: readEnv('VITE_FIREBASE_STORAGE_BUCKET'),
    messagingSenderId: readEnv('VITE_FIREBASE_MESSAGING_SENDER_ID'),
    appId: readEnv('VITE_FIREBASE_APP_ID'),
}

function hasRequiredFirebaseConfig(options: FirebaseOptions): boolean {
    return Boolean(
        options.apiKey
        && options.authDomain
        && options.projectId
        && options.storageBucket
        && options.messagingSenderId
        && options.appId,
    )
}

export function isFirebaseConfigured(): boolean {
    return hasRequiredFirebaseConfig(firebaseOptions)
}

export function getFirebaseApp(): FirebaseApp | null {
    if (!isFirebaseConfigured()) {
        return null
    }

    if (getApps().length > 0) {
        return getApp()
    }

    return initializeApp(firebaseOptions)
}
