import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { descargarPdfFactura } from '../api/facturas'
import {
  descargarNotaCreditoFacturaPdf,
  descargarNotaCreditoPdf,
  emitirNotaCreditoFiscal,
  getNotasCredito,
} from '../api/notasCredito'
import { getVentas } from '../api/ventas'
import { ApiRequestError } from '../api/client'
import { hoyIso } from '../utils/date'
import { verBlob } from '../utils/descargarBlob'
import { formatoFechaHora, numeroComprobante } from '../utils/formato'
import type { NotaCreditoResponse, VentaResponse } from '../types/api'

type Filtro = 'TODOS' | 'FACTURAS' | 'NOTAS'

interface Fila {
  clave: string
  fecha: string
  tipo: string
  numero: string
  cliente: string
  importe: number
  idVenta: number
  estado: string
  nota: NotaCreditoResponse | null
}

function filaFactura(venta: VentaResponse): Fila | null {
  const factura = venta.factura
  if (!factura) return null
  return {
    clave: `F${factura.idFactura}`,
    fecha: venta.fecha,
    tipo: 'Factura C',
    numero: numeroComprobante(factura.puntoVenta, factura.numero),
    cliente: factura.clienteNombre ?? 'Consumidor Final',
    importe: factura.importe,
    idVenta: venta.idVenta,
    estado: 'Emitida',
    nota: null,
  }
}

function estadoNota(nota: NotaCreditoResponse): string {
  if (!nota.esFiscal) return 'Interna'
  if (nota.estado === 'EMITIDA') return 'Emitida'
  return nota.estado === 'ERROR' ? `Error: ${nota.errorDetalle ?? 'ARCA rechazó la emisión'}` : 'Falta emitir ante ARCA'
}

function filaNota(nota: NotaCreditoResponse): Fila {
  return {
    clave: `N${nota.idNotaCredito}`,
    fecha: nota.fecha,
    tipo: nota.esFiscal ? 'Nota de crédito C' : 'Nota de crédito interna',
    numero: nota.numero != null ? numeroComprobante(nota.puntoVenta, nota.numero) : `#${nota.idNotaCredito}`,
    cliente: nota.nombreCliente,
    importe: nota.montoTotal,
    idVenta: nota.idVentaOriginal,
    estado: estadoNota(nota),
    nota,
  }
}

// El historial filtrado por lo fiscal: facturas emitidas y notas de crédito. También es donde se
// retoma una nota de crédito de una venta facturada que quedó sin emitir ante ARCA.
export function Comprobantes() {
  const [desde, setDesde] = useState(hoyIso().slice(0, 8) + '01')
  const [hasta, setHasta] = useState(hoyIso())
  const [filtro, setFiltro] = useState<Filtro>('TODOS')
  const [filas, setFilas] = useState<Fila[]>([])
  const [cargando, setCargando] = useState(false)
  const [procesando, setProcesando] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const cargar = useCallback(async () => {
    setError(null)
    setCargando(true)
    try {
      const [ventas, notas] = await Promise.all([getVentas(desde, hasta, 'FACTURADA'), getNotasCredito(desde, hasta)])
      const facturas = ventas.map(filaFactura).filter((f): f is Fila => f !== null)
      setFilas([...facturas, ...notas.map(filaNota)].sort((a, b) => b.fecha.localeCompare(a.fecha)))
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudieron cargar los comprobantes')
    } finally {
      setCargando(false)
    }
  }, [desde, hasta])

  useEffect(() => {
    cargar()
    // Solo al entrar: las fechas se aplican con "Buscar".
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  function onBuscar(event: FormEvent) {
    event.preventDefault()
    cargar()
  }

  async function ejecutar(clave: string, accion: () => Promise<void>) {
    setError(null)
    setProcesando(clave)
    try {
      await accion()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo completar la acción')
    } finally {
      setProcesando(null)
    }
  }

  function verPdf(fila: Fila) {
    return ejecutar(fila.clave, async () => {
      if (!fila.nota) {
        await descargarPdfFactura(fila.idVenta)
      } else if (fila.nota.esFiscal && fila.nota.estado === 'EMITIDA') {
        verBlob(await descargarNotaCreditoFacturaPdf(fila.nota.idNotaCredito))
      } else {
        verBlob(await descargarNotaCreditoPdf(fila.nota.idNotaCredito))
      }
    })
  }

  function emitirNota(fila: Fila) {
    return ejecutar(fila.clave, async () => {
      if (!fila.nota) return
      const emitida = await emitirNotaCreditoFiscal(fila.nota.idNotaCredito)
      setFilas((actual) => actual.map((f) => (f.clave === fila.clave ? filaNota(emitida) : f)))
    })
  }

  const visibles = filas.filter((f) => filtro === 'TODOS' || (filtro === 'FACTURAS') === (f.nota === null))

  return (
    <div>
      <h2>Comprobantes</h2>
      <form onSubmit={onBuscar}>
        <label>
          Desde
          <input type="date" value={desde} onChange={(e) => setDesde(e.target.value)} />
        </label>
        <label>
          Hasta
          <input type="date" value={hasta} onChange={(e) => setHasta(e.target.value)} />
        </label>
        <label>
          Tipo
          <select value={filtro} onChange={(e) => setFiltro(e.target.value as Filtro)}>
            <option value="TODOS">Todos</option>
            <option value="FACTURAS">Facturas</option>
            <option value="NOTAS">Notas de crédito</option>
          </select>
        </label>
        <button type="submit" disabled={cargando}>
          {cargando && <span className="spinner" />}
          {cargando ? 'Buscando...' : 'Buscar'}
        </button>
      </form>

      {error && <p className="error">{error}</p>}

      <table>
        <thead>
          <tr>
            <th>Fecha</th>
            <th>Comprobante</th>
            <th>Número</th>
            <th>Cliente</th>
            <th>Venta</th>
            <th>Importe</th>
            <th>Estado</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {!cargando && visibles.length === 0 && (
            <tr>
              <td colSpan={8}>No hay comprobantes en este período.</td>
            </tr>
          )}
          {visibles.map((fila) => (
            <tr key={fila.clave}>
              <td>{formatoFechaHora(fila.fecha)}</td>
              <td>{fila.tipo}</td>
              <td>{fila.numero}</td>
              <td>{fila.cliente}</td>
              <td>#{fila.idVenta}</td>
              <td>{fila.importe.toFixed(2)}</td>
              <td>{fila.estado}</td>
              <td>
                <span className="comprobante-acciones">
                  <button type="button" onClick={() => verPdf(fila)} disabled={procesando === fila.clave}>
                    Ver PDF
                  </button>
                  {fila.nota?.esFiscal && fila.nota.estado !== 'EMITIDA' && (
                    <button type="button" onClick={() => emitirNota(fila)} disabled={procesando === fila.clave}>
                      {procesando === fila.clave && <span className="spinner" />}
                      {fila.nota.estado === 'ERROR' ? 'Reintentar' : 'Emitir ante ARCA'}
                    </button>
                  )}
                </span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
