import { useEffect, useState, type FormEvent } from 'react'
import { useAuth } from '../auth/AuthContext'
import { buscarProductoPorCodigo, getProductos } from '../api/productos'
import { descargarVentaPdf, enviarComprobanteEmail, registrarVenta } from '../api/ventas'
import { getSaldoCliente } from '../api/clientes'
import { verPdfFactura } from '../api/facturas'
import { ApiRequestError } from '../api/client'
import { useAutorizaciones } from '../components/AutorizacionesPendientes'
import { BarcodeInput } from '../components/BarcodeInput'
import { BotonDescargarFactura } from '../components/BotonDescargarFactura'
import { BuscadorProductoCarrito } from '../components/BuscadorProductoCarrito'
import { ModalFacturacion } from '../components/ModalFacturacion'
import { SelectorCliente } from '../components/SelectorCliente'
import { useCarrito } from '../hooks/useCarrito'
import { verBlob } from '../utils/descargarBlob'
import { numeroComprobante, textoSaldo } from '../utils/formato'
import type {
  ClienteResponse,
  DetalleVentaRequest,
  FacturaFiscalResponse,
  MedioPago,
  Producto,
  VentaResponse,
} from '../types/api'

interface ItemCarrito extends DetalleVentaRequest {
  descripcionProducto: string
}

export function RegistrarVenta() {
  const { sesion } = useAuth()
  const { agregar: agregarAutorizacion } = useAutorizaciones()
  const [productos, setProductos] = useState<Producto[]>([])
  const { carrito, agregarOFusionar, quitar: quitarDelCarrito, vaciar: vaciarCarrito, total } =
    useCarrito<ItemCarrito>()
  const [medioPago, setMedioPago] = useState<MedioPago>('EFECTIVO')
  const [descuento, setDescuento] = useState('')
  const [motivoDescuento, setMotivoDescuento] = useState('')

  // Elegir un cliente es opcional, salvo para vender en cuenta corriente o pagar con su saldo.
  const [cliente, setCliente] = useState<ClienteResponse | null>(null)
  const [saldoDisponible, setSaldoDisponible] = useState<number | null>(null)
  const [montoSaldoAplicado, setMontoSaldoAplicado] = useState('')
  // Se factura después de cobrar, con "Facturar esta venta": el cliente elegido acá pasa al modal,
  // que es donde se confirma a quién sale. Si ARCA rechaza, la venta ya quedó cobrada.
  const [clienteDeResultado, setClienteDeResultado] = useState<ClienteResponse | null>(null)
  const [factura, setFactura] = useState<FacturaFiscalResponse | null>(null)
  const [errorFactura, setErrorFactura] = useState<string | null>(null)
  const [modalFacturaAbierto, setModalFacturaAbierto] = useState(false)
  const [abriendoFactura, setAbriendoFactura] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [resultado, setResultado] = useState<VentaResponse | null>(null)
  const [enviando, setEnviando] = useState(false)
  const [viendoComprobante, setViendoComprobante] = useState(false)
  const [errorComprobanteVer, setErrorComprobanteVer] = useState<string | null>(null)

  const [errorEscaneo, setErrorEscaneo] = useState<string | null>(null)

  const [emailComprobante, setEmailComprobante] = useState('')
  const [enviandoComprobante, setEnviandoComprobante] = useState(false)
  const [errorComprobante, setErrorComprobante] = useState<string | null>(null)

  useEffect(() => {
    getProductos()
      .then(setProductos)
      .catch(() => setError('No se pudieron cargar los productos'))
  }, [])

  useEffect(() => {
    if (!cliente) {
      setSaldoDisponible(null)
      return
    }
    getSaldoCliente(cliente.idCliente)
      .then(({ saldo }) => setSaldoDisponible(saldo))
      .catch(() => setSaldoDisponible(null))
  }, [cliente])

  function onCambiarMedioPago(medio: MedioPago) {
    setMedioPago(medio)
    // El cargo en cuenta corriente ya descuenta lo que tenga a favor.
    if (medio === 'CUENTA_CORRIENTE') setMontoSaldoAplicado('')
  }

  async function verFactura() {
    if (!factura) return
    setErrorFactura(null)
    setAbriendoFactura(true)
    try {
      await verPdfFactura(factura.idVenta)
    } catch (err) {
      setErrorFactura(err instanceof Error ? err.message : 'No se pudo abrir la factura')
    } finally {
      setAbriendoFactura(false)
    }
  }

  function agregarProductoAlCarrito(producto: Producto, cantidadAAgregar: number): boolean {
    const precioVenta = producto.precioVenta
    if (precioVenta == null) {
      setError(`"${producto.descripcion}" no tiene precio de venta cargado, no se puede vender`)
      return false
    }
    setError(null)
    agregarOFusionar({
      idProducto: producto.idProducto,
      descripcionProducto: producto.descripcion,
      tipo: 'ARTICULO',
      cantidad: cantidadAAgregar,
      precioUnitario: precioVenta,
    })
    return true
  }

  async function onEscanear(codigo: string) {
    setErrorEscaneo(null)
    try {
      const producto = await buscarProductoPorCodigo(codigo)
      if (!agregarProductoAlCarrito(producto, 1)) {
        setErrorEscaneo(`"${producto.descripcion}" no tiene precio de venta cargado, no se puede vender`)
      }
    } catch {
      setErrorEscaneo(`Producto no encontrado para el código ${codigo}`)
    }
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    if (!sesion || carrito.length === 0) return
    setError(null)
    setEnviando(true)
    try {
      const descuentoNumero = descuento ? Number(descuento) : undefined
      const montoSaldoNumero = montoSaldoAplicado ? Number(montoSaldoAplicado) : undefined
      const venta = await registrarVenta({
        idEmpleado: sesion.idEmpleado,
        medioPago,
        detalles: carrito.map(({ idProducto, tipo, cantidad, precioUnitario }) => ({
          idProducto,
          tipo,
          cantidad,
          precioUnitario,
        })),
        descuento: descuentoNumero,
        motivoDescuento: descuentoNumero ? motivoDescuento : undefined,
        idCliente: cliente?.idCliente,
        montoSaldoAplicado: montoSaldoNumero,
      })
      setResultado(venta)
      setClienteDeResultado(cliente)
      setFactura(null)
      setErrorFactura(null)
      vaciarCarrito()
      setDescuento('')
      setMotivoDescuento('')
      setEmailComprobante('')
      setErrorComprobante(null)
      setCliente(null)
      setMontoSaldoAplicado('')
      setMedioPago('EFECTIVO')
      if (venta.estado === 'PENDIENTE_AUTORIZACION') {
        agregarAutorizacion({ tipo: 'DESCUENTO', id: venta.idVenta, monto: venta.descuento, motivo: motivoDescuento })
      }
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo registrar la venta')
    } finally {
      setEnviando(false)
    }
  }

  // Mismo PDF que "Ver"/"Descargar" en Historial de ventas — un solo generador del lado del
  // backend (TicketHtmlBuilder), abierto acá en una pestaña nueva en vez de una vista propia en
  // HTML/CSS (ver utils/descargarBlob.ts, verBlob).
  async function verComprobante() {
    if (!resultado) return
    setErrorComprobanteVer(null)
    setViendoComprobante(true)
    try {
      const blob = await descargarVentaPdf(resultado.idVenta)
      verBlob(blob)
    } catch (err) {
      setErrorComprobanteVer(err instanceof ApiRequestError ? err.message : 'No se pudo abrir el comprobante')
    } finally {
      setViendoComprobante(false)
    }
  }

  async function onEnviarComprobante() {
    if (!resultado || !emailComprobante) return
    setErrorComprobante(null)
    setEnviandoComprobante(true)
    try {
      const actualizada = await enviarComprobanteEmail(resultado.idVenta, emailComprobante)
      setResultado(actualizada)
    } catch (err) {
      setErrorComprobante(err instanceof ApiRequestError ? err.message : 'No se pudo enviar el comprobante')
    } finally {
      setEnviandoComprobante(false)
    }
  }

  const faltaClienteParaCuenta = medioPago === 'CUENTA_CORRIENTE' && !cliente

  return (
    <div>
      <h2>Registrar venta</h2>

      <section>
        <h3>Escanear código de barras</h3>
        <BarcodeInput onScan={onEscanear} />
        {errorEscaneo && <p className="error">{errorEscaneo}</p>}
      </section>

      <BuscadorProductoCarrito
        productos={productos}
        datalistId="productos-datalist"
        onAgregar={agregarProductoAlCarrito}
      />

      <table>
        <thead>
          <tr>
            <th>Producto</th>
            <th>Cantidad</th>
            <th>Precio unitario</th>
            <th>Subtotal</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {carrito.map((item, index) => (
            <tr key={item.clientId}>
              <td>{item.descripcionProducto}</td>
              <td>{item.cantidad}</td>
              <td>{item.precioUnitario.toFixed(2)}</td>
              <td>{(item.cantidad * item.precioUnitario).toFixed(2)}</td>
              <td>
                <button type="button" onClick={() => quitarDelCarrito(index)}>
                  Quitar
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <p>
        <strong>Total: {total.toFixed(2)}</strong>
      </p>

      <form onSubmit={onSubmit}>
        <section>
          <h4>Cliente (opcional)</h4>
          <SelectorCliente value={cliente} onChange={setCliente} />
          {cliente && saldoDisponible != null && (
            <p>
              Cuenta corriente: <span className={saldoDisponible < 0 ? 'saldo-deudor' : undefined}>{textoSaldo(saldoDisponible)}</span>
              {saldoDisponible > 0 && medioPago !== 'CUENTA_CORRIENTE' && (
                <>
                  {' — '}
                  <label>
                    Pagar con saldo
                    <input
                      type="number"
                      min={0}
                      max={Math.min(saldoDisponible, total)}
                      step="0.01"
                      value={montoSaldoAplicado}
                      onChange={(e) => setMontoSaldoAplicado(e.target.value)}
                    />
                  </label>
                </>
              )}
            </p>
          )}
          {montoSaldoAplicado && Number(montoSaldoAplicado) >= total && (
            <p>Se paga el total con saldo — el medio de pago de abajo no aplica.</p>
          )}
        </section>
        <label>
          Medio de pago
          <select value={medioPago} onChange={(e) => onCambiarMedioPago(e.target.value as MedioPago)}>
            <option value="EFECTIVO">Efectivo</option>
            <option value="TRANSFERENCIA">Transferencia</option>
            <option value="TARJETA">Tarjeta</option>
            <option value="CUENTA_CORRIENTE">Cuenta corriente</option>
          </select>
        </label>
        {faltaClienteParaCuenta && <p className="error">Para vender en cuenta corriente, elegí o creá un cliente.</p>}
        {medioPago === 'CUENTA_CORRIENTE' && cliente && (
          <p>El total se carga a la cuenta corriente de {cliente.nombre}.</p>
        )}
        <label>
          Descuento (opcional)
          <input
            type="number"
            min={0}
            step="0.01"
            value={descuento}
            onChange={(e) => setDescuento(e.target.value)}
          />
        </label>
        {descuento && Number(descuento) > 0 && (
          <label>
            Motivo del descuento
            <input
              value={motivoDescuento}
              onChange={(e) => setMotivoDescuento(e.target.value)}
              required
            />
          </label>
        )}
        {error && <p className="error">{error}</p>}
        <button type="submit" disabled={enviando || carrito.length === 0 || faltaClienteParaCuenta}>
          {enviando && <span className="spinner" />}
          {enviando ? 'Registrando...' : 'Registrar venta'}
        </button>
      </form>

      {resultado && (
        <div className="resultado">
          <p>
            {resultado.estado === 'PENDIENTE_AUTORIZACION'
              ? `Venta #${resultado.idVenta} registrada, pendiente de autorización: le llegó al administrador. Podés seguir cobrando; la respuesta aparece en "Mis peticiones", abajo a la derecha.`
              : `Venta #${resultado.idVenta} confirmada.`}
          </p>
          {resultado.montoSaldoAplicado != null && resultado.montoSaldoAplicado > 0 && (
            <p>
              Se pagaron ${resultado.montoSaldoAplicado.toFixed(2)} con el saldo de cuenta corriente de{' '}
              {resultado.nombreCliente}.
            </p>
          )}
          {factura && (
            <div>
              <p>
                Factura C Nº {numeroComprobante(factura.puntoVenta, factura.numero)}
                {factura.clienteNombre ? ` a ${factura.clienteNombre}` : ' a Consumidor Final'}. CAE {factura.cae}
              </p>
              <button type="button" onClick={verFactura} disabled={abriendoFactura}>
                {abriendoFactura ? 'Abriendo...' : 'Ver factura'}
              </button>
              <BotonDescargarFactura factura={factura} onError={setErrorFactura} />
              {errorFactura && <p className="error">{errorFactura}</p>}
            </div>
          )}
          {resultado.estado === 'CONFIRMADA' && !factura && (
            <button type="button" onClick={() => setModalFacturaAbierto(true)}>
              Facturar esta venta
            </button>
          )}
          {resultado.estado === 'CONFIRMADA' && !factura && (
            <button type="button" onClick={verComprobante} disabled={viendoComprobante}>
              {viendoComprobante && <span className="spinner" />}
              {viendoComprobante ? 'Abriendo...' : 'Ver comprobante'}
            </button>
          )}
          {errorComprobanteVer && <p className="error">{errorComprobanteVer}</p>}
          {resultado.estado === 'CONFIRMADA' && !factura && (
            <div>
              <h4>Enviar comprobante por email</h4>
              <input
                type="email"
                placeholder="Email del cliente"
                value={emailComprobante}
                onChange={(e) => setEmailComprobante(e.target.value)}
              />
              <button
                type="button"
                onClick={onEnviarComprobante}
                disabled={enviandoComprobante || !emailComprobante}
              >
                {enviandoComprobante && <span className="spinner" />}
                {enviandoComprobante
                  ? 'Enviando...'
                  : resultado.comprobanteEnviadoPorEmail
                    ? 'Reenviar por email'
                    : 'Enviar por email'}
              </button>
              {errorComprobante && <p className="error">{errorComprobante}</p>}
              {resultado.comprobanteEnviadoPorEmail && resultado.clienteEmail && (
                <p className="resultado">Comprobante enviado a {resultado.clienteEmail}.</p>
              )}
            </div>
          )}
        </div>
      )}

      {modalFacturaAbierto && resultado && (
        <ModalFacturacion
          venta={resultado}
          cliente={clienteDeResultado}
          onCerrar={() => setModalFacturaAbierto(false)}
          onResultado={(intento) => {
            if (intento.estado === 'EMITIDA') {
              setFactura(intento)
              setErrorFactura(null)
            }
          }}
        />
      )}
    </div>
  )
}
