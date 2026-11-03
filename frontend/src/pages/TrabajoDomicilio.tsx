import { useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { getProductos } from '../api/productos'
import { getEmpleados } from '../api/empleados'
import { anularTrabajoDomicilio, descargarVentaPdf, getVenta, guardarTrabajoDomicilio } from '../api/ventas'
import { ApiRequestError } from '../api/client'
import { BuscadorProductoCarrito } from '../components/BuscadorProductoCarrito'
import { CalendarioTrabajos } from '../components/CalendarioTrabajos'
import { useCarrito } from '../hooks/useCarrito'
import { hoyIso } from '../utils/date'
import { verBlob } from '../utils/descargarBlob'
import { estadoVisibleTrabajo } from '../utils/estadoTrabajo'
import { formatoFechaHora } from '../utils/formato'
import type {
  DetalleVentaRequest,
  EmpleadoResponse,
  EstadoTrabajo,
  EstadoVenta,
  MedioPago,
  Producto,
  VentaResponse,
} from '../types/api'

interface ItemCarrito extends DetalleVentaRequest {
  descripcionProducto: string
}

// "Cobrado" no está a propósito: un trabajo queda cobrado solo con "Cerrar y cobrar".
const ESTADOS: { valor: EstadoTrabajo; etiqueta: string }[] = [
  { valor: 'AGENDADO', etiqueta: 'Agendado' },
  { valor: 'EN_CURSO', etiqueta: 'En curso' },
  { valor: 'COMPLETADO', etiqueta: 'Completado' },
]

// Para saber si hay cambios sin guardar antes de volver a la lista.
function firmaDe(campos: string[], items: Pick<ItemCarrito, 'descripcionProducto' | 'cantidad' | 'precioUnitario'>[]): string {
  return JSON.stringify([campos, items.map((i) => [i.descripcionProducto, i.cantidad, i.precioUnitario])])
}

export function TrabajoDomicilio() {
  const { sesion } = useAuth()
  const [searchParams] = useSearchParams()

  const [productos, setProductos] = useState<Producto[]>([])
  const [empleados, setEmpleados] = useState<EmpleadoResponse[]>([])

  const [vista, setVista] = useState<'lista' | 'trabajo'>('lista')
  const [fechaBuscar, setFechaBuscar] = useState(hoyIso())
  const [idVenta, setIdVenta] = useState<number | null>(null)
  const [estadoVenta, setEstadoVenta] = useState<EstadoVenta | null>(null)
  const [estadoTrabajoGuardado, setEstadoTrabajoGuardado] = useState<EstadoTrabajo | null>(null)
  const [fechaTrabajo, setFechaTrabajo] = useState<string | null>(null)
  const [firmaGuardada, setFirmaGuardada] = useState('')
  const [idBuscar, setIdBuscar] = useState('')
  const [cargando, setCargando] = useState(false)
  const [abriendoComprobante, setAbriendoComprobante] = useState(false)

  const [clienteNombre, setClienteNombre] = useState('')
  const [clienteTelefono, setClienteTelefono] = useState('')
  const [direccionTrabajo, setDireccionTrabajo] = useState('')
  const [descripcionTrabajo, setDescripcionTrabajo] = useState('')
  const [estadoTrabajo, setEstadoTrabajo] = useState<EstadoTrabajo>('AGENDADO')
  const [idEmpleadoTecnico, setIdEmpleadoTecnico] = useState('')
  const [fechaProgramada, setFechaProgramada] = useState('')
  // Vacío solo en trabajos cobrados antes de que se pidiera el medio de pago.
  const [medioPago, setMedioPago] = useState<MedioPago | ''>('EFECTIVO')
  const [comisionGuardada, setComisionGuardada] = useState<number | null>(null)
  const [anulando, setAnulando] = useState(false)
  const raiz = useRef<HTMLDivElement>(null)

  // En el celular la lista queda debajo del calendario: sin esto, el trabajo se abre por la mitad.
  useEffect(() => {
    raiz.current?.scrollIntoView()
  }, [vista])

  const { carrito, reemplazar: reemplazarCarrito, agregarOFusionar, quitar: quitarDelCarrito, vaciar: vaciarCarrito, total } =
    useCarrito<ItemCarrito>()
  const [manualDescripcion, setManualDescripcion] = useState('')
  const [manualPrecio, setManualPrecio] = useState('')

  const [error, setError] = useState<string | null>(null)
  const [guardando, setGuardando] = useState(false)
  const [mensaje, setMensaje] = useState<string | null>(null)

  useEffect(() => {
    getProductos()
      .then(setProductos)
      .catch(() => setError('No se pudieron cargar los productos'))
    getEmpleados()
      .then(setEmpleados)
      .catch(() => setError('No se pudieron cargar los empleados'))
  }, [])

  useEffect(() => {
    const idParam = searchParams.get('id')
    if (idParam) {
      cargarTrabajo(Number(idParam))
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  function precargarDesdeVenta(venta: VentaResponse) {
    // Un borrador viejo marcado "Cobrado" a mano no se cobró: se sigue editando como completado.
    const estado = venta.estadoTrabajo === 'COBRADO' ? 'COMPLETADO' : (venta.estadoTrabajo ?? 'AGENDADO')
    const tecnico = venta.idEmpleadoTecnico != null ? String(venta.idEmpleadoTecnico) : ''
    const items = venta.detalles.map((d) => ({
      idProducto: d.idProducto ?? undefined,
      descripcion: d.idProducto ? undefined : d.descripcionProducto,
      descripcionProducto: d.descripcionProducto,
      tipo: d.tipo,
      cantidad: d.cantidad,
      precioUnitario: d.precioUnitario,
    }))
    const fecha = venta.fechaProgramada ?? venta.fecha.slice(0, 10)
    const medio = venta.medioPago ?? (venta.estado === 'EN_PROGRESO' ? 'EFECTIVO' : '')
    setFechaProgramada(fecha)
    setMedioPago(medio)
    setComisionGuardada(venta.comisionTecnico)
    setFechaBuscar(fecha)
    setIdVenta(venta.idVenta)
    setEstadoVenta(venta.estado)
    setEstadoTrabajoGuardado(venta.estadoTrabajo)
    setFechaTrabajo(venta.fecha)
    setClienteNombre(venta.clienteNombre ?? '')
    setClienteTelefono(venta.clienteTelefono ?? '')
    setDireccionTrabajo(venta.direccionTrabajo ?? '')
    setDescripcionTrabajo(venta.descripcionTrabajo ?? '')
    setEstadoTrabajo(estado)
    setIdEmpleadoTecnico(tecnico)
    reemplazarCarrito(items)
    setFirmaGuardada(
      firmaDe(
        [venta.clienteNombre ?? '', venta.clienteTelefono ?? '', venta.direccionTrabajo ?? '', venta.descripcionTrabajo ?? '', estado, tecnico, fecha, medio],
        items,
      ),
    )
    setVista('trabajo')
  }

  async function cargarTrabajo(id: number) {
    setError(null)
    setMensaje(null)
    setCargando(true)
    try {
      const venta = await getVenta(id)
      if (venta.tipoVenta !== 'DOMICILIO') {
        setError(`La venta #${id} no es un trabajo a domicilio`)
        return
      }
      precargarDesdeVenta(venta)
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo cargar el trabajo')
    } finally {
      setCargando(false)
    }
  }

  function abrirDesdeLista(trabajo: VentaResponse) {
    setError(null)
    setMensaje(null)
    precargarDesdeVenta(trabajo)
  }

  function volverALista() {
    if (!soloLectura && hayCambios && !window.confirm('Hay cambios sin guardar y se van a perder. ¿Volver a la lista?')) {
      return
    }
    setError(null)
    setMensaje(null)
    setVista('lista')
  }

  async function verComprobante() {
    if (idVenta == null) return
    setError(null)
    setAbriendoComprobante(true)
    try {
      verBlob(await descargarVentaPdf(idVenta))
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo abrir el comprobante')
    } finally {
      setAbriendoComprobante(false)
    }
  }

  async function eliminar() {
    if (
      idVenta == null ||
      !window.confirm(`Se va a eliminar el trabajo #${idVenta}: sale de la agenda y de los reportes, y sus artículos vuelven al stock. ¿Eliminarlo?`)
    ) {
      return
    }
    setError(null)
    setMensaje(null)
    setAnulando(true)
    try {
      await anularTrabajoDomicilio(idVenta)
      setMensaje(`Trabajo #${idVenta} eliminado. Sus artículos volvieron al stock.`)
      setVista('lista')
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo eliminar el trabajo')
    } finally {
      setAnulando(false)
    }
  }

  function nuevoTrabajo() {
    setVista('trabajo')
    setIdVenta(null)
    setEstadoVenta(null)
    setEstadoTrabajoGuardado(null)
    setFechaTrabajo(null)
    setFechaProgramada(fechaBuscar)
    setMedioPago('EFECTIVO')
    setComisionGuardada(null)
    setFirmaGuardada(firmaDe(['', '', '', '', 'AGENDADO', '', fechaBuscar, 'EFECTIVO'], []))
    setClienteNombre('')
    setClienteTelefono('')
    setDireccionTrabajo('')
    setDescripcionTrabajo('')
    setEstadoTrabajo('AGENDADO')
    setIdEmpleadoTecnico('')
    vaciarCarrito()
    setMensaje(null)
    setError(null)
  }

  function agregarProductoAlCarrito(producto: Producto, cantidad: number): boolean {
    const precioVenta = producto.precioVenta
    if (precioVenta == null) {
      setError(`"${producto.descripcion}" no tiene precio de venta cargado, no se puede usar`)
      return false
    }
    setError(null)
    agregarOFusionar({
      idProducto: producto.idProducto,
      descripcionProducto: producto.descripcion,
      tipo: 'ARTICULO',
      cantidad,
      precioUnitario: precioVenta,
    })
    return true
  }

  function agregarManoDeObra() {
    if (!manualDescripcion.trim() || !manualPrecio) return
    agregarOFusionar({
      descripcion: manualDescripcion.trim(),
      descripcionProducto: manualDescripcion.trim(),
      tipo: 'SERVICIO',
      cantidad: 1,
      precioUnitario: Number(manualPrecio),
    })
    setManualDescripcion('')
    setManualPrecio('')
  }

  const manoDeObraTotal = carrito
    .filter((item) => item.tipo === 'SERVICIO')
    .reduce((acc, item) => acc + item.cantidad * item.precioUnitario, 0)
  const tecnico = empleados.find((e) => String(e.idEmpleado) === idEmpleadoTecnico)
  // Un trabajo cobrado se muestra con el % que se guardó al cobrarlo, aunque después haya cambiado.
  const porcentajeTecnico = idEmpleadoTecnico ? (comisionGuardada ?? tecnico?.comision ?? 0) : null
  const comision = Math.round(manoDeObraTotal * (porcentajeTecnico ?? 0)) / 100
  const gananciaArticulos = carrito
    .filter((item) => item.idProducto != null)
    .reduce((acc, item) => {
      const producto = productos.find((p) => p.idProducto === item.idProducto)
      const costo = producto?.precioCompra ?? 0
      return acc + (item.precioUnitario - costo) * item.cantidad
    }, 0)
  const gananciaNeta = gananciaArticulos + manoDeObraTotal - comision

  // Ya cobrado no se edita: guardarlo de nuevo lo sacaría de la caja en la que se cobró.
  const soloLectura = estadoVenta === 'CONFIRMADA' || estadoVenta === 'ANULADA'
  const hayCambios =
    firmaDe([clienteNombre, clienteTelefono, direccionTrabajo, descripcionTrabajo, estadoTrabajo, idEmpleadoTecnico, fechaProgramada, medioPago], carrito) !== firmaGuardada
  const estadoVisible = estadoVenta ? estadoVisibleTrabajo({ estado: estadoVenta, estadoTrabajo: estadoTrabajoGuardado }) : null

  async function guardar(cerrar: boolean) {
    if (!sesion) return
    if (!clienteNombre.trim()) {
      setError('El nombre del cliente es obligatorio')
      return
    }
    if (!fechaProgramada) {
      setError('La fecha del trabajo es obligatoria')
      return
    }
    if (!medioPago) return
    setError(null)
    setMensaje(null)
    setGuardando(true)
    try {
      const venta = await guardarTrabajoDomicilio({
        idVenta: idVenta ?? undefined,
        idEmpleado: sesion.idEmpleado,
        idEmpleadoTecnico: idEmpleadoTecnico ? Number(idEmpleadoTecnico) : undefined,
        clienteNombre,
        clienteTelefono: clienteTelefono || undefined,
        direccionTrabajo: direccionTrabajo || undefined,
        descripcionTrabajo: descripcionTrabajo || undefined,
        estadoTrabajo,
        fechaProgramada,
        medioPago,
        detalles: carrito.map(({ idProducto, descripcion, tipo, cantidad, precioUnitario }) => ({
          idProducto,
          descripcion,
          tipo,
          cantidad,
          precioUnitario,
        })),
        cerrar,
      })
      precargarDesdeVenta(venta)
      setMensaje(
        cerrar
          ? `Trabajo #${venta.idVenta} cerrado y cobrado.`
          : `Trabajo #${venta.idVenta} guardado como borrador (${venta.estadoTrabajo}).`,
      )
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo guardar el trabajo')
    } finally {
      setGuardando(false)
    }
  }

  if (vista === 'lista') {
    return (
      <div ref={raiz}>
        <h2>Trabajos a domicilio</h2>
        <section>
          <button type="button" onClick={nuevoTrabajo}>
            Nuevo trabajo
          </button>
        </section>

        {mensaje && <p className="resultado">{mensaje}</p>}

        <CalendarioTrabajos fecha={fechaBuscar} onCambiarFecha={setFechaBuscar} onAbrir={abrirDesdeLista} />

        <section>
          <h3>Buscar por número</h3>
          <input
            type="number"
            placeholder="N° de trabajo"
            value={idBuscar}
            onChange={(e) => setIdBuscar(e.target.value)}
          />
          <button
            type="button"
            onClick={() => idBuscar && cargarTrabajo(Number(idBuscar))}
            disabled={cargando || !idBuscar}
          >
            {cargando && <span className="spinner" />}
            {cargando ? 'Cargando...' : 'Buscar'}
          </button>
          {error && <p className="error">{error}</p>}
        </section>
      </div>
    )
  }

  return (
    <div ref={raiz}>
      <button type="button" onClick={volverALista}>
        ← Volver a la lista
      </button>

      <div className="trabajo-encabezado">
        <h2>{idVenta ? `Trabajo #${idVenta}` : 'Nuevo trabajo'}</h2>
        {estadoVisible && <span className={`trabajo-estado ${estadoVisible.clase}`}>{estadoVisible.etiqueta}</span>}
      </div>
      {fechaTrabajo && (
        <p className="trabajo-detalle">
          {estadoVenta === 'CONFIRMADA' ? 'Cobrado' : 'Cargado'} el {formatoFechaHora(fechaTrabajo)}
        </p>
      )}

      {estadoVenta === 'CONFIRMADA' && (
        <section>
          <p>Este trabajo ya se cobró, por eso no se puede editar.</p>
          <button type="button" onClick={verComprobante} disabled={abriendoComprobante}>
            {abriendoComprobante && <span className="spinner" />}
            {abriendoComprobante ? 'Abriendo...' : 'Ver comprobante'}
          </button>
        </section>
      )}

      {error && <p className="error">{error}</p>}

      <fieldset className="trabajo-campos" disabled={soloLectura}>
      <section>
        <h3>Cliente y trabajo</h3>
        <label>
          Fecha del trabajo
          <input type="date" value={fechaProgramada} onChange={(e) => setFechaProgramada(e.target.value)} />
        </label>
        <label>
          Nombre del cliente
          <input
            placeholder="Ej: Juan Pérez"
            value={clienteNombre}
            onChange={(e) => setClienteNombre(e.target.value)}
          />
        </label>
        <label>
          Teléfono
          <input
            placeholder="Ej: 11 2345-6789"
            value={clienteTelefono}
            onChange={(e) => setClienteTelefono(e.target.value)}
          />
        </label>
        <label>
          Dirección
          <input
            placeholder="Ej: Av. Libertador 1450, entre San Martín y Belgrano, timbre 2B"
            value={direccionTrabajo}
            onChange={(e) => setDireccionTrabajo(e.target.value)}
          />
        </label>
        <label>
          Descripción del trabajo
          <textarea
            placeholder="Ej: Cambio de cerradura puerta de entrada, cliente perdió las llaves"
            value={descripcionTrabajo}
            onChange={(e) => setDescripcionTrabajo(e.target.value)}
          />
        </label>
        {!soloLectura && (
          <label>
            Estado
            <select value={estadoTrabajo} onChange={(e) => setEstadoTrabajo(e.target.value as EstadoTrabajo)}>
              {ESTADOS.map((e) => (
                <option key={e.valor} value={e.valor}>
                  {e.etiqueta}
                </option>
              ))}
            </select>
          </label>
        )}
        <label>
          Técnico asignado
          <select value={idEmpleadoTecnico} onChange={(e) => setIdEmpleadoTecnico(e.target.value)}>
            <option value="">Sin asignar</option>
            {empleados
              .filter((e) => e.rol === 'TECNICO')
              .map((e) => (
                <option key={e.idEmpleado} value={e.idEmpleado}>
                  {e.nombre} ({e.comision ?? 0}% comisión)
                </option>
              ))}
          </select>
        </label>
      </section>

      {!soloLectura && (
        <>
          <section>
            <h3>Artículos utilizados</h3>
            <BuscadorProductoCarrito
              productos={productos}
              datalistId="productos-datalist-domicilio"
              onAgregar={agregarProductoAlCarrito}
            />
          </section>

          <section>
            <h3>Mano de obra / servicio</h3>
            <div className="agregar-producto">
              <input
                placeholder="Descripción (ej: Instalación de cerradura)"
                value={manualDescripcion}
                onChange={(e) => setManualDescripcion(e.target.value)}
              />
              <input
                type="number"
                min={0}
                step="0.01"
                placeholder="Precio"
                value={manualPrecio}
                onChange={(e) => setManualPrecio(e.target.value)}
              />
              <button type="button" onClick={agregarManoDeObra} disabled={!manualDescripcion.trim() || !manualPrecio}>
                Agregar mano de obra
              </button>
            </div>
          </section>
        </>
      )}

      <table>
        <thead>
          <tr>
            <th>Descripción</th>
            <th>Tipo</th>
            <th>Cantidad</th>
            <th>Precio unitario</th>
            <th>Subtotal</th>
            {!soloLectura && <th />}
          </tr>
        </thead>
        <tbody>
          {carrito.map((item, index) => (
            <tr key={item.clientId}>
              <td>{item.descripcionProducto}</td>
              <td>{item.tipo === 'SERVICIO' ? 'Mano de obra' : 'Artículo'}</td>
              <td>{item.cantidad}</td>
              <td>{item.precioUnitario.toFixed(2)}</td>
              <td>{(item.cantidad * item.precioUnitario).toFixed(2)}</td>
              {!soloLectura && (
                <td>
                  <button type="button" onClick={() => quitarDelCarrito(index)}>
                    Quitar
                  </button>
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>

      <section>
        <label>
          Medio de pago
          <select value={medioPago} onChange={(e) => setMedioPago(e.target.value as MedioPago)}>
            {!medioPago && <option value="">Sin especificar</option>}
            <option value="EFECTIVO">Efectivo</option>
            <option value="TRANSFERENCIA">Transferencia</option>
            <option value="TARJETA">Tarjeta</option>
          </select>
        </label>
      </section>

      <section>
        <h3>Resumen</h3>
        <ul>
          <li>Total: {total.toFixed(2)}</li>
          <li>Mano de obra: {manoDeObraTotal.toFixed(2)}</li>
          <li>
            Para el técnico ({porcentajeTecnico != null ? `${porcentajeTecnico}%` : 'sin asignar'} de la mano de obra):{' '}
            {comision.toFixed(2)}
          </li>
          <li>Para el negocio: {(total - comision).toFixed(2)}</li>
          <li>
            <strong>Ganancia del negocio, restando el costo de los artículos: {gananciaNeta.toFixed(2)}</strong>
          </li>
        </ul>
      </section>
      </fieldset>

      {!soloLectura && (
        <div className="comprobante-acciones">
          <button type="button" onClick={() => guardar(false)} disabled={guardando}>
            {guardando && <span className="spinner" />}
            Guardar borrador
          </button>
          <button type="button" onClick={() => guardar(true)} disabled={guardando}>
            {guardando && <span className="spinner" />}
            Cerrar y cobrar
          </button>
        </div>
      )}

      {idVenta && estadoVenta !== 'ANULADA' && (
        <div className="comprobante-acciones">
          <button type="button" className="boton-rechazar" onClick={eliminar} disabled={anulando || guardando}>
            {anulando && <span className="spinner" />}
            Eliminar trabajo
          </button>
        </div>
      )}

      {mensaje && <p className="resultado">{mensaje}</p>}
    </div>
  )
}
