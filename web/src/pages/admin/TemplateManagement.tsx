import { useEffect, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { createColumnHelper } from '@tanstack/react-table'
import {
    createReverseRouteTemplate,
    createRouteTemplate,
    createRouteTemplateSegmentTariff,
    createRouteTemplateStop,
    deleteRouteTemplate,
    deleteRouteTemplateSegmentTariff,
    deleteRouteTemplateStop,
    getOffices,
    getRouteTemplates,
    updateRouteTemplate,
    updateRouteTemplateSegmentTariff,
    updateRouteTemplateStop,
} from '../../api/admin'
import { Button } from '../../components/ui/Button'
import { DataTable } from '../../components/ui/DataTable'
import { Modal } from '../../components/ui/Modal'
import {
    ArrowLeftRight,
    Edit,
    GitBranch,
    GripVertical,
    Plus,
    Route,
    ShieldAlert,
    Trash2,
} from 'lucide-react'
import type {
    Office,
    RouteTemplate as RouteTemplateType,
    RouteTemplateSegmentTariff,
    RouteTemplateStop,
} from '../../types/admin'

const templateColumnHelper = createColumnHelper<any>()

type TemplateFormState = {
    name: string
    code: string
    direction: 'forward' | 'reverse'
    start_office: number | ''
    end_office: number | ''
    is_active: boolean
}

const TEMPLATE_EMPTY: TemplateFormState = {
    name: '',
    code: '',
    direction: 'forward',
    start_office: '',
    end_office: '',
    is_active: true,
}

export function TemplateManagement() {
    const queryClient = useQueryClient()
    const [isTemplateModalOpen, setIsTemplateModalOpen] = useState(false)
    const [editingTemplate, setEditingTemplate] = useState<RouteTemplateType | null>(null)
    const [templateForm, setTemplateForm] = useState<TemplateFormState>(TEMPLATE_EMPTY)
    const [selectedTemplateId, setSelectedTemplateId] = useState<number | null>(null)
    const [draggedStopId, setDraggedStopId] = useState<number | null>(null)

    const [newStopOfficeId, setNewStopOfficeId] = useState<number | ''>('')
    const [newStopOrder, setNewStopOrder] = useState<number>(1)

    const [newSegmentFromStop, setNewSegmentFromStop] = useState<number | ''>('')
    const [newSegmentToStop, setNewSegmentToStop] = useState<number | ''>('')
    const [newSegmentPrice, setNewSegmentPrice] = useState<number>(0)
    const [newSegmentCurrency, setNewSegmentCurrency] = useState<string>('DZD')
    const [newSegmentActive, setNewSegmentActive] = useState<boolean>(true)

    const { data: templatesData, isLoading: isTemplatesLoading } = useQuery({
        queryKey: ['admin_route_templates'],
        queryFn: () => getRouteTemplates({ page_size: 200 }),
    })

    const { data: officesData } = useQuery({
        queryKey: ['offices_list'],
        queryFn: () => getOffices({ limit: 200 }),
    })

    const offices = officesData?.results || []
    const templates = templatesData?.results || []

    useEffect(() => {
        if (templates.length === 0) {
            setSelectedTemplateId(null)
            return
        }
        if (selectedTemplateId == null || !templates.some((template) => template.id === selectedTemplateId)) {
            setSelectedTemplateId(templates[0].id)
        }
    }, [templates, selectedTemplateId])

    const selectedTemplate = useMemo(
        () => templates.find((template) => template.id === selectedTemplateId) || null,
        [templates, selectedTemplateId]
    )

    const sortedStops = useMemo(
        () => [...(selectedTemplate?.stops || [])].sort((a, b) => a.stop_order - b.stop_order),
        [selectedTemplate]
    )

    const sortedSegments = useMemo(
        () => [...(selectedTemplate?.segment_tariffs || [])].sort((a, b) => a.from_stop_order - b.from_stop_order),
        [selectedTemplate]
    )

    const invalidateTemplates = () => {
        queryClient.invalidateQueries({ queryKey: ['admin_route_templates'] })
    }

    const templateMutation = useMutation({
        mutationFn: (payload: Partial<RouteTemplateType>) => {
            if (editingTemplate) {
                return updateRouteTemplate(editingTemplate.id, payload)
            }
            return createRouteTemplate(payload)
        },
        onSuccess: (template) => {
            setIsTemplateModalOpen(false)
            setEditingTemplate(null)
            setTemplateForm(TEMPLATE_EMPTY)
            setSelectedTemplateId(template.id)
            invalidateTemplates()
        },
    })

    const templateDeleteMutation = useMutation({
        mutationFn: (id: number) => deleteRouteTemplate(id),
        onSuccess: () => invalidateTemplates(),
    })

    const reverseMutation = useMutation({
        mutationFn: (id: number) => createReverseRouteTemplate(id),
        onSuccess: (created) => {
            setSelectedTemplateId(created.id)
            invalidateTemplates()
        },
    })

    const stopMutation = useMutation({
        mutationFn: (payload: Partial<RouteTemplateStop> & { id?: number }) => {
            if (payload.id) {
                const { id, ...rest } = payload
                return updateRouteTemplateStop(id, rest)
            }
            return createRouteTemplateStop(payload)
        },
        onSuccess: () => {
            invalidateTemplates()
            setNewStopOfficeId('')
            setNewStopOrder((sortedStops[sortedStops.length - 1]?.stop_order || 0) + 1)
        },
    })

    const stopDeleteMutation = useMutation({
        mutationFn: (id: number) => deleteRouteTemplateStop(id),
        onSuccess: () => invalidateTemplates(),
    })

    const reorderStopsMutation = useMutation({
        mutationFn: async (orderedStops: RouteTemplateStop[]) => {
            const updates = orderedStops
                .map((stop, index) => ({
                    id: stop.id,
                    nextOrder: index + 1,
                    currentOrder: stop.stop_order,
                }))
                .filter((entry) => entry.nextOrder !== entry.currentOrder)

            for (const update of updates) {
                await updateRouteTemplateStop(update.id, { stop_order: update.nextOrder })
            }
        },
        onSuccess: () => invalidateTemplates(),
    })

    const segmentMutation = useMutation({
        mutationFn: (payload: Partial<RouteTemplateSegmentTariff> & { id?: number }) => {
            if (payload.id) {
                const { id, ...rest } = payload
                return updateRouteTemplateSegmentTariff(id, rest)
            }
            return createRouteTemplateSegmentTariff(payload)
        },
        onSuccess: () => {
            invalidateTemplates()
            setNewSegmentFromStop('')
            setNewSegmentToStop('')
            setNewSegmentPrice(0)
            setNewSegmentCurrency('DZD')
            setNewSegmentActive(true)
        },
    })

    const segmentDeleteMutation = useMutation({
        mutationFn: (id: number) => deleteRouteTemplateSegmentTariff(id),
        onSuccess: () => invalidateTemplates(),
    })

    const autoFillSegmentsMutation = useMutation({
        mutationFn: async (template: RouteTemplateType) => {
            const stops = [...template.stops].sort((a, b) => a.stop_order - b.stop_order)
            if (stops.length < 2) {
                return
            }

            const existingPairs = new Set(
                template.segment_tariffs.map((segment) => `${segment.from_stop}:${segment.to_stop}`)
            )
            const defaultPrice = template.segment_tariffs[0]?.passenger_price || 100
            const defaultCurrency = template.segment_tariffs[0]?.currency || 'DZD'

            for (let index = 0; index < stops.length - 1; index += 1) {
                const fromStop = stops[index]
                const toStop = stops[index + 1]
                const key = `${fromStop.id}:${toStop.id}`
                if (existingPairs.has(key)) {
                    continue
                }
                await createRouteTemplateSegmentTariff({
                    route_template: template.id,
                    from_stop: fromStop.id,
                    to_stop: toStop.id,
                    passenger_price: defaultPrice,
                    currency: defaultCurrency,
                    is_active: true,
                })
            }
        },
        onSuccess: () => invalidateTemplates(),
    })

    const handleDropStop = (targetStopId: number) => {
        if (!selectedTemplate || draggedStopId == null || draggedStopId === targetStopId) {
            setDraggedStopId(null)
            return
        }

        const current = [...sortedStops]
        const fromIndex = current.findIndex((stop) => stop.id === draggedStopId)
        const toIndex = current.findIndex((stop) => stop.id === targetStopId)
        if (fromIndex === -1 || toIndex === -1) {
            setDraggedStopId(null)
            return
        }

        const [moved] = current.splice(fromIndex, 1)
        current.splice(toIndex, 0, moved)
        setDraggedStopId(null)
        reorderStopsMutation.mutate(current)
    }

    const openCreateTemplateModal = () => {
        setEditingTemplate(null)
        setTemplateForm(TEMPLATE_EMPTY)
        setIsTemplateModalOpen(true)
    }

    const openEditTemplateModal = (template: RouteTemplateType) => {
        setEditingTemplate(template)
        setTemplateForm({
            name: template.name,
            code: template.code,
            direction: template.direction,
            start_office: template.start_office,
            end_office: template.end_office,
            is_active: template.is_active,
        })
        setIsTemplateModalOpen(true)
    }

    const templateColumns = [
        templateColumnHelper.accessor('name', {
            header: 'Template',
            cell: (info) => <span className="font-semibold text-text-primary">{info.getValue()}</span>,
        }),
        templateColumnHelper.accessor('code', {
            header: 'Code',
            cell: (info) => <span className="font-mono text-xs">{info.getValue()}</span>,
        }),
        templateColumnHelper.accessor('direction', {
            header: 'Direction',
            cell: (info) => (
                <span className="px-2 py-0.5 rounded text-xs font-medium border bg-surface-700/40 text-brand-400 border-brand-500/20">
                    {info.getValue() === 'reverse' ? 'Reverse' : 'Forward'}
                </span>
            ),
        }),
        templateColumnHelper.accessor((row) => `${row.start_office_name} -> ${row.end_office_name}`, {
            id: 'endpoints',
            header: 'Endpoints',
            cell: (info) => <span className="text-sm text-text-secondary">{info.getValue()}</span>,
        }),
        templateColumnHelper.accessor((row) => row.stops.length, {
            id: 'stop_count',
            header: 'Stops',
            cell: (info) => <span>{info.getValue()}</span>,
        }),
        templateColumnHelper.display({
            id: 'actions',
            header: 'Actions',
            cell: (info) => {
                const template = info.row.original
                return (
                    <div className="flex items-center gap-2">
                        <Button
                            variant="ghost"
                            className="p-1.5 h-auto text-brand-400 hover:bg-brand-500/10"
                            onClick={(e) => {
                                e.stopPropagation()
                                openEditTemplateModal(template)
                            }}
                            title="Modifier"
                        >
                            <Edit className="w-4 h-4" />
                        </Button>
                        <Button
                            variant="ghost"
                            className="p-1.5 h-auto text-accent-400 hover:bg-accent-500/10"
                            onClick={(e) => {
                                e.stopPropagation()
                                reverseMutation.mutate(template.id)
                            }}
                            isLoading={reverseMutation.isPending && reverseMutation.variables === template.id}
                            title="Creer reverse"
                        >
                            <ArrowLeftRight className="w-4 h-4" />
                        </Button>
                        <Button
                            variant="danger"
                            className="p-1.5 h-auto"
                            onClick={(e) => {
                                e.stopPropagation()
                                if (confirm('Supprimer ce template ?')) {
                                    templateDeleteMutation.mutate(template.id)
                                }
                            }}
                            isLoading={templateDeleteMutation.isPending && templateDeleteMutation.variables === template.id}
                            title="Supprimer"
                        >
                            <Trash2 className="w-4 h-4" />
                        </Button>
                    </div>
                )
            },
        }),
    ]

    const currentTemplateError = (templateMutation.error as any)?.response?.data
    const sectionError =
        (stopMutation.error as any)?.response?.data ||
        (reorderStopsMutation.error as any)?.response?.data ||
        (segmentMutation.error as any)?.response?.data ||
        (autoFillSegmentsMutation.error as any)?.response?.data ||
        (reverseMutation.error as any)?.response?.data

    return (
        <div className="space-y-6 animate-fade-in">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <div>
                    <h1 className="text-2xl font-bold text-text-primary flex items-center gap-2">
                        <Route className="w-6 h-6 text-brand-400" />
                        Template Management
                    </h1>
                    <p className="text-sm text-text-muted mt-1">
                        Gere les templates de route, l'ordre des arrets, les tarifs segmentaires et la creation reverse.
                    </p>
                </div>
                <Button onClick={openCreateTemplateModal} className="shrink-0 flex items-center gap-2">
                    <Plus className="w-4 h-4" />
                    Nouveau template
                </Button>
            </div>

            <div className="bg-surface-800/80 backdrop-blur-md border border-surface-700 rounded-xl overflow-hidden min-h-[320px]">
                <DataTable
                    data={templates}
                    columns={templateColumns}
                    isLoading={isTemplatesLoading}
                    pageCount={1}
                    pageIndex={0}
                    onPageChange={() => {}}
                    onRowClick={(row) => setSelectedTemplateId(row.id)}
                    emptyMessage="Aucun template trouve."
                />
            </div>

            {selectedTemplate && (
                <div className="grid grid-cols-1 xl:grid-cols-2 gap-6">
                    <div className="bg-surface-800/80 backdrop-blur-md border border-surface-700 rounded-xl p-5 space-y-4">
                        <div className="flex items-center justify-between">
                            <h2 className="text-lg font-semibold text-text-primary flex items-center gap-2">
                                <GitBranch className="w-5 h-5 text-brand-400" />
                                Stops - {selectedTemplate.name}
                            </h2>
                            <span className="text-xs text-text-muted">Drag and drop pour reordonner</span>
                        </div>

                        <div className="space-y-2">
                            {sortedStops.map((stop) => (
                                <div
                                    key={stop.id}
                                    draggable
                                    onDragStart={() => setDraggedStopId(stop.id)}
                                    onDragOver={(event) => event.preventDefault()}
                                    onDrop={() => handleDropStop(stop.id)}
                                    className={`flex items-center gap-3 bg-surface-900 border rounded-lg px-3 py-2 transition-colors ${
                                        draggedStopId === stop.id
                                            ? 'border-brand-500/60 bg-brand-500/10'
                                            : 'border-surface-700'
                                    }`}
                                >
                                    <GripVertical className="w-4 h-4 text-text-muted cursor-grab" />
                                    <span className="w-8 text-center text-xs font-bold text-brand-300">#{stop.stop_order}</span>
                                    <span className="flex-1 text-sm text-text-primary">{stop.office_name}</span>
                                    <Button
                                        variant="danger"
                                        className="p-1.5 h-auto"
                                        onClick={() => {
                                            if (confirm('Supprimer cet arret ?')) {
                                                stopDeleteMutation.mutate(stop.id)
                                            }
                                        }}
                                        isLoading={stopDeleteMutation.isPending && stopDeleteMutation.variables === stop.id}
                                    >
                                        <Trash2 className="w-4 h-4" />
                                    </Button>
                                </div>
                            ))}
                            {sortedStops.length === 0 && (
                                <p className="text-sm text-text-muted">Aucun arret configure.</p>
                            )}
                        </div>

                        <div className="pt-3 border-t border-surface-700 grid grid-cols-1 md:grid-cols-3 gap-3">
                            <select
                                className="bg-surface-900 border border-surface-700 rounded-lg px-3 py-2 text-sm text-text-primary"
                                value={newStopOfficeId}
                                onChange={(e) => setNewStopOfficeId(e.target.value ? Number(e.target.value) : '')}
                            >
                                <option value="">Agence</option>
                                {offices.map((office: Office) => (
                                    <option key={office.id} value={office.id}>
                                        {office.name}
                                    </option>
                                ))}
                            </select>
                            <input
                                type="number"
                                min={1}
                                className="bg-surface-900 border border-surface-700 rounded-lg px-3 py-2 text-sm text-text-primary"
                                value={newStopOrder}
                                onChange={(e) => setNewStopOrder(Number(e.target.value) || 1)}
                            />
                            <Button
                                onClick={() => {
                                    if (!newStopOfficeId) return
                                    stopMutation.mutate({
                                        route_template: selectedTemplate.id,
                                        office: newStopOfficeId,
                                        stop_order: newStopOrder,
                                    })
                                }}
                                isLoading={stopMutation.isPending}
                            >
                                Ajouter stop
                            </Button>
                        </div>
                    </div>

                    <div className="bg-surface-800/80 backdrop-blur-md border border-surface-700 rounded-xl p-5 space-y-4">
                        <div className="flex items-center justify-between">
                            <h2 className="text-lg font-semibold text-text-primary">Segment Tariffs</h2>
                            <Button
                                variant="secondary"
                                onClick={() => autoFillSegmentsMutation.mutate(selectedTemplate)}
                                isLoading={autoFillSegmentsMutation.isPending}
                                className="text-xs"
                            >
                                Auto-fill adjacent
                            </Button>
                        </div>

                        <div className="space-y-2">
                            {sortedSegments.map((segment) => (
                                <div key={segment.id} className="flex items-center gap-2 bg-surface-900 border border-surface-700 rounded-lg px-3 py-2">
                                    <span className="text-xs text-text-muted">{segment.from_stop_order} {'->'} {segment.to_stop_order}</span>
                                    <span className="font-semibold text-sm text-text-primary ml-1">{segment.passenger_price} {segment.currency}</span>
                                    <span className={`ml-auto px-2 py-0.5 rounded text-[10px] border ${segment.is_active ? 'bg-status-success/10 text-emerald-400 border-status-success/20' : 'bg-red-500/10 text-red-400 border-status-error/20'}`}>
                                        {segment.is_active ? 'Actif' : 'Inactif'}
                                    </span>
                                    <Button
                                        variant="ghost"
                                        className="p-1.5 h-auto text-brand-400"
                                        onClick={() => {
                                            segmentMutation.mutate({ id: segment.id, is_active: !segment.is_active })
                                        }}
                                        isLoading={segmentMutation.isPending}
                                    >
                                        <Edit className="w-4 h-4" />
                                    </Button>
                                    <Button
                                        variant="danger"
                                        className="p-1.5 h-auto"
                                        onClick={() => {
                                            if (confirm('Supprimer ce segment ?')) {
                                                segmentDeleteMutation.mutate(segment.id)
                                            }
                                        }}
                                        isLoading={segmentDeleteMutation.isPending && segmentDeleteMutation.variables === segment.id}
                                    >
                                        <Trash2 className="w-4 h-4" />
                                    </Button>
                                </div>
                            ))}
                            {sortedSegments.length === 0 && (
                                <p className="text-sm text-text-muted">Aucun segment configure.</p>
                            )}
                        </div>

                        <div className="pt-3 border-t border-surface-700 grid grid-cols-1 md:grid-cols-5 gap-3">
                            <select
                                className="bg-surface-900 border border-surface-700 rounded-lg px-3 py-2 text-sm text-text-primary"
                                value={newSegmentFromStop}
                                onChange={(e) => setNewSegmentFromStop(e.target.value ? Number(e.target.value) : '')}
                            >
                                <option value="">From stop</option>
                                {sortedStops.map((stop) => (
                                    <option key={stop.id} value={stop.id}>
                                        #{stop.stop_order} {stop.office_name}
                                    </option>
                                ))}
                            </select>
                            <select
                                className="bg-surface-900 border border-surface-700 rounded-lg px-3 py-2 text-sm text-text-primary"
                                value={newSegmentToStop}
                                onChange={(e) => setNewSegmentToStop(e.target.value ? Number(e.target.value) : '')}
                            >
                                <option value="">To stop</option>
                                {sortedStops.map((stop) => (
                                    <option key={stop.id} value={stop.id}>
                                        #{stop.stop_order} {stop.office_name}
                                    </option>
                                ))}
                            </select>
                            <input
                                type="number"
                                min={0}
                                className="bg-surface-900 border border-surface-700 rounded-lg px-3 py-2 text-sm text-text-primary"
                                value={newSegmentPrice}
                                onChange={(e) => setNewSegmentPrice(Number(e.target.value) || 0)}
                                placeholder="Prix"
                            />
                            <input
                                type="text"
                                className="bg-surface-900 border border-surface-700 rounded-lg px-3 py-2 text-sm text-text-primary"
                                value={newSegmentCurrency}
                                onChange={(e) => setNewSegmentCurrency(e.target.value.toUpperCase().slice(0, 3))}
                                placeholder="DZD"
                            />
                            <Button
                                onClick={() => {
                                    if (!newSegmentFromStop || !newSegmentToStop) return
                                    segmentMutation.mutate({
                                        route_template: selectedTemplate.id,
                                        from_stop: newSegmentFromStop,
                                        to_stop: newSegmentToStop,
                                        passenger_price: newSegmentPrice,
                                        currency: newSegmentCurrency || 'DZD',
                                        is_active: newSegmentActive,
                                    })
                                }}
                                isLoading={segmentMutation.isPending}
                            >
                                Ajouter segment
                            </Button>
                        </div>
                    </div>
                </div>
            )}

            {(currentTemplateError || sectionError) && (
                <div className="bg-red-500/10 border border-status-error/30 text-red-400 p-3 rounded-lg text-sm flex items-start gap-2">
                    <ShieldAlert className="w-5 h-5 shrink-0" />
                    <p>{JSON.stringify(currentTemplateError || sectionError)}</p>
                </div>
            )}

            <Modal
                isOpen={isTemplateModalOpen}
                onClose={() => !templateMutation.isPending && setIsTemplateModalOpen(false)}
                title={editingTemplate ? 'Modifier template' : 'Nouveau template'}
            >
                <form
                    onSubmit={(e) => {
                        e.preventDefault()
                        if (!templateForm.start_office || !templateForm.end_office) return
                        templateMutation.mutate({
                            name: templateForm.name,
                            code: templateForm.code,
                            direction: templateForm.direction,
                            start_office: Number(templateForm.start_office),
                            end_office: Number(templateForm.end_office),
                            is_active: templateForm.is_active,
                        })
                    }}
                    className="space-y-4"
                >
                    <div className="grid grid-cols-2 gap-4">
                        <div>
                            <label className="block text-sm font-medium text-text-primary mb-1">Nom *</label>
                            <input
                                required
                                type="text"
                                className="w-full bg-surface-900 border border-surface-700 rounded-lg px-3 py-2 text-text-primary"
                                value={templateForm.name}
                                onChange={(e) => setTemplateForm((prev) => ({ ...prev, name: e.target.value }))}
                            />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-text-primary mb-1">Code *</label>
                            <input
                                required
                                type="text"
                                className="w-full bg-surface-900 border border-surface-700 rounded-lg px-3 py-2 text-text-primary"
                                value={templateForm.code}
                                onChange={(e) => setTemplateForm((prev) => ({ ...prev, code: e.target.value.toUpperCase() }))}
                            />
                        </div>
                    </div>

                    <div className="grid grid-cols-3 gap-4">
                        <div>
                            <label className="block text-sm font-medium text-text-primary mb-1">Direction *</label>
                            <select
                                className="w-full bg-surface-900 border border-surface-700 rounded-lg px-3 py-2 text-text-primary"
                                value={templateForm.direction}
                                onChange={(e) => setTemplateForm((prev) => ({ ...prev, direction: e.target.value as 'forward' | 'reverse' }))}
                            >
                                <option value="forward">Forward</option>
                                <option value="reverse">Reverse</option>
                            </select>
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-text-primary mb-1">Start office *</label>
                            <select
                                required
                                className="w-full bg-surface-900 border border-surface-700 rounded-lg px-3 py-2 text-text-primary"
                                value={templateForm.start_office}
                                onChange={(e) => setTemplateForm((prev) => ({ ...prev, start_office: e.target.value ? Number(e.target.value) : '' }))}
                            >
                                <option value="">Selectionner</option>
                                {offices.map((office: Office) => (
                                    <option key={office.id} value={office.id}>
                                        {office.name}
                                    </option>
                                ))}
                            </select>
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-text-primary mb-1">End office *</label>
                            <select
                                required
                                className="w-full bg-surface-900 border border-surface-700 rounded-lg px-3 py-2 text-text-primary"
                                value={templateForm.end_office}
                                onChange={(e) => setTemplateForm((prev) => ({ ...prev, end_office: e.target.value ? Number(e.target.value) : '' }))}
                            >
                                <option value="">Selectionner</option>
                                {offices.map((office: Office) => (
                                    <option key={office.id} value={office.id}>
                                        {office.name}
                                    </option>
                                ))}
                            </select>
                        </div>
                    </div>

                    <label className="flex items-center gap-2 text-sm text-text-secondary">
                        <input
                            type="checkbox"
                            checked={templateForm.is_active}
                            onChange={(e) => setTemplateForm((prev) => ({ ...prev, is_active: e.target.checked }))}
                        />
                        Template actif
                    </label>

                    <div className="flex justify-end gap-3 mt-6">
                        <Button type="button" variant="secondary" onClick={() => setIsTemplateModalOpen(false)}>
                            Annuler
                        </Button>
                        <Button type="submit" isLoading={templateMutation.isPending}>
                            {editingTemplate ? 'Enregistrer' : 'Creer'}
                        </Button>
                    </div>
                </form>
            </Modal>
        </div>
    )
}
