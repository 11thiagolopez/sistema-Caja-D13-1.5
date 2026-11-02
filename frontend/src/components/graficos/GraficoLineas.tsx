import { useMemo } from 'react'
import type { ChartConfiguration } from 'chart.js'
import { TEMA } from './tema'
import { useGrafico } from './useGrafico'

interface Props {
  titulo: string
  etiquetas: string[]
  valores: number[]
  // Índices a destacar; -1 si no hay.
  indiceMejor: number
  indicePeor: number
  formatoValor: (valor: number) => string
  formatoEje: (valor: number) => string
  formatoEtiquetaEje: (etiqueta: string) => string
  formatoEtiquetaTooltip: (etiqueta: string) => string
}

const MAX_DIAS_CON_PUNTOS = 45

export function GraficoLineas({
  titulo,
  etiquetas,
  valores,
  indiceMejor,
  indicePeor,
  formatoValor,
  formatoEje,
  formatoEtiquetaEje,
  formatoEtiquetaTooltip,
}: Props) {
  const config = useMemo<ChartConfiguration<'line'>>(() => {
    const radioNormal = etiquetas.length > MAX_DIAS_CON_PUNTOS ? 0 : 3
    const destacado = (i: number) => i === indiceMejor || i === indicePeor
    const colorDelPunto = (i: number) => {
      if (i === indiceMejor) return TEMA.mejor
      if (i === indicePeor) return TEMA.peor
      return TEMA.serie
    }
    const formaDelPunto = (i: number) => {
      if (i === indiceMejor) return 'triangle'
      if (i === indicePeor) return 'rectRot'
      return 'circle'
    }

    return {
      type: 'line',
      data: {
        labels: etiquetas,
        datasets: [
          {
            data: valores,
            borderColor: TEMA.serie,
            backgroundColor: TEMA.serieLavado,
            borderWidth: 2,
            borderJoinStyle: 'round',
            borderCapStyle: 'round',
            fill: true,
            tension: 0,
            pointRadius: etiquetas.map((_, i) => (destacado(i) ? 8 : radioNormal)),
            pointHoverRadius: etiquetas.map((_, i) => (destacado(i) ? 9 : 5)),
            pointHitRadius: 12,
            pointStyle: etiquetas.map((_, i) => formaDelPunto(i)),
            pointBackgroundColor: etiquetas.map((_, i) => colorDelPunto(i)),
            pointBorderColor: TEMA.superficie,
            pointBorderWidth: 2,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        animation: false,
        interaction: { mode: 'index', intersect: false },
        // Evita que el marcador de un extremo quede recortado contra el borde del gráfico.
        layout: { padding: { top: 12, right: 8 } },
        plugins: {
          legend: { display: false },
          tooltip: {
            displayColors: false,
            callbacks: {
              title: (items) => formatoEtiquetaTooltip(etiquetas[items[0].dataIndex]),
              label: (item) => formatoValor(item.parsed.y ?? 0),
            },
          },
        },
        scales: {
          x: {
            grid: { display: false },
            ticks: {
              autoSkip: true,
              maxRotation: 0,
              callback: (valor) => formatoEtiquetaEje(etiquetas[Number(valor)]),
            },
          },
          y: {
            beginAtZero: true,
            grid: { color: TEMA.grilla },
            border: { display: false },
            ticks: { callback: (valor) => formatoEje(Number(valor)) },
          },
        },
      },
    }
  }, [
    etiquetas,
    valores,
    indiceMejor,
    indicePeor,
    formatoValor,
    formatoEje,
    formatoEtiquetaEje,
    formatoEtiquetaTooltip,
  ])

  const canvasRef = useGrafico(config)

  return (
    <div className="grafico-lienzo" style={{ height: 280 }}>
      <canvas ref={canvasRef} role="img" aria-label={titulo} />
    </div>
  )
}
