import { useEffect, useState } from 'react'
import { getCliente } from '../api/clientes'
import { descargarPdfFactura, facturarVenta } from '../api/facturas'
import { getVentas } from '../api/ventas'
import { ApiRequestError } from '../api/client'
import { hoyIso } from '../utils/date'
import { formatoFechaHora, numeroComprobante } from '../utils/formato'
import { Modal } from './Modal'
import { SelectorCliente } from './SelectorCliente'
import type { ClienteResponse, FacturaFiscalResponse, VentaResponse } from '../types/api'

interface ModalFacturacionProps {
  venta?: VentaResponse | null
  cliente?: ClienteResponse | null
  onCerrar: () => void
  // También con un rechazo de ARCA: la pantalla que lo abrió tiene que reflejar el último intento.
  onResultado: (factura: FacturaFiscalResponse) => void
}

function ventaFacturable(venta: VentaResponse, idCliente: number | undefined): boolean {
  return (
    venta.estado === 'CONFIRMADA' &&
    venta.estadoFactura === 'SIN_FACTURAR' &&
    !venta.tieneNotaCredito &&
    (venta.idCliente == null || venta.idCliente === idCliente)
  )
}

// Único lugar donde se emite una factura desde el sistema, salvo al cobrar. Recibe una venta, un
// cliente o ambos, y pide lo que falte. Sin cliente, sale a Consumidor Final.
export function ModalFacturacion({ venta: ventaInicial, cliente: clienteInicial, onCerrar, onResultado }: ModalFacturacionProps) {
  const [cliente, setCliente] = useState<ClienteResponse | null>(clienteInicial ?? null)
  const [venta, setVenta] = useState<VentaResponse | null>(ventaInicial ?? null)
  const [ventas, setVentas] = useState<VentaResponse[]>([])
  const [desde, setDesde] = useState(hoyIso().slice(0, 8) + '01')
  const [hasta, setHasta] = useState(hoyIso())
  const [buscando, setBuscando] = useState(false)
  const [facturando, setFacturando] = useState(false)
  const [abriendoPdf, setAbriendoPdf] = useState(false)
  const [emitida, setEmitida] = useState<FacturaFiscalResponse | null>(null)
  const [error, setError] = useState<string | null>(ventaInicial?.factura?.errorDetalle ?? null)

  const idVentaCliente = ventaInicial?.idCliente
  const elegirVenta = !ventaInicial
  const idClienteElegido = cliente?.idCliente

  useEffect(() => {
    if (idVentaCliente == null || clienteInicial) return
    getCliente(idVentaCliente)
      .then(setCliente)
      .catch(() => setError('No se pudieron cargar los datos del cliente de la venta'))
  }, [idVentaCliente, clienteInicial])

  useEffect(() => {
    if (!elegirVenta) return
    let cancelado = false
    setBuscando(true)
    getVentas(desde, hasta, 'SIN_FACTURAR')
      .then((resultado) => {
        if (cancelado) return
        const facturables = resultado.filter((v) => ventaFacturable(v, idClienteElegido))
        setVentas(facturables)
        // La elegida puede haber dejado de estar: otro período o la cuenta de otro cliente.
        setVenta((actual) => facturables.find((v) => v.idVenta === actual?.idVenta) ?? null)
      })
      .catch((err) => {
        if (!cancelado) setError(err instanceof ApiRequestError ? err.message : 'No se pudieron cargar las ventas')
      })
      .finally(() => {
        if (!cancelado) setBuscando(false)
      })
    return () => {
      cancelado = true
    }
  }, [elegirVenta, desde, hasta, idClienteElegido])

  const clienteBloqueado =
    venta?.idCliente != null && (venta.medioPago === 'CUENTA_CORRIENTE' || venta.montoSaldoAplicado != null)
  const faltaCondicion = cliente?.docTipo === 80 && cliente.condicionIva == null

  async function onEmitir() {
    if (!venta) return
    const receptor = cliente ? cliente.nombre : 'Consumidor Final'
    const pregunta = `¿Emitir Factura C a ${receptor} por $${venta.totalVenta.toFixed(2)}? Es un comprobante real ante ARCA y no se puede deshacer.`
    if (!window.confirm(pregunta)) return

    setError(null)
    setFacturando(true)
    try {
      const factura = await facturarVenta(
        venta.idVenta,
        cliente ? { idCliente: cliente.idCliente } : { clienteDocTipo: 99 },
      )
      onResultado(factura)
      if (factura.estado === 'EMITIDA') {
        setEmitida(factura)
      } else {
        setError(factura.errorDetalle ?? 'ARCA no autorizó el comprobante')
      }
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo facturar la venta')
    } finally {
      setFacturando(false)
    }
  }

  async function onVerPdf() {
    if (!emitida) return
    setAbriendoPdf(true)
    try {
      await descargarPdfFactura(emitida.idVenta)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo abrir el PDF')
    } finally {
      setAbriendoPdf(false)
    }
  }

  if (emitida) {
    return (
      <Modal title="Factura emitida" onClose={onCerrar}>
        <p>
          Factura C Nº {numeroComprobante(emitida.puntoVenta, emitida.numero)}
          {emitida.clienteNombre ? ` a ${emitida.clienteNombre}` : ' a Consumidor Final'}.
        </p>
        <p>CAE {emitida.cae}</p>
        {error && <p className="error">{error}</p>}
        <div className="comprobante-acciones">
          <button type="button" onClick={onVerPdf} disabled={abriendoPdf}>
            {abriendoPdf ? 'Abriendo...' : 'Ver PDF'}
          </button>
          <button type="button" className="boton-secundario" onClick={onCerrar}>
            Cerrar
          </button>
        </div>
      </Modal>
    )
  }

  return (
    <Modal title={venta && !elegirVenta ? `Facturar venta #${venta.idVenta}` : 'Facturar venta'} onClose={onCerrar} wide={elegirVenta}>
      <h4>Cliente</h4>
      <SelectorCliente value={cliente} onChange={setCliente} bloqueado={clienteBloqueado} />
      {!cliente && <p>Sin cliente, la factura sale a Consumidor Final.</p>}
      {faltaCondicion && (
        <p className="error">Este cliente tiene CUIT pero no su condición frente al IVA. Hay que completarla en Clientes.</p>
      )}

      {elegirVenta && (
        <>
          <h4>Venta</h4>
          <div className="agregar-producto">
            <label>
              Desde
              <input type="date" value={desde} onChange={(e) => setDesde(e.target.value)} />
            </label>
            <label>
              Hasta
              <input type="date" value={hasta} onChange={(e) => setHasta(e.target.value)} />
            </label>
          </div>
          <table>
            <thead>
              <tr>
                <th />
                <th>#</th>
                <th>Fecha</th>
                <th>Medio de pago</th>
                <th>Total</th>
              </tr>
            </thead>
            <tbody>
              {!buscando && ventas.length === 0 && (
                <tr>
                  <td colSpan={5}>No hay ventas sin facturar en este período.</td>
                </tr>
              )}
              {ventas.map((v) => (
                <tr key={v.idVenta}>
                  <td>
                    <input
                      type="radio"
                      name="venta-a-facturar"
                      aria-label={`Elegir venta ${v.idVenta}`}
                      checked={venta?.idVenta === v.idVenta}
                      onChange={() => setVenta(v)}
                    />
                  </td>
                  <td>{v.idVenta}</td>
                  <td>{formatoFechaHora(v.fecha)}</td>
                  <td>{v.medioPago}</td>
                  <td>{v.totalVenta.toFixed(2)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}

      {venta && (
        <p>
          <strong>Total a facturar: ${venta.totalVenta.toFixed(2)}</strong>
        </p>
      )}
      {error && <p className="error">{error}</p>}
      <button type="button" onClick={onEmitir} disabled={facturando || !venta || faltaCondicion}>
        {facturando && <span className="spinner" />}
        {facturando ? 'Facturando...' : venta?.factura?.estado === 'ERROR' ? 'Reintentar factura' : 'Emitir factura'}
      </button>
    </Modal>
  )
}
