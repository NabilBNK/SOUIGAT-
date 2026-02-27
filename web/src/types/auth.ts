export type Role = 'admin' | 'office_staff' | 'conductor' | 'driver'
export type Department = 'all' | 'cargo' | 'passenger' | null

export interface User {
    id: number
    phone: string // Matches backend serializer
    first_name: string
    last_name: string
    role: Role
    department: Department
    office: number | null
    office_name?: string
    device_id: string | null
    device_bound_at: string | null
    is_active: boolean
}

export interface LoginRequest {
    phone: string
    password: string
    device_id?: string
    platform: 'web' | 'mobile'
}

export interface TokenPair {
    access: string
    refresh: string
}

export interface AuthState {
    user: User | null
    accessToken: string | null
    refreshToken: string | null
    isAuthenticated: boolean
    isLoading: boolean
}
