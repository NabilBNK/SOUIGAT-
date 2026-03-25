import { getFirestore, type Firestore } from 'firebase/firestore'
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

    firestoreInstance = getFirestore(app)
    return firestoreInstance
}
