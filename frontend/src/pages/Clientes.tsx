import { useCallback, useEffect, useState, type FormEvent } from 'react'
import {
  eliminarCliente,
  getCliente,
  getClientes,
  getCuentaCorrienteCliente,
  getVentasCliente,
  registrarPagoCliente,
} from '../api/clientes'
import { ApiRequestError } from '../api/client'
import { FormularioCliente } from '../components/FormularioCliente'
import { Modal } from '../components/Modal'
import { ModalFacturacion } from '../components/ModalFacturacion'
import { etiquetaDocumento, nombreCondicionIva } from '../utils/cuit'
import { formatoFechaHora, formatoPesos, numeroComprobante, textoSaldo } from '../utils/formato'
import type {
  ClienteResponse,
  CuentaCorrienteClienteResponse,
  MovimientoCuentaCorrienteClienteResponse,
  PagoClienteRequest,
  VentaResponse,
} from '../types/api'

function descripcionDe(m: MovimientoCuentaCorrienteClienteResponse): string {
  if (m.medioPago) return `${m.descripcion ?? 'Pago'} (${m.medioPago})`
  return m.descripcion ?? '—'
}

export function Clientes() {
  const [clientes, setClientes] = useState<ClienteResponse[]>([])
  const [busqueda, setBusqueda] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [cargando, setCargando] = useState(true)
  const [creando, setCreando] = useState(false)
  const [idAbierto, setIdAbierto] = useState<number | null>(null)

  const cargar = useCallback(() => {
    setError(null)
    return getClientes()
      .then(setClientes)
      .catch((err) => setError(err instanceof ApiRequestError ? err.message : 'No se pudieron cargar los clientes'))
  }, [])

  useEffect(() => {
    cargar().finally(() => setCargando(false))
  }, [cargar])

  const texto = busqueda.trim().toLowerCase()
  const visibles = clientes.filter(
    (c) => !texto || c.nombre.toLowerCase().includes(texto) || (c.docNro ?? '').includes(texto),
  )

  return (
    <div>
      <h2>Clientes</h2>
      <div className="agregar-producto">
        <label>
          Buscar
          <input
            placeholder="Nombre, razón social, CUIT o DNI"
            value={busqueda}
            onChange={(e) => setBusqueda(e.target.value)}
          />
        </label>
        <button type="button" onClick={() => setCreando(true)}>
          Nuevo cliente
        </button>
      </div>

      {cargando && <p>Cargando...</p>}
      {error && <p className="error">{error}</p>}
      {!cargando && !error && visibles.length === 0 && <p>No hay clientes para mostrar.</p>}

      <div className="clientes-grilla">
        {visibles.map((c) => (
          <button type="button" key={c.idCliente} className="cliente-tarjeta" onClick={() => setIdAbierto(c.idCliente)}>
            <strong>{c.nombre}</strong>
            <small>{etiquetaDocumento(c.docTipo, c.docNro)}</small>
            <small>{nombreCondicionIva(c.condicionIva)}</small>
            {c.saldo != null && c.saldo !== 0 && (
              <span className={c.saldo < 0 ? 'saldo-deudor' : 'saldo-a-favor'}>{textoSaldo(c.saldo)}</span>
            )}
          </button>
        ))}
      </div>

      {creando && (
        <Modal title="Nuevo cliente" onClose={() => setCreando(false)}>
          <FormularioCliente
            onGuardado={(nuevo) => {
              setCreando(false)
              cargar()
              setIdAbierto(nuevo.idCliente)
            }}
            onCancelar={() => setCreando(false)}
          />
        </Modal>
      )}

      {idAbierto != null && (
        <PanelCliente
          key={idAbierto}
          idCliente={idAbierto}
          onCerrar={() => setIdAbierto(null)}
          onCambio={cargar}
        />
      )}
    </div>
  )
}

interface PanelClienteProps {
  idCliente: number
  onCerrar: () => void
  onCambio: () => void
}

function PanelCliente({ idCliente, onCerrar, onCambio }: PanelClienteProps) {
  const [cliente, setCliente] = useState<ClienteResponse | null>(null)
  const [cuenta, setCuenta] = useState<CuentaCorrienteClienteResponse | null>(null)
  const [ventas, setVentas] = useState<VentaResponse[]>([])
  const [error, setError] = useState<string | null>(null)
  const [editando, setEditando] = useState(false)
  const [facturando, setFacturando] = useState(false)
  const [dandoDeBaja, setDandoDeBaja] = useState(false)

  const cargar = useCallback(async () => {
    try {
      const [datos, cuentaCorriente, ventasCliente] = await Promise.all([
        getCliente(idCliente),
        getCuentaCorrienteCliente(idCliente),
        getVentasCliente(idCliente),
      ])
      setCliente(datos)
      setCuenta(cuentaCorriente)
      setVentas(ventasCliente)
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudieron cargar los datos del cliente')
    }
  }, [idCliente])

  useEffect(() => {
    cargar()
  }, [cargar])

  function actualizar() {
    onCambio()
    cargar()
  }

  async function onDarDeBaja() {
    if (!cliente || !window.confirm(`¿Dar de baja a "${cliente.nombre}"?`)) return
    setError(null)
    setDandoDeBaja(true)
    try {
      await eliminarCliente(cliente.idCliente)
      onCambio()
      onCerrar()
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo dar de baja al cliente')
      setDandoDeBaja(false)
    }
  }

  return (
    <div className="panel-lateral-fondo" onClick={onCerrar}>
      <aside className="panel-lateral" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3>{cliente?.nombre ?? 'Cliente'}</h3>
          <button type="button" className="modal-cerrar" onClick={onCerrar} aria-label="Cerrar">
            ×
          </button>
        </div>

        {error && <p className="error">{error}</p>}
        {!cliente && !error && <p>Cargando...</p>}

        {cliente && editando && (
          <FormularioCliente
            cliente={cliente}
            onGuardado={() => {
              setEditando(false)
              actualizar()
            }}
            onCancelar={() => setEditando(false)}
          />
        )}

        {cliente && !editando && (
          <>
            <section>
              <p>{etiquetaDocumento(cliente.docTipo, cliente.docNro)}</p>
              <p>{nombreCondicionIva(cliente.condicionIva)}</p>
              {cliente.email && <p>{cliente.email}</p>}
              {cliente.telefono && <p>{cliente.telefono}</p>}
              {cliente.direccion && <p>{cliente.direccion}</p>}
              <div className="comprobante-acciones">
                <button type="button" onClick={() => setFacturando(true)}>
                  Facturar venta
                </button>
                <button type="button" className="boton-secundario" onClick={() => setEditando(true)}>
                  Editar
                </button>
                <button type="button" className="boton-secundario" onClick={onDarDeBaja} disabled={dandoDeBaja}>
                  {dandoDeBaja ? 'Dando de baja...' : 'Dar de baja'}
                </button>
              </div>
            </section>

            <section>
              <h4>Ventas y facturas</h4>
              <table>
                <thead>
                  <tr>
                    <th>#</th>
                    <th>Fecha</th>
                    <th>Total</th>
                    <th>Medio</th>
                    <th>Factura</th>
                  </tr>
                </thead>
                <tbody>
                  {ventas.length === 0 && (
                    <tr>
                      <td colSpan={5}>Sin ventas vinculadas.</td>
                    </tr>
                  )}
                  {ventas.map((v) => (
                    <tr key={v.idVenta}>
                      <td>{v.idVenta}</td>
                      <td>{formatoFechaHora(v.fecha)}</td>
                      <td>{v.totalVenta.toFixed(2)}</td>
                      <td>{v.medioPago}</td>
                      <td>
                        {v.estado !== 'CONFIRMADA'
                          ? v.estado
                          : v.estadoFactura === 'FACTURADA' && v.factura
                            ? numeroComprobante(v.factura.puntoVenta, v.factura.numero)
                            : 'Sin facturar'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </section>

            {cuenta && (
              <section>
                <h4>Cuenta corriente</h4>
                <p>
                  <strong className={cuenta.saldoActual < 0 ? 'saldo-deudor' : undefined}>
                    {textoSaldo(cuenta.saldoActual)}
                  </strong>
                </p>
                <RegistrarPago idCliente={cliente.idCliente} saldo={cuenta.saldoActual} onRegistrado={actualizar} />
                <table>
                  <thead>
                    <tr>
                      <th>Fecha</th>
                      <th>Movimiento</th>
                      <th>Monto</th>
                    </tr>
                  </thead>
                  <tbody>
                    {cuenta.movimientos.length === 0 && (
                      <tr>
                        <td colSpan={3}>Sin movimientos todavía.</td>
                      </tr>
                    )}
                    {cuenta.movimientos.map((m) => (
                      <tr key={m.idMovimiento}>
                        <td>{formatoFechaHora(m.fecha)}</td>
                        <td>{descripcionDe(m)}</td>
                        <td>
                          {m.tipo === 'CREDITO' ? '+' : '-'}
                          {formatoPesos(m.monto)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </section>
            )}
          </>
        )}

        {facturando && cliente && (
          <ModalFacturacion
            cliente={cliente}
            onCerrar={() => setFacturando(false)}
            onResultado={(factura) => {
              if (factura.estado === 'EMITIDA') actualizar()
            }}
          />
        )}
      </aside>
    </div>
  )
}

interface RegistrarPagoProps {
  idCliente: number
  saldo: number
  onRegistrado: () => void
}

function RegistrarPago({ idCliente, saldo, onRegistrado }: RegistrarPagoProps) {
  const [monto, setMonto] = useState('')
  const [medioPago, setMedioPago] = useState<PagoClienteRequest['medioPago']>('EFECTIVO')
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setEnviando(true)
    try {
      await registrarPagoCliente(idCliente, { monto: Number(monto), medioPago })
      setMonto('')
      onRegistrado()
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo registrar el pago')
    } finally {
      setEnviando(false)
    }
  }

  return (
    <form onSubmit={onSubmit}>
      <label>
        Monto del pago
        <input
          type="number"
          min={0.01}
          step="0.01"
          required
          value={monto}
          placeholder={saldo < 0 ? String(Math.abs(saldo)) : undefined}
          onChange={(e) => setMonto(e.target.value)}
        />
      </label>
      <label>
        Medio de pago
        <select value={medioPago} onChange={(e) => setMedioPago(e.target.value as PagoClienteRequest['medioPago'])}>
          <option value="EFECTIVO">Efectivo</option>
          <option value="TRANSFERENCIA">Transferencia</option>
          <option value="TARJETA">Tarjeta</option>
        </select>
      </label>
      <button type="submit" disabled={enviando || !monto}>
        {enviando && <span className="spinner" />}
        {enviando ? 'Registrando...' : 'Registrar pago'}
      </button>
      {error && <p className="error">{error}</p>}
    </form>
  )
}
