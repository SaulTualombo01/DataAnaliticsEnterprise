import Dashboard from '@/components/Dashboard'
import { Toaster } from '@/components/ui/toast'

import '@/index.css'

/**
 * Vista de analítica (M4) dentro del shell compartido. Solo se monta en modo demostración:
 * sus indicadores son sintéticos y el backend de M4 aún no está habilitado.
 */
function Analitica() {
  return (
    <div className="m4-root">
      <Toaster>
        <Dashboard />
      </Toaster>
    </div>
  )
}

export default Analitica
