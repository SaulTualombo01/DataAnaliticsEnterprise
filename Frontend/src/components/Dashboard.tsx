import { useEffect, useState } from 'react'
import { TriangleAlert } from 'lucide-react'
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'

import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { toast } from '@/components/ui/toast'
import summaryMock from '@/mocks/ticket-promedio.summary.json'

type LatestKpi = {
  kpi_code: string
  value: number | null
  unit: string
  status: string
  scope_complete: boolean
  evaluated_at: string
}

type DashboardSummary = {
  project_id: string
  generated_at: string
  execution_state: string
  dashboards: { id: string; name: string; widget_count: number }[]
  latest_kpis: LatestKpi[]
}

const SIMULATED_DELAY_MS = 2000
const KPI_CODE = 'TICKET_PROMEDIO'

const dayFormatter = new Intl.DateTimeFormat('es', { day: '2-digit', month: 'short', timeZone: 'UTC' })

function Dashboard() {
  const [summary, setSummary] = useState<DashboardSummary | null>(null)

  useEffect(() => {
    const timer = setTimeout(() => setSummary(summaryMock.data), SIMULATED_DELAY_MS)
    return () => clearTimeout(timer)
  }, [])

  const series = (summary?.latest_kpis ?? [])
    .filter((kpi) => kpi.kpi_code === KPI_CODE && kpi.value !== null)
    .map((kpi) => ({ day: dayFormatter.format(new Date(kpi.evaluated_at)), value: kpi.value }))
  const unit = summary?.latest_kpis.find((kpi) => kpi.kpi_code === KPI_CODE)?.unit ?? ''

  const showQualityWarning = () =>
    toast.add({
      type: 'warning',
      title: 'Advertencia de calidad',
      description: 'Advertencia: Exactitud del dato al 94%',
    })

  return (
    <main className="mx-auto flex min-h-svh max-w-3xl flex-col gap-4 p-6">
      <Card>
        <CardHeader>
          <CardTitle>Ticket Promedio</CardTitle>
          <CardDescription>Datos sintéticos · últimos 7 días</CardDescription>
        </CardHeader>
        <CardContent>
          {summary === null ? (
            <div className="flex flex-col gap-3" aria-busy="true" aria-label="Cargando KPI">
              <Skeleton className="h-4 w-40" />
              <Skeleton className="h-64 w-full" />
            </div>
          ) : (
            <div className="h-64 w-full">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={series} margin={{ top: 8, right: 16, bottom: 0, left: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                  <XAxis dataKey="day" stroke="var(--muted-foreground)" fontSize={12} />
                  <YAxis stroke="var(--muted-foreground)" fontSize={12} unit={` ${unit}`} width={72} />
                  <Tooltip formatter={(value) => [`${value} ${unit}`, 'Ticket promedio']} />
                  <Line type="monotone" dataKey="value" stroke="var(--primary)" strokeWidth={2} dot={{ r: 3 }} />
                </LineChart>
              </ResponsiveContainer>
            </div>
          )}
        </CardContent>
      </Card>

      <Button variant="outline" className="self-start" onClick={showQualityWarning}>
        <TriangleAlert aria-hidden="true" />
        Simular advertencia de calidad
      </Button>
    </main>
  )
}

export default Dashboard
