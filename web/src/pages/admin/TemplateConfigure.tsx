import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createRouteTemplateSegmentTariff,
  createRouteTemplateStop,
  deleteRouteTemplateSegmentTariff,
  deleteRouteTemplateStop,
  getOffices,
  getRouteTemplate,
  syncRouteTemplateToFirebase,
  updateRouteTemplate,
  updateRouteTemplateSegmentTariff,
  updateRouteTemplateStop,
} from '../../api/admin'
import { Button } from '../../components/ui/Button'
import { ArrowLeft, GripVertical, RefreshCw, ShieldAlert, Trash2 } from 'lucide-react'
import type { Office, RouteTemplate, RouteTemplateSegmentTariff, RouteTemplateStop } from '../../types/admin'

export function TemplateConfigure() {
  const { id } = useParams<{ id: string }>()
  const templateId = Number(id)
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const [draggedStopId, setDraggedStopId] = useState<number | null>(null)
  const [newStopInput, setNewStopInput] = useState('')
  const [newStopOrder, setNewStopOrder] = useState(1)
  const [newSegmentFromStop, setNewSegmentFromStop] = useState<number | ''>('')
  const [newSegmentToStop, setNewSegmentToStop] = useState<number | ''>('')
  const [newSegmentPrice, setNewSegmentPrice] = useState(0)
  const [newSegmentCurrency, setNewSegmentCurrency] = useState('DZD')
  const [newSegmentActive] = useState(true)
  const [cargoSmallPrice, setCargoSmallPrice] = useState(0)
  const [cargoMediumPrice, setCargoMediumPrice] = useState(0)
  const [cargoLargePrice, setCargoLargePrice] = useState(0)
  const [pricingCurrency, setPricingCurrency] = useState('DZD')

  const { data: templatesData, isLoading } = useQuery({
    queryKey: ['admin_route_template', templateId],
    queryFn: () => getRouteTemplate(templateId),
    enabled: Number.isFinite(templateId),
  })
  const { data: officesData } = useQuery({
    queryKey: ['offices_list'],
    queryFn: () => getOffices({ limit: 200 }),
  })

  const template: RouteTemplate | null = useMemo(() => {
    if (!templatesData) return null
    return templatesData
  }, [templatesData])

  const activeOffices = useMemo(() => (officesData?.results || []).filter((office: Office) => office.is_active), [officesData])
  const sortedStops = useMemo(() => [...(template?.stops || [])].sort((a, b) => a.stop_order - b.stop_order), [template])
  const sortedSegments = useMemo(() => [...(template?.segment_tariffs || [])].sort((a, b) => a.from_stop_order - b.from_stop_order), [template])

  const computedPassengerPrice = useMemo(() => {
    if (!template) return 0
    const stopCount = template.stops.length
    if (stopCount < 2) return 0

    const activeSegments = (template.segment_tariffs || []).filter((segment) => segment.is_active && segment.passenger_price > 0)
    const directFull = activeSegments.find(
      (segment) => segment.from_stop_order === 1 && segment.to_stop_order === stopCount,
    )
    if (directFull) {
      return directFull.passenger_price
    }

    const adjacentMap = new Map<number, number>()
    for (const segment of activeSegments) {
      if (segment.to_stop_order === segment.from_stop_order + 1) {
        adjacentMap.set(segment.from_stop_order, segment.passenger_price)
      }
    }

    let total = 0
    for (let order = 1; order < stopCount; order += 1) {
      const price = adjacentMap.get(order)
      if (!price || price <= 0) {
        return 0
      }
      total += price
    }
    return total
  }, [template])

  const invalidateTemplates = () => {
    queryClient.invalidateQueries({ queryKey: ['admin_route_templates'] })
    queryClient.invalidateQueries({ queryKey: ['admin_route_template', templateId] })
  }

  useEffect(() => {
    if (!template) {
      return
    }
    setCargoSmallPrice(template.cargo_small_price || 0)
    setCargoMediumPrice(template.cargo_medium_price || 0)
    setCargoLargePrice(template.cargo_large_price || 0)
    setPricingCurrency((template.currency || 'DZD').toUpperCase())
  }, [template])

  const stopMutation = useMutation({
    mutationFn: (payload: Partial<RouteTemplateStop> & { id?: number }) => {
      if (payload.id) {
        const { id: stopId, ...rest } = payload
        return updateRouteTemplateStop(stopId, rest)
      }
      return createRouteTemplateStop(payload)
    },
    onSuccess: () => {
      invalidateTemplates()
      setNewStopInput('')
      setNewStopOrder((sortedStops[sortedStops.length - 1]?.stop_order || 0) + 1)
    },
  })

  const stopDeleteMutation = useMutation({
    mutationFn: (stopId: number) => deleteRouteTemplateStop(stopId),
    onSuccess: () => invalidateTemplates(),
  })

  const reorderStopsMutation = useMutation({
    mutationFn: async (orderedStops: RouteTemplateStop[]) => {
      const updates = orderedStops
        .map((stop, index) => ({ id: stop.id, nextOrder: index + 1, currentOrder: stop.stop_order }))
        .filter((entry) => entry.nextOrder !== entry.currentOrder)

      if (updates.length === 0) {
        return
      }

      // Two-phase reorder avoids unique constraint collisions on (route_template, stop_order).
      const maxCurrentOrder = Math.max(...orderedStops.map((stop) => stop.stop_order), 0)
      const temporaryBase = maxCurrentOrder + 1000

      for (let index = 0; index < updates.length; index += 1) {
        const update = updates[index]
        await updateRouteTemplateStop(update.id, { stop_order: temporaryBase + index })
      }

      for (const update of updates) {
        await updateRouteTemplateStop(update.id, { stop_order: update.nextOrder })
      }
    },
    onSuccess: () => invalidateTemplates(),
  })

  const segmentMutation = useMutation({
    mutationFn: (payload: Partial<RouteTemplateSegmentTariff> & { id?: number }) => {
      if (payload.id) {
        const { id: segmentId, ...rest } = payload
        return updateRouteTemplateSegmentTariff(segmentId, rest)
      }
      return createRouteTemplateSegmentTariff(payload)
    },
    onSuccess: () => {
      invalidateTemplates()
      setNewSegmentFromStop('')
      setNewSegmentToStop('')
      setNewSegmentPrice(0)
      setNewSegmentCurrency('DZD')
    },
  })

  const segmentDeleteMutation = useMutation({
    mutationFn: (segmentId: number) => deleteRouteTemplateSegmentTariff(segmentId),
    onSuccess: () => invalidateTemplates(),
  })

  const autoFillSegmentsMutation = useMutation({
    mutationFn: async (targetTemplate: RouteTemplate) => {
      const stops = [...targetTemplate.stops].sort((a, b) => a.stop_order - b.stop_order)
      if (stops.length < 2) {
        return { created: 0, updated: 0 }
      }

      const stopByOrder = new Map(stops.map((stop) => [stop.stop_order, stop]))
      const existingByPair = new Map(targetTemplate.segment_tariffs.map((segment) => [`${segment.from_stop}:${segment.to_stop}`, segment]))
      const adjacentPriceByFromOrder = new Map<number, number>()
      const knownPairPrices: Array<{ from: number; to: number; price: number }> = []

      for (const segment of targetTemplate.segment_tariffs) {
        if (segment.is_active && segment.passenger_price > 0 && segment.to_stop_order > segment.from_stop_order) {
          knownPairPrices.push({
            from: segment.from_stop_order,
            to: segment.to_stop_order,
            price: segment.passenger_price,
          })
        }

        if (
          segment.is_active
          && segment.to_stop_order === segment.from_stop_order + 1
          && segment.passenger_price > 0
        ) {
          adjacentPriceByFromOrder.set(segment.from_stop_order, segment.passenger_price)
        }
      }

      // Infer missing adjacent intervals from known pair fares when possible.
      // Example: A->B=300 and A->C=1500 => infer B->C=1200.
      let inferred = true
      while (inferred) {
        inferred = false

        for (const pair of knownPairPrices) {
          const unknownOrders: number[] = []
          let knownSum = 0

          for (let order = pair.from; order < pair.to; order += 1) {
            const intervalPrice = adjacentPriceByFromOrder.get(order)
            if (intervalPrice == null) {
              unknownOrders.push(order)
            } else {
              knownSum += intervalPrice
            }
          }

          if (unknownOrders.length !== 1) {
            continue
          }

          const missingOrder = unknownOrders[0]
          const inferredPrice = pair.price - knownSum
          if (inferredPrice <= 0) {
            continue
          }

          const existing = adjacentPriceByFromOrder.get(missingOrder)
          if (existing == null) {
            adjacentPriceByFromOrder.set(missingOrder, inferredPrice)
            inferred = true
          }
        }
      }

      const computeByAdjacent = (fromOrder: number, toOrder: number): number | null => {
        let total = 0
        for (let order = fromOrder; order < toOrder; order += 1) {
          const price = adjacentPriceByFromOrder.get(order)
          if (!price || price <= 0) {
            return null
          }
          total += price
        }
        return total > 0 ? total : null
      }

      let created = 0
      let updated = 0

      for (let fromOrder = 1; fromOrder < stops.length; fromOrder += 1) {
        for (let toOrder = fromOrder + 1; toOrder <= stops.length; toOrder += 1) {
          const fromStop = stopByOrder.get(fromOrder)
          const toStop = stopByOrder.get(toOrder)
          if (!fromStop || !toStop) {
            continue
          }

          const computedPrice = computeByAdjacent(fromOrder, toOrder)
          if (computedPrice == null) {
            continue
          }

          const key = `${fromStop.id}:${toStop.id}`
          const existing = existingByPair.get(key)
          if (existing) {
            if (!existing.is_active || existing.passenger_price <= 0) {
              await updateRouteTemplateSegmentTariff(existing.id, {
                passenger_price: computedPrice,
                currency: existing.currency || 'DZD',
                is_active: true,
              })
              updated += 1
            }
            continue
          }

          await createRouteTemplateSegmentTariff({
            route_template: targetTemplate.id,
            from_stop: fromStop.id,
            to_stop: toStop.id,
            passenger_price: computedPrice,
            currency: 'DZD',
            is_active: true,
          })
          created += 1
        }
      }

      return { created, updated }
    },
    onSuccess: (result) => {
      invalidateTemplates()
      if (result.created === 0 && result.updated === 0) {
        alert('Auto-fill: aucun tarif calculable avec les intervalles actifs actuels.')
      } else {
        alert(`Auto-fill termine: ${result.created} cree(s), ${result.updated} reactive(s).`)
      }
    },
  })

  const syncTemplateMutation = useMutation({
    mutationFn: () => syncRouteTemplateToFirebase(templateId),
    onSuccess: () => {
      alert('Template synchronise vers Firebase avec succes.')
    },
  })

  const savePricingMutation = useMutation({
    mutationFn: async () => {
      if (!template) {
        throw new Error('Template introuvable pour enregistrer la tarification.')
      }

      if (computedPassengerPrice <= 0) {
        throw new Error('Les tarifs segmentaires passager doivent etre definis pour calculer le prix passager de route.')
      }

      const payload = {
        cargo_small_price: cargoSmallPrice,
        cargo_medium_price: cargoMediumPrice,
        cargo_large_price: cargoLargePrice,
        currency: (pricingCurrency || 'DZD').toUpperCase().slice(0, 3),
      }
      return updateRouteTemplate(template.id, payload)
    },
    onSuccess: () => {
      invalidateTemplates()
      alert('Tarification enregistree pour ce template.')
    },
  })

  const sectionError =
    (stopMutation.error as any)?.response?.data ||
    (reorderStopsMutation.error as any)?.response?.data ||
    (segmentMutation.error as any)?.response?.data ||
    (autoFillSegmentsMutation.error as any)?.response?.data

  const getStopLabel = (stop: RouteTemplateStop) => stop.stop_label || stop.office_name || stop.stop_name || `Stop ${stop.stop_order}`

  const handleDropStop = (targetStopId: number) => {
    if (!template || draggedStopId == null || draggedStopId === targetStopId) {
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

  if (!Number.isFinite(templateId)) {
    return <div className="text-red-400">Template invalide.</div>
  }

  if (isLoading) {
    return <div className="text-text-muted">Chargement...</div>
  }

  if (!template) {
    return (
      <div className="space-y-4">
        <p className="text-text-muted">Template introuvable.</p>
        <Button variant="secondary" onClick={() => navigate('/admin/templates')}>Retour</Button>
      </div>
    )
  }

  return (
    <div className="space-y-6 animate-fade-in">
      <div className="flex items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-text-primary">Configurer Template</h1>
          <p className="text-sm text-text-muted mt-1">{template.name} ({template.code})</p>
        </div>
        <div className="flex items-center gap-2">
          <Button
            onClick={() => syncTemplateMutation.mutate()}
            isLoading={syncTemplateMutation.isPending}
            className="flex items-center gap-2"
          >
            <RefreshCw className="w-4 h-4" />
            Sync vers Firebase
          </Button>
          <Link to="/admin/templates">
            <Button variant="secondary" className="flex items-center gap-2"><ArrowLeft className="w-4 h-4" />Retour templates</Button>
          </Link>
        </div>
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-2 gap-6">
        <div className="xl:col-span-2 bg-surface-800/80 backdrop-blur-md border border-surface-700 rounded-xl p-5 space-y-4">
          <div className="flex items-center justify-between gap-3">
            <h2 className="text-lg font-semibold text-text-primary">Tarification Cargo (entre agences)</h2>
            <Button
              onClick={() => savePricingMutation.mutate()}
              isLoading={savePricingMutation.isPending}
            >
              Enregistrer tarification
            </Button>
          </div>

          <p className="text-xs text-text-muted">
            Ces prix sont definis entre l'agence depart et l'agence arrivee (pas par stop): {template.start_office_name} {'->'} {template.end_office_name}
          </p>

          <p className="text-xs text-brand-300">
            Prix passager (auto depuis Segment Tarifs): {computedPassengerPrice > 0 ? `${computedPassengerPrice} ${pricingCurrency}` : 'non calculable'}
          </p>

          <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
            <input
              type="number"
              min={0}
              className="bg-surface-900 border border-surface-700 rounded-lg px-3 py-2 text-sm text-text-primary"
              value={cargoSmallPrice || ''}
              onChange={(e) => setCargoSmallPrice(Number(e.target.value) || 0)}
              placeholder="Colis S (DZD)"
              title="Prix colis Small entre agences"
            />
            <input
              type="number"
              min={0}
              className="bg-surface-900 border border-surface-700 rounded-lg px-3 py-2 text-sm text-text-primary"
              value={cargoMediumPrice || ''}
              onChange={(e) => setCargoMediumPrice(Number(e.target.value) || 0)}
              placeholder="Colis M (DZD)"
              title="Prix colis Medium entre agences"
            />
            <input
              type="number"
              min={0}
              className="bg-surface-900 border border-surface-700 rounded-lg px-3 py-2 text-sm text-text-primary"
              value={cargoLargePrice || ''}
              onChange={(e) => setCargoLargePrice(Number(e.target.value) || 0)}
              placeholder="Colis L (DZD)"
              title="Prix colis Large entre agences"
            />
            <input
              type="text"
              className="bg-surface-900 border border-surface-700 rounded-lg px-3 py-2 text-sm text-text-primary"
              value={pricingCurrency}
              onChange={(e) => setPricingCurrency(e.target.value.toUpperCase().slice(0, 3))}
              placeholder="Devise (ex: DZD)"
              title="Code devise (3 lettres)"
            />
          </div>
        </div>

        <div className="bg-surface-800/80 backdrop-blur-md border border-surface-700 rounded-xl p-5 space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-semibold text-text-primary">Stops</h2>
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
                className={`flex items-center gap-3 bg-surface-900 border rounded-lg px-3 py-2 ${draggedStopId === stop.id ? 'border-brand-500/60 bg-brand-500/10' : 'border-surface-700'}`}
              >
                <GripVertical className="w-4 h-4 text-text-muted cursor-grab" />
                <span className="w-8 text-center text-xs font-bold text-brand-300">#{stop.stop_order}</span>
                <span className="flex-1 text-sm text-text-primary">{getStopLabel(stop)}</span>
                <Button
                  variant="danger"
                  className="p-1.5 h-auto"
                  onClick={() => { if (confirm('Supprimer cet arret ?')) stopDeleteMutation.mutate(stop.id) }}
                  isLoading={stopDeleteMutation.isPending && stopDeleteMutation.variables === stop.id}
                >
                  <Trash2 className="w-4 h-4" />
                </Button>
              </div>
            ))}
            {sortedStops.length === 0 && <p className="text-sm text-text-muted">Aucun arret configure.</p>}
          </div>

          <div className="pt-3 border-t border-surface-700 grid grid-cols-1 md:grid-cols-3 gap-3">
            <input
              list="template-stop-options"
              className="bg-surface-900 border border-surface-700 rounded-lg px-3 py-2 text-sm text-text-primary"
              value={newStopInput}
              onChange={(e) => setNewStopInput(e.target.value)}
              placeholder="Tapez un stop ou selectionnez une agence"
            />
            <datalist id="template-stop-options">
              {activeOffices.map((office: Office) => <option key={office.id} value={office.name} />)}
            </datalist>
            <input
              type="number"
              min={1}
              className="bg-surface-900 border border-surface-700 rounded-lg px-3 py-2 text-sm text-text-primary"
              value={newStopOrder}
              onChange={(e) => setNewStopOrder(Number(e.target.value) || 1)}
            />
            <Button
              onClick={() => {
                const rawValue = newStopInput.trim()
                if (!rawValue) return
                const matchedOffice = activeOffices.find((office) => office.name.toLowerCase() === rawValue.toLowerCase())
                stopMutation.mutate({
                  route_template: template.id,
                  office: matchedOffice?.id ?? null,
                  stop_name: matchedOffice ? '' : rawValue,
                  stop_order: newStopOrder,
                })
              }}
              isLoading={stopMutation.isPending}
            >Ajouter stop</Button>
          </div>
        </div>

        <div className="bg-surface-800/80 backdrop-blur-md border border-surface-700 rounded-xl p-5 space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-semibold text-text-primary">Segment Tarifs</h2>
            <Button
              variant="secondary"
              onClick={() => autoFillSegmentsMutation.mutate(template)}
              isLoading={autoFillSegmentsMutation.isPending}
              className="text-xs"
            >Auto-fill adjacent</Button>
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
                  onClick={() => segmentMutation.mutate({ id: segment.id, is_active: !segment.is_active })}
                  isLoading={segmentMutation.isPending}
                >
                  ✎
                </Button>
                <Button
                  variant="danger"
                  className="p-1.5 h-auto"
                  onClick={() => { if (confirm('Supprimer ce segment ?')) segmentDeleteMutation.mutate(segment.id) }}
                  isLoading={segmentDeleteMutation.isPending && segmentDeleteMutation.variables === segment.id}
                >
                  <Trash2 className="w-4 h-4" />
                </Button>
              </div>
            ))}
            {sortedSegments.length === 0 && <p className="text-sm text-text-muted">Aucun segment configure.</p>}
          </div>

          <div className="pt-3 border-t border-surface-700 grid grid-cols-1 md:grid-cols-5 gap-3">
            <select
              className="bg-surface-900 border border-surface-700 rounded-lg px-3 py-2 text-sm text-text-primary"
              value={newSegmentFromStop}
              onChange={(e) => setNewSegmentFromStop(e.target.value ? Number(e.target.value) : '')}
            >
              <option value="">From stop</option>
              {sortedStops.map((stop) => <option key={stop.id} value={stop.id}>#{stop.stop_order} {getStopLabel(stop)}</option>)}
            </select>
            <select
              className="bg-surface-900 border border-surface-700 rounded-lg px-3 py-2 text-sm text-text-primary"
              value={newSegmentToStop}
              onChange={(e) => setNewSegmentToStop(e.target.value ? Number(e.target.value) : '')}
            >
              <option value="">To stop</option>
              {sortedStops.map((stop) => <option key={stop.id} value={stop.id}>#{stop.stop_order} {getStopLabel(stop)}</option>)}
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
                  route_template: template.id,
                  from_stop: newSegmentFromStop,
                  to_stop: newSegmentToStop,
                  passenger_price: newSegmentPrice,
                  currency: newSegmentCurrency || 'DZD',
                  is_active: newSegmentActive,
                })
              }}
              isLoading={segmentMutation.isPending}
            >Ajouter segment</Button>
          </div>
        </div>
      </div>

      {sectionError && (
        <div className="bg-red-500/10 border border-status-error/30 text-red-400 p-3 rounded-lg text-sm flex items-start gap-2">
          <ShieldAlert className="w-5 h-5 shrink-0" />
          <p>{typeof sectionError === 'string' ? sectionError : JSON.stringify(sectionError)}</p>
        </div>
      )}

      {syncTemplateMutation.isError && (
        <div className="bg-red-500/10 border border-status-error/30 text-red-400 p-3 rounded-lg text-sm flex items-start gap-2">
          <ShieldAlert className="w-5 h-5 shrink-0" />
          <p>{JSON.stringify((syncTemplateMutation.error as any)?.response?.data || (syncTemplateMutation.error as any)?.message || 'Echec de synchronisation Firebase')}</p>
        </div>
      )}

      {savePricingMutation.isError && (
        <div className="bg-red-500/10 border border-status-error/30 text-red-400 p-3 rounded-lg text-sm flex items-start gap-2">
          <ShieldAlert className="w-5 h-5 shrink-0" />
          <p>{JSON.stringify((savePricingMutation.error as any)?.response?.data || (savePricingMutation.error as any)?.message || 'Echec enregistrement tarification')}</p>
        </div>
      )}
    </div>
  )
}
