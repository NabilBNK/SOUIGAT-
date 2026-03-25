import { Outlet } from 'react-router-dom'
import { Sidebar } from './Sidebar'
import { Header } from './Header'

export function AppShell() {
    return (
        <div className="min-h-screen bg-surface-900 flex">
            <Sidebar />
            <div className="flex-1 ml-64 flex flex-col min-w-0">
                <Header />
                <main className="p-8 flex-1 overflow-x-hidden">
                    <Outlet />
                </main>
            </div>
        </div>
    )
}
