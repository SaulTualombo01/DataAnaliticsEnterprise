import { zodResolver } from '@hookform/resolvers/zod'
import { Controller, useForm } from 'react-hook-form'
import { z } from 'zod'

import { Button } from '@/components/ui/button'
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { toast } from '@/components/ui/toast'

// Mismos valores que KpiCategory en docs/contratos/m4/analitica-http.schema.json.
const CATEGORIES = [
  { value: 'calidad', label: 'Calidad' },
  { value: 'volumen', label: 'Volumen' },
  { value: 'completitud', label: 'Completitud' },
  { value: 'rendimiento', label: 'Rendimiento' },
  { value: 'comercial', label: 'Comercial' },
] as const

// Sintético: el catálogo de M1 aún no expone activos. Sustituir por su consulta cuando exista.
const DATA_SOURCES = [
  { value: 'ventas_historicas', label: 'ventas_historicas' },
  { value: 'sesiones_web', label: 'sesiones_web' },
  { value: 'inventario', label: 'inventario' },
] as const

const kpiSchema = z.object({
  name: z
    .string()
    .trim()
    .min(3, 'El nombre debe tener al menos 3 caracteres')
    .max(120, 'El nombre no puede superar 120 caracteres'),
  category: z.enum(
    CATEGORIES.map((category) => category.value),
    { error: 'Selecciona una categoría' },
  ),
  dataSource: z.enum(
    DATA_SOURCES.map((source) => source.value),
    { error: 'Selecciona una fuente de datos' },
  ),
  alertThreshold: z
    .string()
    .trim()
    .min(1, 'Ingresa un umbral de alerta')
    .transform(Number)
    .pipe(z.number({ error: 'El umbral debe ser un número' })),
})

type KpiFormInput = z.input<typeof kpiSchema>
type KpiFormOutput = z.output<typeof kpiSchema>

const EMPTY_FORM: Partial<KpiFormInput> = {
  name: '',
  category: undefined,
  dataSource: undefined,
  alertThreshold: '',
}

function KpiBuilderForm() {
  const form = useForm<KpiFormInput, unknown, KpiFormOutput>({
    resolver: zodResolver(kpiSchema),
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
              <Select items={DATA_SOURCES} value={field.value ?? null} onValueChange={field.onChange}>
                <SelectTrigger
                  id="kpi-data-source"
                  className="w-full"
                  aria-invalid={fieldState.invalid}
                  onBlur={field.onBlur}
                >
                  <SelectValue placeholder="Selecciona una tabla" />
                </SelectTrigger>
                <SelectContent>
                  {DATA_SOURCES.map((source) => (
                    <SelectItem key={source.value} value={source.value}>
                      {source.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <FieldDescription>Tablas sintéticas de e-commerce.</FieldDescription>
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
