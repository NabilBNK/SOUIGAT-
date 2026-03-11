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
        <div className="bg-surface-800 border border-surface-600/50 rounded-lg overflow-hidden flex flex-col">
            <div className="overflow-x-auto">
                <table className="w-full text-sm text-left">
                    <thead className="bg-surface-800/80 border-b border-surface-600/30">
                        {table.getHeaderGroups().map((headerGroup) => (
                            <tr key={headerGroup.id}>
                                {headerGroup.headers.map((header) => (
                                    <th
                                        key={header.id}
                                        className="px-5 py-3.5 text-[11px] font-semibold text-text-muted uppercase tracking-wider whitespace-nowrap"
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
                    <tbody className="divide-y divide-surface-600/20">
                        {isLoading ? (
                            // Loading skeletons
                            Array.from({ length: 5 }).map((_, i) => (
                                <tr key={i} className="animate-pulse">
                                    {columns.map((_, colIdx) => (
                                        <td key={colIdx} className="px-5 py-4">
                                            <div className="h-4 bg-surface-600/30 rounded w-3/4"></div>
                                        </td>
                                    ))}
                                </tr>
                            ))
                        ) : table.getRowModel().rows?.length ? (
                            table.getRowModel().rows.map((row) => (
                                <tr
                                    key={row.id}
                                    onClick={() => onRowClick && onRowClick(row.original)}
                                    className={`group transition-colors ${onRowClick ? 'cursor-pointer hover:bg-surface-700/40' : ''
                                        }`}
                                >
                                    {row.getVisibleCells().map((cell) => (
                                        <td key={cell.id} className="px-5 py-3 text-text-primary whitespace-nowrap">
                                            {flexRender(cell.column.columnDef.cell, cell.getContext())}
                                        </td>
                                    ))}
                                </tr>
                            ))
                        ) : (
                            <tr>
                                <td
                                    colSpan={columns.length}
                                    className="px-5 py-12 text-center text-text-muted"
                                >
                                    <PackageOpen className="w-10 h-10 mx-auto mb-3 opacity-20" />
                                    <p className="text-[13px]">{emptyMessage}</p>
                                </td>
                            </tr>
                        )}
                    </tbody>
                </table>
            </div>

            {/* Pagination control */}
            {pageCount > 1 && (
                <div className="px-5 py-3 border-t border-surface-600/50 flex items-center justify-between bg-surface-800/50">
                    <p className="text-[12px] text-text-muted">
                        Page <span className="font-medium text-text-primary">{pageIndex + 1}</span> sur{' '}
                        <span className="font-medium text-text-primary">{pageCount}</span>
                    </p>
                    <div className="flex items-center gap-2">
                        <button
                            onClick={() => onPageChange(pageIndex - 1)}
                            disabled={pageIndex <= 0 || isLoading}
                            className="p-1.5 rounded-md text-text-secondary hover:text-text-primary hover:bg-surface-600/50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                        >
                            <ChevronLeft className="w-4 h-4" />
                        </button>
                        <button
                            onClick={() => onPageChange(pageIndex + 1)}
                            disabled={pageIndex >= pageCount || isLoading}
                            className="p-1.5 rounded-md text-text-secondary hover:text-text-primary hover:bg-surface-600/50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                        >
                            <ChevronRight className="w-4 h-4" />
                        </button>
                    </div>
                </div>
            )}
        </div>
    )
}
