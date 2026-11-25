import { useEffect, useState } from 'react'
import { getHistorialCliente } from '../api/ventas'
import { estadoVisibleTrabajo } from '../utils/estadoTrabajo'
import { formatoDiaLargo, formatoPesos } from '../utils/formato'
import type { VentaResponse } from '../types/api'

const ESPERA_MS = 500

/**
 * Trabajos anteriores del mismo teléfono o de la misma dirección (Etapa 3 del documento del
 * dueño: "saber qué se hizo antes, por ejemplo qué cerradura se puso"). Se busca solo mientras el
 * jefe escribe, con una pequeña espera para no pedir en cada tecla. Solo lectura.
 */
export function HistorialCliente({
  telefono,
  direccion,
  idVentaActual,
}: {
  telefono: string
  direccion: string
  idVentaActual: number | null
}) {
  const [anteriores, setAnteriores] = useState<VentaResponse[]>([])

  useEffect(() => {
    if (telefono.trim().length < 8 && direccion.trim().length < 4) {
      setAnteriores([])
      return
    }
    let vigente = true
    const espera = setTimeout(() => {
      getHistorialCliente(telefono, direccion, idVentaActual)
        .then((lista) => {
          if (vigente) setAnteriores(lista)
        })
        .catch(() => {
          if (vigente) setAnteriores([])
        })
    }, ESPERA_MS)
    return () => {
      vigente = false
      clearTimeout(espera)
    }
  }, [telefono, direccion, idVentaActual])

  if (anteriores.length === 0) return null

  return (
    <div className="historial-cliente">
      <strong>Trabajos anteriores de este cliente o dirección ({anteriores.length})</strong>
      <ul>
        {anteriores.map((t) => (
          <li key={t.idVenta}>
            <span className="historial-cliente-encabezado">
              #{t.idVenta} · {t.fechaProgramada ? formatoDiaLargo(t.fechaProgramada) : 'sin fecha'} ·{' '}
              {estadoVisibleTrabajo(t).etiqueta}
              {t.nombreTecnico && ` · ${t.nombreTecnico}`}
            </span>
            <span>
              {t.direccionTrabajo || 'Sin dirección'}
              {t.clienteNombre && ` — ${t.clienteNombre}`}
            </span>
            {t.descripcionTrabajo && <span className="texto-con-saltos">{t.descripcionTrabajo}</span>}
            {t.detalles.length > 0 && (
              <span className="historial-cliente-items">
                {t.detalles.map((d) => `${d.cantidad} × ${d.descripcionProducto}`).join(' · ')}
                {' — '}
                {formatoPesos(t.totalVenta)}
              </span>
            )}
          </li>
        ))}
      </ul>
    </div>
  )
}
