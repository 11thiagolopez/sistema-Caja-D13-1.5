import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { getRecargos } from '../api/recargos'
import { calcularRecargo, ETIQUETA_MEDIO_PAGO } from '../utils/recargo'
import { buscarProductoPorCodigo, getProductos } from '../api/productos'
import { descargarVentaPdf, enviarComprobanteEmail, registrarVenta } from '../api/ventas'
import { getSaldoCliente } from '../api/clientes'
import { verPdfFactura } from '../api/facturas'
import { ApiRequestError } from '../api/client'
import { BotonesCaja, EstadoCaja } from '../components/AccionesCaja'
import { useAutorizaciones } from '../components/AutorizacionesPendientes'
import { EncabezadoPagina } from '../components/EncabezadoPagina'
import { BarcodeInput } from '../components/BarcodeInput'
import { BotonDescargarFactura } from '../components/BotonDescargarFactura'
import { BuscadorProductoCarrito } from '../components/BuscadorProductoCarrito'
import { ModalFacturacion } from '../components/ModalFacturacion'
import { SelectorCliente } from '../components/SelectorCliente'
import { useCarrito } from '../hooks/useCarrito'
import { verBlob } from '../utils/descargarBlob'
import { formatoPesos, numeroComprobante, textoSaldo } from '../utils/formato'
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

  // % que se lleva el posnet por medio. Si no cargan, se ve sin vista previa: el backend igual cobra el recargo.
  const [recargos, setRecargos] = useState<Partial<Record<MedioPago, number>>>({})

  useEffect(() => {
    getProductos()
      .then(setProductos)
      .catch(() => setError('No se pudieron cargar los productos'))
    getRecargos()
      .then((lista) => setRecargos(Object.fromEntries(lista.map((r) => [r.medioPago, r.porcentaje]))))
      .catch(() => setRecargos({}))
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

  // Vista previa del recargo: sobre lo que pasa por el posnet (sin descuento ni lo pagado con saldo).
  const esTarjeta = medioPago === 'TARJETA_CREDITO' || medioPago === 'TARJETA_DEBITO'
  const porcentajeRecargo = esTarjeta ? (recargos[medioPago] ?? null) : null
  const netoPorPosnet = Math.max(total - (Number(descuento) || 0) - (Number(montoSaldoAplicado) || 0), 0)
  const recargoPrevio = porcentajeRecargo != null ? calcularRecargo(netoPorPosnet, porcentajeRecargo) : 0

  const opcionesMedioPago: { valor: MedioPago; texto: string; detalle?: string }[] = [
    { valor: 'EFECTIVO', texto: 'Efectivo' },
    { valor: 'TRANSFERENCIA', texto: 'Transferencia' },
    {
      valor: 'TARJETA_DEBITO',
      texto: 'Débito',
      detalle: recargos.TARJETA_DEBITO != null ? `+${recargos.TARJETA_DEBITO}%` : undefined,
    },
    {
      valor: 'TARJETA_CREDITO',
      texto: 'Crédito',
      detalle: recargos.TARJETA_CREDITO != null ? `+${recargos.TARJETA_CREDITO}%` : undefined,
    },
    { valor: 'CUENTA_CORRIENTE', texto: 'Cuenta corriente' },
  ]
  const totalACobrar = Math.max(total - (Number(descuento) || 0) - (Number(montoSaldoAplicado) || 0), 0) + recargoPrevio
  const cantidadArticulos = carrito.reduce((acc, item) => acc + item.cantidad, 0)

  return (
    <div className="cobros">
      <EncabezadoPagina titulo="Cobros" etiqueta={<EstadoCaja />} acciones={<BotonesCaja />} />

      {resultado && (
        <section className={`tarjeta cobro-resultado ${resultado.estado === 'PENDIENTE_AUTORIZACION' ? 'cobro-resultado-pendiente' : ''}`} role="status">
          <div className="cobro-resultado-titulo">
            <strong>
              {resultado.estado === 'PENDIENTE_AUTORIZACION'
                ? `Venta #${resultado.idVenta} esperando autorización`
                : `Venta #${resultado.idVenta} cobrada`}
            </strong>
            <span>
              {resultado.estado === 'PENDIENTE_AUTORIZACION'
                ? 'Le llegó al administrador. Podés seguir cobrando; la respuesta aparece en "Mis peticiones", abajo a la derecha.'
                : `${formatoPesos(resultado.totalCobrado)} · ${ETIQUETA_MEDIO_PAGO[resultado.medioPago] ?? ''}`}
            </span>
          </div>
          {resultado.recargoTarjeta != null && resultado.recargoTarjeta > 0 && (
            <div className="cobro-posnet">
              <span>Cobrar con el posnet</span>
              <strong>{formatoPesos(resultado.totalCobrado)}</strong>
              <small>
                Incluye {formatoPesos(resultado.recargoTarjeta)} de recargo ({resultado.porcentajeRecargo}%). En caja
                queda registrada por {formatoPesos(resultado.totalVenta - (resultado.montoSaldoAplicado ?? 0))}.
              </small>
            </div>
          )}
          {resultado.montoSaldoAplicado != null && resultado.montoSaldoAplicado > 0 && (
            <p>
              Se pagaron {formatoPesos(resultado.montoSaldoAplicado)} con el saldo de cuenta corriente de{' '}
              {resultado.nombreCliente}.
            </p>
          )}
          {factura && (
            <p>
              Factura C Nº {numeroComprobante(factura.puntoVenta, factura.numero)}
              {factura.clienteNombre ? ` a ${factura.clienteNombre}` : ' a Consumidor Final'}. CAE {factura.cae}
            </p>
          )}
          <div className="cobro-resultado-acciones">
            {factura && (
              <>
                <button type="button" onClick={verFactura} disabled={abriendoFactura}>
                  {abriendoFactura ? 'Abriendo…' : 'Ver factura'}
                </button>
                <BotonDescargarFactura factura={factura} onError={setErrorFactura} />
              </>
            )}
            {resultado.estado === 'CONFIRMADA' && !factura && (
              <>
                <button type="button" onClick={() => setModalFacturaAbierto(true)}>
                  Facturar esta venta
                </button>
                <button type="button" className="btn-secundario" onClick={verComprobante} disabled={viendoComprobante}>
                  {viendoComprobante && <span className="spinner" aria-hidden="true" />}
                  {viendoComprobante ? 'Abriendo…' : 'Ver comprobante'}
                </button>
              </>
            )}
          </div>
          {errorFactura && <p className="error">{errorFactura}</p>}
          {errorComprobanteVer && <p className="error">{errorComprobanteVer}</p>}
          {resultado.estado === 'CONFIRMADA' && !factura && (
            <div className="cobro-email">
              <label>
                Enviar comprobante por email
                <input
                  type="email"
                  autoComplete="off"
                  spellCheck={false}
                  placeholder="cliente@ejemplo.com"
                  value={emailComprobante}
                  onChange={(e) => setEmailComprobante(e.target.value)}
                />
              </label>
              <button
                type="button"
                className="btn-secundario"
                onClick={onEnviarComprobante}
                disabled={enviandoComprobante || !emailComprobante}
              >
                {enviandoComprobante && <span className="spinner" aria-hidden="true" />}
                {enviandoComprobante
                  ? 'Enviando…'
                  : resultado.comprobanteEnviadoPorEmail
                    ? 'Reenviar por email'
                    : 'Enviar por email'}
              </button>
            </div>
          )}
          {errorComprobante && <p className="error">{errorComprobante}</p>}
          {resultado.comprobanteEnviadoPorEmail && resultado.clienteEmail && (
            <p className="alerta alerta-ok">Comprobante enviado a {resultado.clienteEmail}.</p>
          )}
        </section>
      )}

      <div className="cobros-grilla">
        <section className="tarjeta cobros-productos">
          <header className="tarjeta-encabezado">
            <h3>Productos</h3>
            {carrito.length > 0 && (
              <span className="texto-apagado">
                {cantidadArticulos} {cantidadArticulos === 1 ? 'artículo' : 'artículos'}
              </span>
            )}
          </header>
          <div className="cobros-carga">
            <label>
              Escanear código de barras
              <BarcodeInput onScan={onEscanear} />
            </label>
            {errorEscaneo && <p className="error">{errorEscaneo}</p>}
            <div className="cobros-buscar">
              <span className="cobros-buscar-titulo">O buscalo por nombre</span>
              <BuscadorProductoCarrito
                productos={productos}
                datalistId="productos-datalist"
                onAgregar={agregarProductoAlCarrito}
              />
            </div>
          </div>

          {carrito.length === 0 ? (
            <p className="cobros-vacio">Escaneá o buscá un producto para empezar la venta.</p>
          ) : (
            <div className="tabla-scroll">
              <table className="cobros-carrito">
                <thead>
                  <tr>
                    <th>Producto</th>
                    <th className="celda-numero">Cantidad</th>
                    <th className="celda-numero col-precio">Precio</th>
                    <th className="celda-numero">Subtotal</th>
                    <th>
                      <span className="solo-lector">Acciones</span>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {carrito.map((item, index) => (
                    <tr key={item.clientId}>
                      <td className="texto-fuerte">{item.descripcionProducto}</td>
                      <td className="celda-numero">{item.cantidad}</td>
                      <td className="celda-numero col-precio">{formatoPesos(item.precioUnitario)}</td>
                      <td className="celda-numero texto-fuerte">{formatoPesos(item.cantidad * item.precioUnitario)}</td>
                      <td className="celda-numero">
                        <button
                          type="button"
                          className="btn-peligro-borde btn-chico"
                          onClick={() => quitarDelCarrito(index)}
                          aria-label={`Quitar ${item.descripcionProducto}`}
                        >
                          Quitar
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>

        <form className="tarjeta cobros-panel" onSubmit={onSubmit}>
          <div className="cobros-total">
            <span>Total</span>
            <strong>{formatoPesos(total)}</strong>
          </div>

          <div className="cobros-bloque">
            <SelectorCliente value={cliente} onChange={setCliente} />
            {cliente && saldoDisponible != null && (
              <p className="cobros-saldo">
                Cuenta corriente:{' '}
                <span className={saldoDisponible < 0 ? 'saldo-deudor' : undefined}>{textoSaldo(saldoDisponible)}</span>
              </p>
            )}
            {cliente && saldoDisponible != null && saldoDisponible > 0 && medioPago !== 'CUENTA_CORRIENTE' && (
              <label>
                Pagar con saldo (opcional)
                <input
                  type="number"
                  inputMode="decimal"
                  min={0}
                  max={Math.min(saldoDisponible, total)}
                  step="0.01"
                  value={montoSaldoAplicado}
                  onChange={(e) => setMontoSaldoAplicado(e.target.value)}
                />
              </label>
            )}
            {montoSaldoAplicado && Number(montoSaldoAplicado) >= total && (
              <p className="alerta">Se paga el total con saldo: la forma de pago no aplica.</p>
            )}
          </div>

          <fieldset className="cobros-bloque cobros-medios">
            <legend>Forma de pago</legend>
            <div className="opciones-grandes">
              {opcionesMedioPago.map((op) => (
                <button
                  key={op.valor}
                  type="button"
                  className="opcion-grande"
                  aria-pressed={medioPago === op.valor}
                  onClick={() => onCambiarMedioPago(op.valor)}
                >
                  {op.texto}
                  {op.detalle && <small>{op.detalle}</small>}
                </button>
              ))}
            </div>
          </fieldset>

          {esTarjeta && netoPorPosnet > 0 && porcentajeRecargo != null && (
            <div className="cobro-posnet">
              <span>Cobrar con el posnet</span>
              <strong>{formatoPesos(netoPorPosnet + recargoPrevio)}</strong>
              <small>
                Precio {formatoPesos(netoPorPosnet)} + recargo {formatoPesos(recargoPrevio)}. El posnet se queda con el{' '}
                {porcentajeRecargo}% y quedan {formatoPesos(netoPorPosnet)}, que es lo que se registra en caja.
                {sesion?.rol === 'ADMIN' && (
                  <>
                    {' '}
                    <Link to="/configuracion/recargos">Cambiar porcentajes</Link>
                  </>
                )}
              </small>
            </div>
          )}
          {faltaClienteParaCuenta && (
            <p className="alerta alerta-aviso">Para vender en cuenta corriente, elegí o creá un cliente.</p>
          )}
          {medioPago === 'CUENTA_CORRIENTE' && cliente && (
            <p className="alerta">El total se carga a la cuenta corriente de {cliente.nombre}.</p>
          )}

          <details className="cobros-descuento" open={Boolean(descuento)}>
            <summary>Aplicar descuento</summary>
            <label>
              Descuento en pesos
              <input
                type="number"
                inputMode="decimal"
                min={0}
                step="0.01"
                value={descuento}
                onChange={(e) => setDescuento(e.target.value)}
              />
            </label>
            {descuento && Number(descuento) > 0 && (
              <label>
                Motivo del descuento
                <input value={motivoDescuento} onChange={(e) => setMotivoDescuento(e.target.value)} required />
              </label>
            )}
          </details>

          {error && (
            <p className="alerta alerta-error" role="alert">
              {error}
            </p>
          )}
          <button
            type="submit"
            className="cobros-confirmar"
            disabled={enviando || carrito.length === 0 || faltaClienteParaCuenta}
          >
            {enviando && <span className="spinner" aria-hidden="true" />}
            {enviando ? 'Registrando…' : carrito.length === 0 ? 'Registrar venta' : `Cobrar ${formatoPesos(totalACobrar)}`}
          </button>
        </form>
      </div>

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
