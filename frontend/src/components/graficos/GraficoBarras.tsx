import { useMemo } from 'react'
import type { ChartConfiguration, Plugin, Scale } from 'chart.js'
import { TEMA } from './tema'
import { useGrafico } from './useGrafico'

interface Props {
  titulo: string
  etiquetas: string[]
  valores: number[]
  horizontal?: boolean
  formatoValor: (valor: number) => string
  formatoEje?: (valor: number) => string
  // Texto del tooltip para una barra; por defecto, su valor formateado.
  textoTooltip?: (indice: number) => string
}

const LARGO_MAX_ETIQUETA = 26
const LARGO_MAX_ETIQUETA_ANGOSTO = 16
const ANCHO_ANGOSTO = 480
const ALTO_POR_BARRA = 34
const ESPACIO_VALOR_EN_PUNTA = 104

function truncar(texto: string, largo: number): string {
  return texto.length > largo ? `${texto.slice(0, largo - 1)}…` : texto
}

export function GraficoBarras({
  titulo,
  etiquetas,
  valores,
  horizontal = false,
  formatoValor,
  formatoEje = formatoValor,
  textoTooltip,
}: Props) {
  const config = useMemo<ChartConfiguration<'bar'>>(() => {
    // En las horizontales el valor va escrito en la punta de cada barra, y por eso no hace falta eje.
    const valorEnPunta: Plugin<'bar'> = {
      id: 'valorEnPunta',
      afterDatasetsDraw(grafico) {
        const { ctx } = grafico
        ctx.save()
        ctx.font = `13px ${grafico.options.font?.family ?? 'sans-serif'}`
        ctx.fillStyle = TEMA.textoPrimario
        ctx.textAlign = 'left'
        ctx.textBaseline = 'middle'
        grafico.getDatasetMeta(0).data.forEach((barra, i) => {
          ctx.fillText(formatoValor(valores[i]), barra.x + 6, barra.y)
        })
        ctx.restore()
      },
    }

    return {
      type: 'bar',
      data: {
        labels: etiquetas,
        datasets: [
          {
            data: valores,
            backgroundColor: TEMA.serie,
            hoverBackgroundColor: '#256abf',
            maxBarThickness: 24,
            borderSkipped: false,
            borderRadius: horizontal
              ? { topRight: 4, bottomRight: 4, topLeft: 0, bottomLeft: 0 }
              : { topLeft: 4, topRight: 4, bottomLeft: 0, bottomRight: 0 },
          },
        ],
      },
      plugins: horizontal ? [valorEnPunta] : [],
      options: {
        indexAxis: horizontal ? 'y' : 'x',
        responsive: true,
        maintainAspectRatio: false,
        animation: false,
        layout: { padding: { right: horizontal ? ESPACIO_VALOR_EN_PUNTA : 0 } },
        plugins: {
          legend: { display: false },
          tooltip: {
            displayColors: false,
            callbacks: {
              label: (item) => (textoTooltip ? textoTooltip(item.dataIndex) : formatoValor(item.parsed[horizontal ? 'x' : 'y'] ?? 0)),
            },
          },
        },
        scales: horizontal
          ? {
              x: { display: false, beginAtZero: true },
              y: {
                grid: { display: false },
                ticks: {
                  autoSkip: false,
                  // La etiqueta completa queda en el título del tooltip.
                  callback(this: Scale, valor) {
                    const largo = this.chart.width < ANCHO_ANGOSTO ? LARGO_MAX_ETIQUETA_ANGOSTO : LARGO_MAX_ETIQUETA
                    return truncar(this.getLabelForValue(Number(valor)), largo)
                  },
                },
              },
            }
          : {
              x: { grid: { display: false } },
              y: {
                beginAtZero: true,
                grid: { color: TEMA.grilla },
                border: { display: false },
                ticks: { callback: (valor) => formatoEje(Number(valor)) },
              },
            },
      },
    }
  }, [etiquetas, valores, horizontal, formatoValor, formatoEje, textoTooltip])

  const canvasRef = useGrafico(config)
  const alto = horizontal ? Math.max(120, etiquetas.length * ALTO_POR_BARRA + 16) : 280

  return (
    <div className="grafico-lienzo" style={{ height: alto }}>
      <canvas ref={canvasRef} role="img" aria-label={titulo} />
    </div>
  )
}
