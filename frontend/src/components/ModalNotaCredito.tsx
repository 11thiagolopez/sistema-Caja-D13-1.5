import { useEffect, useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import { getCliente } from '../api/clientes'
import {
  crearNotaCredito,
  descargarNotaCreditoFacturaPdf,
  descargarNotaCreditoPdf,
  emitirNotaCreditoFiscal,
} from '../api/notasCredito'
import { ApiRequestError } from '../api/client'
import { verBlob } from '../utils/descargarBlob'
import { numeroComprobante } from '../utils/formato'
import { Modal } from './Modal'
import { SelectorCliente } from './SelectorCliente'
import type { AlcanceNotaCredito, ClienteResponse, MotivoNotaCredito, NotaCreditoResponse, VentaResponse } from '../types/api'

const MOTIVOS: { value: MotivoNotaCredito; label: string }[] = [
  { value: 'DEVOLUCION_MERCADERIA', label: 'Devolución de mercadería (devuelve stock)' },
  { value: 'ANULACION_TOTAL', label: 'Anulación total (devuelve stock)' },
  { value: 'ERROR_TIPEO', label: 'Error de tipeo (no toca stock)' },
  { value: 'DESCUENTO_POSTERIOR', label: 'Descuento posterior (no toca stock)' },
]

interface ModalNotaCreditoProps {
  venta: VentaResponse
  onCerrar: () => void
  onCreada: () => void
}

// Se abre siempre desde una venta, facturada o no. Exige cliente: el importe se acredita en su saldo.
export function ModalNotaCredito({ venta, onCerrar, onCreada }: ModalNotaCreditoProps) {
  const { sesion } = useAuth()
  const [motivo, setMotivo] = useState<MotivoNotaCredito>('DEVOLUCION_MERCADERIA')
  const [alcance, setAlcance] = useState<AlcanceNotaCredito>('TOTAL')
  const [cantidades, setCantidades] = useState<Record<number, number>>(() =>
    Object.fromEntries(venta.detalles.map((d) => [d.idDetalle, d.cantidad])),
  )
  const [cliente, setCliente] = useState<ClienteResponse | null>(null)
  const [confirmando, setConfirmando] = useState(false)
  const [emitiendo, setEmitiendo] = useState(false)
  const [nota, setNota] = useState<NotaCreditoResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  const facturada = venta.estadoFactura === 'FACTURADA' && venta.factura != null

  useEffect(() => {
    if (venta.idCliente == null) return
    getCliente(venta.idCliente)
      .then(setCliente)
      .catch(() => setError('No se pudieron cargar los datos del cliente de la venta'))
  }, [venta.idCliente])

  async function onConfirmar() {
    if (!sesion || !cliente) return
    setError(null)
    setConfirmando(true)
    try {
      const creada = await crearNotaCredito({
        idVentaOriginal: venta.idVenta,
        idEmpleado: sesion.idEmpleado,
        idCliente: cliente.idCliente,
        motivo,
        alcance,
        detalles:
          alcance === 'PARCIAL'
            ? venta.detalles
                .filter((d) => (cantidades[d.idDetalle] ?? 0) > 0)
                .map((d) => ({ idDetalleVentaOriginal: d.idDetalle, cantidad: cantidades[d.idDetalle] }))
            : undefined,
      })
      setNota(creada)
      onCreada()
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo crear la nota de crédito')
    } finally {
      setConfirmando(false)
    }
  }

  async function onEmitirFiscal() {
    if (!nota) return
    setError(null)
    setEmitiendo(true)
    try {
      setNota(await emitirNotaCreditoFiscal(nota.idNotaCredito))
      onCreada()
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo emitir ante ARCA')
    } finally {
      setEmitiendo(false)
    }
  }

  async function abrirPdf(pedido: Promise<Blob>) {
    setError(null)
    try {
      verBlob(await pedido)
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo abrir el PDF')
    }
  }

  if (nota) {
    return (
      <Modal title={`Nota de crédito #${nota.idNotaCredito}`} onClose={onCerrar}>
        <p>
          ${nota.montoTotal.toFixed(2)} acreditados en el saldo de {nota.nombreCliente}.
        </p>
        {nota.esFiscal && nota.estado !== 'EMITIDA' && (
          <>
            <p>
              {nota.estado === 'ERROR'
                ? `ARCA rechazó la emisión: ${nota.errorDetalle}`
                : 'La venta tiene factura: falta emitir esta nota de crédito ante ARCA.'}
            </p>
            <p>Si no lo hacés ahora, podés retomarlo desde Comprobantes.</p>
            <button type="button" onClick={onEmitirFiscal} disabled={emitiendo}>
              {emitiendo && <span className="spinner" />}
              {emitiendo ? 'Emitiendo...' : nota.estado === 'ERROR' ? 'Reintentar' : 'Emitir ante ARCA'}
            </button>
          </>
        )}
        {nota.esFiscal && nota.estado === 'EMITIDA' && (
          <p>
            Nota de Crédito C Nº {numeroComprobante(nota.puntoVenta, nota.numero)}. CAE {nota.cae}
          </p>
        )}
        {error && <p className="error">{error}</p>}
        <div className="comprobante-acciones">
          {nota.esFiscal && nota.estado === 'EMITIDA' ? (
            <button type="button" onClick={() => abrirPdf(descargarNotaCreditoFacturaPdf(nota.idNotaCredito))}>
              Ver PDF fiscal
            </button>
          ) : (
            <button type="button" className="boton-secundario" onClick={() => abrirPdf(descargarNotaCreditoPdf(nota.idNotaCredito))}>
              Ver PDF interno
            </button>
          )}
          <button type="button" className="boton-secundario" onClick={onCerrar}>
            Cerrar
          </button>
        </div>
      </Modal>
    )
  }

  return (
    <Modal title={`Nota de crédito — venta #${venta.idVenta}`} onClose={onCerrar} wide>
      <p>
        {facturada
          ? `Referencia a la Factura C Nº ${numeroComprobante(venta.factura!.puntoVenta, venta.factura!.numero)}: se emite como Nota de Crédito C.`
          : 'La venta no tiene factura: la nota de crédito es interna y referencia a la venta.'}
      </p>

      <label>
        Motivo
        <select value={motivo} onChange={(e) => setMotivo(e.target.value as MotivoNotaCredito)}>
          {MOTIVOS.map((m) => (
            <option key={m.value} value={m.value}>
              {m.label}
            </option>
          ))}
        </select>
      </label>

      <fieldset>
        <label>
          <input type="radio" checked={alcance === 'TOTAL'} onChange={() => setAlcance('TOTAL')} />
          Total (toda la venta)
        </label>
        <label>
          <input type="radio" checked={alcance === 'PARCIAL'} onChange={() => setAlcance('PARCIAL')} />
          Parcial (elegir cantidades por línea)
        </label>
      </fieldset>

      <table>
        <thead>
          <tr>
            <th>Producto</th>
            <th>Cantidad vendida</th>
            {alcance === 'PARCIAL' && <th>Cantidad a acreditar</th>}
            <th>Precio unitario</th>
          </tr>
        </thead>
        <tbody>
          {venta.detalles.map((d) => (
            <tr key={d.idDetalle}>
              <td>{d.descripcionProducto}</td>
              <td>{d.cantidad}</td>
              {alcance === 'PARCIAL' && (
                <td>
                  <input
                    type="number"
                    min={0}
                    max={d.cantidad}
                    value={cantidades[d.idDetalle] ?? 0}
                    onChange={(e) => setCantidades((actual) => ({ ...actual, [d.idDetalle]: Number(e.target.value) }))}
                  />
                </td>
              )}
              <td>${d.precioUnitario.toFixed(2)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {venta.descuento > 0 && <p>La venta tuvo un descuento de ${venta.descuento.toFixed(2)}: se acredita lo que se cobró.</p>}

      <h4>Cliente (obligatorio)</h4>
      <SelectorCliente value={cliente} onChange={setCliente} />
      {error && <p className="error">{error}</p>}
      <button type="button" onClick={onConfirmar} disabled={confirmando || !cliente}>
        {confirmando && <span className="spinner" />}
        {confirmando ? 'Creando...' : 'Confirmar nota de crédito'}
      </button>
    </Modal>
  )
}
