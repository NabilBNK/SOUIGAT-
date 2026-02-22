import dayjs from 'dayjs'
import 'dayjs/locale/fr'

dayjs.locale('fr')

export function formatCurrency(amount: number, currency = 'DZD'): string {
    if (currency === 'DZD') {
        const formatter = new Intl.NumberFormat('fr-DZ', {
            style: 'decimal',
            minimumFractionDigits: 2,
            maximumFractionDigits: 2,
        })
        return `${formatter.format(amount)} DA`
    }
    return `${amount.toLocaleString()} ${currency}`
}

export function formatDate(date: string): string {
    return dayjs(date).format('DD/MM/YYYY')
}

export function formatDateTime(date: string): string {
    return dayjs(date).format('DD/MM/YYYY HH:mm')
}

export function formatRelativeTime(date: string): string {
    const diff = dayjs().diff(dayjs(date), 'hour')
    if (diff < 1) return 'Il y a moins d\'une heure'
    if (diff < 24) return `Il y a ${diff}h`
    if (diff < 48) return 'Hier'
    return formatDate(date)
}

export function isStale(date: string, hoursThreshold = 4): boolean {
    return dayjs().diff(dayjs(date), 'hour') >= hoursThreshold
}
