import { useEffect, useRef } from 'react'
import {
  ArcElement,
  BarController,
  BarElement,
  CategoryScale,
  Chart,
  DoughnutController,
  Filler,
  Legend,
  LinearScale,
  LineController,
  LineElement,
  PointElement,
  Tooltip,
  type ChartConfiguration,
  type ChartType,
} from 'chart.js'
import { TEMA } from './tema'

Chart.register(
  ArcElement,
  BarController,
  BarElement,
  CategoryScale,
  DoughnutController,
  Filler,
  Legend,
  LinearScale,
  LineController,
  LineElement,
  PointElement,
  Tooltip,
)
Chart.defaults.font.family = "system-ui, 'Segoe UI', Roboto, sans-serif"
Chart.defaults.color = TEMA.textoSecundario

// Crea el gráfico sobre el canvas y lo destruye antes de recrearlo o al desmontar: es lo que evita
// el error "Canvas is already in use". `config` tiene que venir de useMemo, cambia con los datos.
export function useGrafico<T extends ChartType>(config: ChartConfiguration<T>) {
  const canvasRef = useRef<HTMLCanvasElement>(null)

  useEffect(() => {
    if (!canvasRef.current) return
    const grafico = new Chart<T>(canvasRef.current, config)
    return () => grafico.destroy()
  }, [config])

  return canvasRef
}
