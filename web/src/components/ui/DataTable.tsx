import {
    useReactTable,
    getCoreRowModel,
    flexRender,
    type ColumnDef,
    type OnChangeFn,
    type RowSelectionState,
} from '@tanstack/react-table'
import { ChevronLeft, ChevronRight, PackageOpen } from 'lucide-react'

interface DataTableProps<TData, TValue> {
    columns: ColumnDef<TData, TValue>[]
    data: TData[]
    pageCount: number
    pageIndex: number
    onPageChange: (page: number) => void
    isLoading?: boolean
    emptyMessage?: string
    onRowClick?: (row: TData) => void
    rowSelection?: RowSelectionState
    onRowSelectionChange?: OnChangeFn<RowSelectionState>
}

export function DataTable<TData, TValue>({
    columns,
    data,
    pageCount,
    pageIndex,
    onPageChange,
    isLoading,
    emptyMessage = 'Aucune donnée trouvée',
    onRowClick,
    rowSelection,
    onRowSelectionChange,
}: DataTableProps<TData, TValue>) {
    const table = useReactTable({
        data,
        columns,
        getCoreRowModel: getCoreRowModel(),
        manualPagination: true,
        pageCount,
        state: {
            rowSelection,
        },
        enableRowSelection: true,
        onRowSelectionChange,
    })

    return (
        <div className="bg-surface-800/80 backdrop-blur-md border border-surface-700 shadow-xl rounded-2xl overflow-hidden flex flex-col">
            <div className="overflow-x-auto">
                <table className="w-full text-sm text-left">
                    <thead className="bg-surface-900/50 border-b border-surface-700">
                        {table.getHeaderGroups().map((headerGroup) => (
                            <tr key={headerGroup.id}>
                                {headerGroup.headers.map((header) => (
                                    <th
                                        key={header.id}
                                        className="px-6 py-4 text-[11px] font-bold text-text-secondary uppercase tracking-wider whitespace-nowrap"
                                    >
                                        {header.isPlaceholder
                                            ? null
                                            : flexRender(
                                                header.column.columnDef.header,
                                                header.getContext()
                                            )}
                                    </th>
                                ))}
                            </tr>
                        ))}
                    </thead>
                    <tbody className="divide-y divide-surface-700/50">
                        {isLoading ? (
                            Array.from({ length: 5 }).map((_, i) => (
                                <tr key={i} className="animate-pulse">
                                    {columns.map((_, colIdx) => (
                                        <td key={colIdx} className="px-6 py-5">
                                            <div className="h-4 bg-surface-600/50 rounded lg:w-3/4 w-full"></div>
                                        </td>
                                    ))}
                                </tr>
                            ))
                        ) : table.getRowModel().rows?.length ? (
                            table.getRowModel().rows.map((row) => (
                                <tr
                                    key={row.id}
                                    onClick={() => onRowClick && onRowClick(row.original)}
                                    className={`group transition-colors duration-200 ${onRowClick ? 'cursor-pointer hover:bg-surface-700/60' : ''
                                        }`}
                                >
                                    {row.getVisibleCells().map((cell) => (
                                        <td key={cell.id} className="px-6 py-4 text-text-primary whitespace-nowrap font-medium">
                                            {flexRender(cell.column.columnDef.cell, cell.getContext())}
                                        </td>
                                    ))}
                                </tr>
                            ))
                        ) : (
                            <tr>
                                <td
                                    colSpan={columns.length}
                                    className="px-6 py-16 text-center text-text-muted"
                                >
                                    <PackageOpen className="w-12 h-12 mx-auto mb-4 opacity-20 text-text-secondary" />
                                    <p className="text-sm font-medium tracking-wide">{emptyMessage}</p>
                                </td>
                            </tr>
                        )}
                    </tbody>
                </table>
            </div>

            {/* Pagination control */}
            {pageCount > 1 && (
                <div className="px-6 py-4 border-t border-surface-700 flex items-center justify-between bg-surface-900/40">
                    <p className="text-xs text-text-secondary font-medium tracking-wide">
                        Page <span className="text-text-primary px-1">{pageIndex + 1}</span> sur{' '}
                        <span className="text-text-primary px-1">{pageCount}</span>
                    </p>
                    <div className="flex items-center gap-3">
                        <button
                            onClick={() => onPageChange(pageIndex - 1)}
                            disabled={pageIndex <= 0 || isLoading}
                            className="p-2 rounded-lg text-text-secondary hover:text-text-primary hover:bg-surface-700 disabled:opacity-50 disabled:cursor-not-allowed transition-all outline-none focus-visible:ring-2 focus-visible:ring-brand-500 border border-transparent hover:border-surface-600 shadow-sm"
                        >
                            <ChevronLeft className="w-4 h-4" />
                        </button>
                        <button
                            onClick={() => onPageChange(pageIndex + 1)}
                            disabled={pageIndex >= pageCount || isLoading}
                            className="p-2 rounded-lg text-text-secondary hover:text-text-primary hover:bg-surface-700 disabled:opacity-50 disabled:cursor-not-allowed transition-all outline-none focus-visible:ring-2 focus-visible:ring-brand-500 border border-transparent hover:border-surface-600 shadow-sm"
                        >
                            <ChevronRight className="w-4 h-4" />
                        </button>
                    </div>
                </div>
            )}
        </div>
    )
}
