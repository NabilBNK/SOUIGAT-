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
        <div className="bg-white dark:bg-[#1a2634] border border-slate-200 dark:border-slate-800 rounded-lg overflow-hidden flex flex-col">
            <div className="overflow-x-auto">
                <table className="w-full text-sm text-left">
                    <thead className="bg-white dark:bg-[#1a2634]/80 border-b border-slate-200 dark:border-slate-700/30">
                        {table.getHeaderGroups().map((headerGroup) => (
                            <tr key={headerGroup.id}>
                                {headerGroup.headers.map((header) => (
                                    <th
                                        key={header.id}
                                        className="px-5 py-3.5 text-[11px] font-semibold text-slate-400 dark:text-slate-500 uppercase tracking-wider whitespace-nowrap"
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
                                            <div className="h-4 bg-slate-200 dark:bg-slate-700/30 rounded w-3/4"></div>
                                        </td>
                                    ))}
                                </tr>
                            ))
                        ) : table.getRowModel().rows?.length ? (
                            table.getRowModel().rows.map((row) => (
                                <tr
                                    key={row.id}
                                    onClick={() => onRowClick && onRowClick(row.original)}
                                    className={`group transition-colors ${onRowClick ? 'cursor-pointer hover:bg-slate-100 dark:bg-[#1e293b]/40' : ''
                                        }`}
                                >
                                    {row.getVisibleCells().map((cell) => (
                                        <td key={cell.id} className="px-5 py-3 text-slate-900 dark:text-slate-100 whitespace-nowrap">
                                            {flexRender(cell.column.columnDef.cell, cell.getContext())}
                                        </td>
                                    ))}
                                </tr>
                            ))
                        ) : (
                            <tr>
                                <td
                                    colSpan={columns.length}
                                    className="px-5 py-12 text-center text-slate-400 dark:text-slate-500"
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
                <div className="px-5 py-3 border-t border-slate-200 dark:border-slate-800 flex items-center justify-between bg-white dark:bg-[#1a2634]/50">
                    <p className="text-[12px] text-slate-400 dark:text-slate-500">
                        Page <span className="font-medium text-slate-900 dark:text-slate-100">{pageIndex + 1}</span> sur{' '}
                        <span className="font-medium text-slate-900 dark:text-slate-100">{pageCount}</span>
                    </p>
                    <div className="flex items-center gap-2">
                        <button
                            onClick={() => onPageChange(pageIndex - 1)}
                            disabled={pageIndex <= 0 || isLoading}
                            className="p-1.5 rounded-md text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:text-slate-100 hover:bg-slate-200 dark:bg-slate-700/50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                        >
                            <ChevronLeft className="w-4 h-4" />
                        </button>
                        <button
                            onClick={() => onPageChange(pageIndex + 1)}
                            disabled={pageIndex >= pageCount || isLoading}
                            className="p-1.5 rounded-md text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:text-slate-100 hover:bg-slate-200 dark:bg-slate-700/50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                        >
                            <ChevronRight className="w-4 h-4" />
                        </button>
                    </div>
                </div>
            )}
        </div>
    )
}
