import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { createColumnHelper } from '@tanstack/react-table'
import {
    createReverseRouteTemplate,
    createRouteTemplate,
    deleteRouteTemplate,
    getOffices,
    getRouteTemplates,
    updateRouteTemplate,
} from '../../api/admin'
import { Button } from '../../components/ui/Button'
import { DataTable } from '../../components/ui/DataTable'
import { Modal } from '../../components/ui/Modal'
import {
    ArrowLeftRight,
    Edit,
    Plus,
    Route,
    ShieldAlert,
    Trash2,
} from 'lucide-react'
import type {
    Office,
    RouteTemplate as RouteTemplateType,
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
    const navigate = useNavigate()
    const queryClient = useQueryClient()
    const [isTemplateModalOpen, setIsTemplateModalOpen] = useState(false)
    const [editingTemplate, setEditingTemplate] = useState<RouteTemplateType | null>(null)
    const [templateForm, setTemplateForm] = useState<TemplateFormState>(TEMPLATE_EMPTY)

    const { data: templatesData, isLoading: isTemplatesLoading } = useQuery({
        queryKey: ['admin_route_templates'],
        queryFn: () => getRouteTemplates({ page_size: 200 }),
    })

    const { data: officesData } = useQuery({
        queryKey: ['offices_list'],
        queryFn: () => getOffices({ limit: 200 }),
    })

    const offices = officesData?.results || []
    const activeOffices = useMemo(
        () => offices.filter((office: Office) => office.is_active),
        [offices],
    )
    const templates = templatesData?.results || []

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
            invalidateTemplates()
            navigate(`/admin/templates/${template.id}/configure`)
        },
    })

    const templateDeleteMutation = useMutation({
        mutationFn: (id: number) => deleteRouteTemplate(id),
        onSuccess: () => invalidateTemplates(),
    })

    const reverseMutation = useMutation({
        mutationFn: (id: number) => createReverseRouteTemplate(id),
        onSuccess: (created) => {
            invalidateTemplates()
            navigate(`/admin/templates/${created.id}/configure`)
        },
    })

    const formatApiError = (value: unknown): string => {
        if (!value) return 'Une erreur est survenue.'
        if (typeof value === 'string') return value
        if (typeof value === 'object') {
            const detail = (value as { detail?: unknown }).detail
            if (typeof detail === 'string') return detail
            return JSON.stringify(value)
        }
        return String(value)
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
            cell: (info) => {
                const row = info.row.original as RouteTemplateType
                return (
                    <button
                        type="button"
                        onClick={(event) => {
                            event.stopPropagation()
                            navigate(`/admin/templates/${row.id}/configure`)
                        }}
                        className="font-semibold text-text-primary hover:text-brand-300"
                        title="Configurer ce template"
                    >
                        {info.getValue()}
                    </button>
                )
            },
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
                            variant="secondary"
                            className="p-1.5 h-auto"
                            onClick={(e) => {
                                e.stopPropagation()
                                navigate(`/admin/templates/${template.id}/configure`)
                            }}
                            title="Configurer"
                        >
                            <Route className="w-4 h-4" />
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
                    onRowClick={(row) => navigate(`/admin/templates/${row.id}/configure`)}
                    emptyMessage="Aucun template trouve."
                />
            </div>

            {(currentTemplateError || sectionError) && (
                <div className="bg-red-500/10 border border-status-error/30 text-red-400 p-3 rounded-lg text-sm flex items-start gap-2">
                    <ShieldAlert className="w-5 h-5 shrink-0" />
                    <p>{formatApiError(currentTemplateError || sectionError)}</p>
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
                                {activeOffices.map((office: Office) => (
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
                                {activeOffices.map((office: Office) => (
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
