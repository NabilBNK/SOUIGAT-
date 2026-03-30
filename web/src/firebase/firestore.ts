import {
    getFirestore,
    initializeFirestore,
    persistentLocalCache,
    persistentMultipleTabManager,
    type Firestore,
} from 'firebase/firestore'
import { getFirebaseApp } from './config'

let firestoreInstance: Firestore | null = null

export function getFirebaseFirestore(): Firestore | null {
    if (firestoreInstance) {
        return firestoreInstance
    }

    const app = getFirebaseApp()
    if (!app) {
        return null
    }

    try {
        firestoreInstance = initializeFirestore(app, {
            localCache: persistentLocalCache({
                tabManager: persistentMultipleTabManager(),
            }),
        })
    } catch {
        firestoreInstance = getFirestore(app)
    }

    return firestoreInstance
}
