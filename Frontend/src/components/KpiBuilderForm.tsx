import { useEffect, useMemo, useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { Controller, useForm } from 'react-hook-form'
import { z } from 'zod'

import { Button } from '@/components/ui/button'
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { toast } from '@/components/ui/toast'
import { fetchCatalogAssets } from '@/services/catalogMock'
import type { Asset } from '@/shared/contracts'

// Mismos valores que KpiCategory en docs/contratos/m4/analitica-http.schema.json.
const CATEGORIES = [
  { value: 'calidad', label: 'Calidad' },
  { value: 'volumen', label: 'Volumen' },
  { value: 'completitud', label: 'Completitud' },
  { value: 'rendimiento', label: 'Rendimiento' },
  { value: 'comercial', label: 'Comercial' },
] as const

const DATA_SOURCE_REQUIRED = 'Selecciona una fuente de datos'

function buildKpiSchema(assetIds: ReadonlySet<string>) {
  return z.object({
    name: z
      .string()
      .trim()
      .min(3, 'El nombre debe tener al menos 3 caracteres')
      .max(120, 'El nombre no puede superar 120 caracteres'),
    category: z.enum(
      CATEGORIES.map((category) => category.value),
      { error: 'Selecciona una categoría' },
    ),
    dataSource: z
      .string({ error: DATA_SOURCE_REQUIRED })
      .refine((assetId) => assetIds.has(assetId), DATA_SOURCE_REQUIRED),
    alertThreshold: z
      .string()
      .trim()
      .min(1, 'Ingresa un umbral de alerta')
      .transform(Number)
      .pipe(z.number({ error: 'El umbral debe ser un número' })),
  })
}

type KpiSchema = ReturnType<typeof buildKpiSchema>
type KpiFormInput = z.input<KpiSchema>
type KpiFormOutput = z.output<KpiSchema>

type CatalogState = { status: 'loading' } | { status: 'error' } | { status: 'ready'; assets: Asset[] }

const EMPTY_FORM: Partial<KpiFormInput> = {
  name: '',
  category: undefined,
  dataSource: undefined,
  alertThreshold: '',
}

function KpiBuilderForm() {
  const [catalog, setCatalog] = useState<CatalogState>({ status: 'loading' })

  useEffect(() => {
    let cancelled = false
    fetchCatalogAssets()
      .then((assets) => {
        if (!cancelled) setCatalog({ status: 'ready', assets })
      })
      .catch(() => {
        if (!cancelled) setCatalog({ status: 'error' })
      })
    return () => {
      cancelled = true
    }
  }, [])

  const dataSources = useMemo(
    () => (catalog.status === 'ready' ? catalog.assets.map((asset) => ({ value: asset.id, label: asset.name })) : []),
    [catalog],
  )
  const resolver = useMemo(
    () => zodResolver(buildKpiSchema(new Set(dataSources.map((source) => source.value)))),
    [dataSources],
  )

  const form = useForm<KpiFormInput, unknown, KpiFormOutput>({
    resolver,
    defaultValues: EMPTY_FORM,
    mode: 'onChange',
  })

  const onSubmit = (kpi: KpiFormOutput) => {
    toast.add({
      type: 'success',
      title: 'Configuración guardada',
      description: `KPI "${kpi.name}" guardado con umbral ${kpi.alertThreshold} (simulado).`,
    })
    form.reset(EMPTY_FORM)
  }

  return (
    <form onSubmit={form.handleSubmit(onSubmit)} noValidate>
      <FieldGroup>
        <Controller
          name="name"
          control={form.control}
          render={({ field, fieldState }) => (
            <Field data-invalid={fieldState.invalid}>
              <FieldLabel htmlFor="kpi-name">Nombre del KPI</FieldLabel>
              <Input
                {...field}
                id="kpi-name"
                placeholder="Ticket promedio mensual"
                autoComplete="off"
                aria-invalid={fieldState.invalid}
              />
              <FieldError errors={[fieldState.error]} />
            </Field>
          )}
        />

        <Controller
          name="category"
          control={form.control}
          render={({ field, fieldState }) => (
            <Field data-invalid={fieldState.invalid}>
              <FieldLabel htmlFor="kpi-category">Categoría</FieldLabel>
              <Select items={CATEGORIES} value={field.value ?? null} onValueChange={field.onChange}>
                <SelectTrigger
                  id="kpi-category"
                  className="w-full"
                  aria-invalid={fieldState.invalid}
                  onBlur={field.onBlur}
                >
                  <SelectValue placeholder="Selecciona una categoría" />
                </SelectTrigger>
                <SelectContent>
                  {CATEGORIES.map((category) => (
                    <SelectItem key={category.value} value={category.value}>
                      {category.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <FieldError errors={[fieldState.error]} />
            </Field>
          )}
        />

        <Controller
          name="dataSource"
          control={form.control}
          render={({ field, fieldState }) => (
            <Field data-invalid={fieldState.invalid}>
              <FieldLabel htmlFor="kpi-data-source">Fuente de datos</FieldLabel>
              {catalog.status === 'loading' ? (
                <Skeleton className="h-8 w-full" aria-busy="true" aria-label="Cargando fuentes de datos" />
              ) : (
                <Select
                  name={field.name}
                  items={dataSources}
                  value={field.value ?? null}
                  onValueChange={field.onChange}
                  disabled={catalog.status === 'error'}
                >
                  <SelectTrigger
                    id="kpi-data-source"
                    className="w-full"
                    aria-invalid={fieldState.invalid}
                    onBlur={field.onBlur}
                  >
                    <SelectValue placeholder="Selecciona un activo del catálogo" />
                  </SelectTrigger>
                  <SelectContent>
                    {dataSources.map((source) => (
                      <SelectItem key={source.value} value={source.value}>
                        {source.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
              {catalog.status === 'error' ? (
                <FieldError>No se pudo cargar el catálogo de activos. Recarga la página para reintentar.</FieldError>
              ) : (
                <FieldDescription>Activos sintéticos del catálogo (M1).</FieldDescription>
              )}
              <FieldError errors={[fieldState.error]} />
            </Field>
          )}
        />

        <Controller
          name="alertThreshold"
          control={form.control}
          render={({ field, fieldState }) => (
            <Field data-invalid={fieldState.invalid}>
              <FieldLabel htmlFor="kpi-alert-threshold">Umbral de alerta</FieldLabel>
              <Input
                {...field}
                id="kpi-alert-threshold"
                type="number"
                step="any"
                inputMode="decimal"
                placeholder="40"
                aria-invalid={fieldState.invalid}
              />
              <FieldError errors={[fieldState.error]} />
            </Field>
          )}
        />

        <Button type="submit" className="self-start" disabled={form.formState.isSubmitting}>
          Guardar KPI
        </Button>
      </FieldGroup>
    </form>
  )
}

export default KpiBuilderForm
