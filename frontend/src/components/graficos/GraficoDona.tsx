import { useMemo } from 'react'
import type { ChartConfiguration } from 'chart.js'
import { TEMA } from './tema'
import { useGrafico } from './useGrafico'

interface Props {
  titulo: string
  etiquetas: string[]
  valores: number[]
  colores: string[]
  formatoValor: (valor: number) => string
  formatoPorcentaje: (fraccion: number) => string
}

export function GraficoDona({ titulo, etiquetas, valores, colores, formatoValor, formatoPorcentaje }: Props) {
  const config = useMemo<ChartConfiguration<'doughnut'>>(() => {
    const total = valores.reduce((acc, valor) => acc + valor, 0)

    return {
      type: 'doughnut',
      data: {
        labels: etiquetas,
        datasets: [
          {
            data: valores,
            backgroundColor: colores,
            borderColor: TEMA.superficie,
            borderWidth: 2,
            hoverOffset: 4,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        animation: false,
        cutout: '62%',
        plugins: {
          legend: {
            position: 'bottom',
            labels: { usePointStyle: true, pointStyle: 'circle', padding: 16 },
          },
          tooltip: {
            callbacks: {
              label: (item) => `${formatoValor(item.parsed)} (${formatoPorcentaje(item.parsed / total)})`,
            },
          },
        },
      },
    }
  }, [etiquetas, valores, colores, formatoValor, formatoPorcentaje])

  const canvasRef = useGrafico(config)

  return (
    <div className="grafico-lienzo" style={{ height: 280 }}>
      <canvas ref={canvasRef} role="img" aria-label={titulo} />
    </div>
  )
}
