import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { getProductos } from '../api/productos'
import { getEmpleados } from '../api/empleados'
import { getVenta, guardarTrabajoDomicilio } from '../api/ventas'
import { ApiRequestError } from '../api/client'
import { BuscadorProductoCarrito } from '../components/BuscadorProductoCarrito'
import type {
  DetalleVentaRequest,
  EmpleadoResponse,
  EstadoTrabajo,
  Producto,
  VentaResponse,
} from '../types/api'

interface ItemCarrito extends DetalleVentaRequest {
  descripcionProducto: string
}

const ESTADOS: { valor: EstadoTrabajo; etiqueta: string }[] = [
  { valor: 'AGENDADO', etiqueta: 'Agendado' },
  { valor: 'EN_CURSO', etiqueta: 'En curso' },
  { valor: 'COMPLETADO', etiqueta: 'Completado' },
  { valor: 'COBRADO', etiqueta: 'Cobrado' },
]

export function TrabajoDomicilio() {
  const { sesion } = useAuth()
  const [searchParams] = useSearchParams()

  const [productos, setProductos] = useState<Producto[]>([])
  const [empleados, setEmpleados] = useState<EmpleadoResponse[]>([])

  const [idVenta, setIdVenta] = useState<number | null>(null)
  const [idBuscar, setIdBuscar] = useState('')
  const [cargando, setCargando] = useState(false)

  const [clienteNombre, setClienteNombre] = useState('')
  const [clienteTelefono, setClienteTelefono] = useState('')
  const [direccionTrabajo, setDireccionTrabajo] = useState('')
  const [descripcionTrabajo, setDescripcionTrabajo] = useState('')
  const [estadoTrabajo, setEstadoTrabajo] = useState<EstadoTrabajo>('AGENDADO')
  const [idEmpleadoTecnico, setIdEmpleadoTecnico] = useState('')

  const [carrito, setCarrito] = useState<ItemCarrito[]>([])
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
    setIdVenta(venta.idVenta)
    setClienteNombre(venta.clienteNombre ?? '')
    setClienteTelefono(venta.clienteTelefono ?? '')
    setDireccionTrabajo(venta.direccionTrabajo ?? '')
    setDescripcionTrabajo(venta.descripcionTrabajo ?? '')
    setEstadoTrabajo(venta.estadoTrabajo ?? 'AGENDADO')
    setIdEmpleadoTecnico(venta.idEmpleadoTecnico != null ? String(venta.idEmpleadoTecnico) : '')
    setCarrito(
      venta.detalles.map((d) => ({
        idProducto: d.idProducto ?? undefined,
        descripcion: d.idProducto ? undefined : d.descripcionProducto,
        descripcionProducto: d.descripcionProducto,
        tipo: d.tipo,
        cantidad: d.cantidad,
        precioUnitario: d.precioUnitario,
      })),
    )
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

  function nuevoTrabajo() {
    setIdVenta(null)
    setClienteNombre('')
    setClienteTelefono('')
    setDireccionTrabajo('')
    setDescripcionTrabajo('')
    setEstadoTrabajo('AGENDADO')
    setIdEmpleadoTecnico('')
    setCarrito([])
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
    setCarrito((actual) => {
      const indiceExistente = actual.findIndex((item) => item.idProducto === producto.idProducto)
      if (indiceExistente >= 0) {
        const copia = [...actual]
        copia[indiceExistente] = {
          ...copia[indiceExistente],
          cantidad: copia[indiceExistente].cantidad + cantidad,
        }
        return copia
      }
      return [
        ...actual,
        {
          idProducto: producto.idProducto,
          descripcionProducto: producto.descripcion,
          tipo: 'ARTICULO',
          cantidad,
          precioUnitario: precioVenta,
        },
      ]
    })
    return true
  }

  function agregarManoDeObra() {
    if (!manualDescripcion.trim() || !manualPrecio) return
    setCarrito((actual) => [
      ...actual,
      {
        descripcion: manualDescripcion.trim(),
        descripcionProducto: manualDescripcion.trim(),
        tipo: 'SERVICIO',
        cantidad: 1,
        precioUnitario: Number(manualPrecio),
      },
    ])
    setManualDescripcion('')
    setManualPrecio('')
  }

  function quitarDelCarrito(index: number) {
    setCarrito((actual) => actual.filter((_, i) => i !== index))
  }

  const total = carrito.reduce((acc, item) => acc + item.cantidad * item.precioUnitario, 0)
  const manoDeObraTotal = carrito
    .filter((item) => item.tipo === 'SERVICIO')
    .reduce((acc, item) => acc + item.cantidad * item.precioUnitario, 0)
  const tecnico = empleados.find((e) => String(e.idEmpleado) === idEmpleadoTecnico)
  const comision = tecnico ? (manoDeObraTotal * (tecnico.comision ?? 0)) / 100 : 0
  const gananciaArticulos = carrito
    .filter((item) => item.idProducto != null)
    .reduce((acc, item) => {
      const producto = productos.find((p) => p.idProducto === item.idProducto)
      const costo = producto?.precioCompra ?? 0
      return acc + (item.precioUnitario - costo) * item.cantidad
    }, 0)
  const gananciaNeta = gananciaArticulos + manoDeObraTotal - comision

  async function guardar(cerrar: boolean) {
    if (!sesion) return
    if (!clienteNombre.trim()) {
      setError('El nombre del cliente es obligatorio')
      return
    }
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

  return (
    <div>
      <h2>Trabajo a domicilio</h2>

      <section>
        <h3>Cargar trabajo existente</h3>
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
        {idVenta && (
          <button type="button" onClick={nuevoTrabajo}>
            Empezar uno nuevo
          </button>
        )}
      </section>

      {idVenta && (
        <p>
          <strong>Editando trabajo #{idVenta}</strong>
        </p>
      )}

      {error && <p className="error">{error}</p>}

      <section>
        <h3>Cliente y trabajo</h3>
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

      <table>
        <thead>
          <tr>
            <th>Descripción</th>
            <th>Tipo</th>
            <th>Cantidad</th>
            <th>Precio unitario</th>
            <th>Subtotal</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {carrito.map((item, index) => (
            <tr key={index}>
              <td>{item.descripcionProducto}</td>
              <td>{item.tipo === 'SERVICIO' ? 'Mano de obra' : 'Artículo'}</td>
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

      <section>
        <h3>Resumen</h3>
        <ul>
          <li>Total: {total.toFixed(2)}</li>
          <li>
            Comisión del técnico ({tecnico ? `${tecnico.comision ?? 0}%` : 'sin asignar'} sobre mano de obra):{' '}
            {comision.toFixed(2)}
          </li>
          <li>
            <strong>Ganancia neta: {gananciaNeta.toFixed(2)}</strong>
          </li>
        </ul>
      </section>

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

      {mensaje && <p className="resultado">{mensaje}</p>}
    </div>
  )
}
