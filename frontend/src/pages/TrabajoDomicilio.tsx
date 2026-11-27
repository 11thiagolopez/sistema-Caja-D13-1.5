import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { getProductos } from '../api/productos'
import { getEmpleados } from '../api/empleados'
import {
  agregarNotaTrabajoDomicilio,
  anularTrabajoDomicilio,
  cobrarTrabajoTerminado,
  descargarOrdenTrabajo,
  descargarVentaPdf,
  getAgendaTrabajos,
  getNotasTrabajoDomicilio,
  getVenta,
  guardarTrabajoDomicilio,
  reabrirTrabajo,
} from '../api/ventas'
import { ApiRequestError } from '../api/client'
import { BuscadorProductoCarrito } from '../components/BuscadorProductoCarrito'
import { AgendaSemanal } from '../components/AgendaSemanal'
import { HistorialCliente } from '../components/HistorialCliente'
import { trabajosSuperpuestos } from '../utils/superposicion'
import {
  ES_CELULAR,
  linkWhatsapp,
  mensajeConfirmacion,
  numeroWhatsapp,
  PESTANIA_WHATSAPP,
  WHATSAPP_DEL_LOCAL,
} from '../utils/whatsapp'
import { CalendarioTrabajos } from '../components/CalendarioTrabajos'
import { AdjuntosTrabajo } from '../components/AdjuntosTrabajo'
import { EncabezadoPagina } from '../components/EncabezadoPagina'
import { getFotosTrabajo } from '../api/fotosTrabajo'
import { getArchivosTrabajo } from '../api/archivosTrabajo'
import { useCarrito } from '../hooks/useCarrito'
import { hoyIso } from '../utils/date'
import { verBlob } from '../utils/descargarBlob'
import { estadoVisibleTrabajo } from '../utils/estadoTrabajo'
import { formatoFechaHora, formatoPesos } from '../utils/formato'
import type {
  DetalleVentaRequest,
  EmpleadoResponse,
  EstadoTrabajo,
  EstadoVenta,
  FotoTrabajoResponse,
  ArchivoTrabajoResponse,
  MedioPago,
  NotaTrabajoResponse,
  Producto,
  VentaResponse,
} from '../types/api'

interface ItemCarrito extends DetalleVentaRequest {
  descripcionProducto: string
}

// "Cerrado y cobrado"/"Cancelado" no están a propósito: eso lo fija el backend con "Cerrar y
// cobrar" y "Eliminar trabajo", no se elige a mano desde este selector.
const ESTADOS: { valor: EstadoTrabajo; etiqueta: string }[] = [
  { valor: 'AGENDADO', etiqueta: 'Agendado' },
  { valor: 'EN_CAMINO', etiqueta: 'En camino' },
  { valor: 'EN_CURSO', etiqueta: 'En curso' },
  { valor: 'TERMINADO_TECNICO', etiqueta: 'El técnico avisó que terminó' },
  // Bloquea el trabajo: después solo se cobra o se reabre.
  { valor: 'TERMINADO', etiqueta: 'Terminado (bloquea el trabajo)' },
]

// Para saber si hay cambios sin guardar antes de volver a la lista.
function firmaDe(campos: string[], items: Pick<ItemCarrito, 'descripcionProducto' | 'cantidad' | 'precioUnitario'>[]): string {
  return JSON.stringify([campos, items.map((i) => [i.descripcionProducto, i.cantidad, i.precioUnitario])])
}

// Semana o mes: se recuerda en este navegador (es solo una preferencia de vista).
const CLAVE_VISTA_AGENDA = 'd13.vistaAgendaTrabajos'

function leerVistaAgenda(): 'semana' | 'mes' {
  try {
    return localStorage.getItem(CLAVE_VISTA_AGENDA) === 'mes' ? 'mes' : 'semana'
  } catch {
    return 'semana'
  }
}

export function TrabajoDomicilio() {
  const { sesion } = useAuth()
  const [searchParams] = useSearchParams()

  const [productos, setProductos] = useState<Producto[]>([])
  const [empleados, setEmpleados] = useState<EmpleadoResponse[]>([])

  const [vista, setVista] = useState<'lista' | 'trabajo'>('lista')
  const [fechaBuscar, setFechaBuscar] = useState(hoyIso())
  const [vistaAgenda, setVistaAgenda] = useState<'semana' | 'mes'>(leerVistaAgenda)
  const [idVenta, setIdVenta] = useState<number | null>(null)
  const [estadoVenta, setEstadoVenta] = useState<EstadoVenta | null>(null)
  const [estadoTrabajoGuardado, setEstadoTrabajoGuardado] = useState<EstadoTrabajo | null>(null)
  const [fechaTrabajo, setFechaTrabajo] = useState<string | null>(null)
  const [firmaGuardada, setFirmaGuardada] = useState('')
  const [idBuscar, setIdBuscar] = useState('')
  const [cargando, setCargando] = useState(false)
  const [abriendoComprobante, setAbriendoComprobante] = useState(false)
  const [abriendoOrden, setAbriendoOrden] = useState(false)

  const [clienteNombre, setClienteNombre] = useState('')
  const [clienteTelefono, setClienteTelefono] = useState('')
  const [direccionTrabajo, setDireccionTrabajo] = useState('')
  const [descripcionTrabajo, setDescripcionTrabajo] = useState('')
  const [estadoTrabajo, setEstadoTrabajo] = useState<EstadoTrabajo>('AGENDADO')
  const [idEmpleadoTecnico, setIdEmpleadoTecnico] = useState('')
  const [fechaProgramada, setFechaProgramada] = useState('')
  const [horaDesde, setHoraDesde] = useState('')
  const [horaHasta, setHoraHasta] = useState('')
  const [observacionesTecnico, setObservacionesTecnico] = useState('')
  const [notas, setNotas] = useState<NotaTrabajoResponse[]>([])
  const [fotos, setFotos] = useState<FotoTrabajoResponse[]>([])
  const [archivos, setArchivos] = useState<ArchivoTrabajoResponse[]>([])
  const [notaTexto, setNotaTexto] = useState('')
  const [agregandoNota, setAgregandoNota] = useState(false)
  // Vacío solo en trabajos cobrados antes de que se pidiera el medio de pago.
  const [medioPago, setMedioPago] = useState<MedioPago | ''>('EFECTIVO')
  const [comisionGuardada, setComisionGuardada] = useState<number | null>(null)
  const [anulando, setAnulando] = useState(false)
  const raiz = useRef<HTMLDivElement>(null)

  // En el celular la lista queda debajo del calendario: sin esto, el trabajo se abre por la mitad.
  useEffect(() => {
    raiz.current?.scrollIntoView()
  }, [vista])

  const { carrito, reemplazar: reemplazarCarrito, agregarOFusionar, quitar: quitarDelCarrito, cambiarPrecio, vaciar: vaciarCarrito, total } =
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
    // Un borrador viejo marcado "Cerrado y cobrado" a mano no se cobró: se sigue editando como terminado.
    const estado = venta.estadoTrabajo === 'CERRADO_Y_COBRADO' ? 'TERMINADO' : (venta.estadoTrabajo ?? 'AGENDADO')
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
    const desde = venta.horaDesde?.slice(0, 5) ?? ''
    const hasta = venta.horaHasta?.slice(0, 5) ?? ''
    setHoraDesde(desde)
    setHoraHasta(hasta)
    setObservacionesTecnico(venta.observacionesTecnico ?? '')
    reemplazarCarrito(items)
    setFirmaGuardada(
      firmaDe(
        [
          venta.clienteNombre ?? '',
          venta.clienteTelefono ?? '',
          venta.direccionTrabajo ?? '',
          venta.descripcionTrabajo ?? '',
          estado,
          tecnico,
          fecha,
          medio,
          desde,
          hasta,
          venta.observacionesTecnico ?? '',
        ],
        items,
      ),
    )
    setNotas([])
    setFotos([])
    setArchivos([])
    if (venta.idVenta != null) {
      getNotasTrabajoDomicilio(venta.idVenta).then(setNotas).catch(() => {})
      getFotosTrabajo(venta.idVenta).then(setFotos).catch(() => {})
      getArchivosTrabajo(venta.idVenta).then(setArchivos).catch(() => {})
    }
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

  // La orden sale con lo último guardado en el backend, así que con cambios pendientes se frena
  // para no imprimir una orden distinta de lo que se ve en pantalla.
  async function agregarNota(e: FormEvent) {
    e.preventDefault()
    if (idVenta == null || !notaTexto.trim()) return
    setError(null)
    setAgregandoNota(true)
    try {
      const nota = await agregarNotaTrabajoDomicilio(idVenta, notaTexto.trim())
      setNotas((actuales) => [...actuales, nota])
      setNotaTexto('')
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo agregar la nota')
    } finally {
      setAgregandoNota(false)
    }
  }

  async function cobrarTerminado() {
    if (idVenta == null || !medioPago) return
    setError(null)
    setMensaje(null)
    setGuardando(true)
    try {
      const venta = await cobrarTrabajoTerminado(idVenta, medioPago)
      precargarDesdeVenta(venta)
      setMensaje(`Trabajo #${venta.idVenta} cerrado y cobrado.`)
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo cobrar el trabajo')
    } finally {
      setGuardando(false)
    }
  }

  async function reabrir() {
    if (idVenta == null) return
    setError(null)
    setMensaje(null)
    setGuardando(true)
    try {
      const venta = await reabrirTrabajo(idVenta)
      precargarDesdeVenta(venta)
      setMensaje(`Trabajo #${venta.idVenta} reabierto: ya se puede modificar de nuevo.`)
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo reabrir el trabajo')
    } finally {
      setGuardando(false)
    }
  }

  function elegirVistaAgenda(vista: 'semana' | 'mes') {
    setVistaAgenda(vista)
    try {
      localStorage.setItem(CLAVE_VISTA_AGENDA, vista)
    } catch {
      // Sin localStorage la vista cambia igual, solo no se recuerda al volver.
    }
  }

  async function imprimirOrden() {
    if (idVenta == null) return
    setError(null)
    if (!soloLectura && hayCambios) {
      setError('Guardá los cambios antes de imprimir la orden: sale con lo último guardado.')
      return
    }
    setAbriendoOrden(true)
    try {
      verBlob(await descargarOrdenTrabajo(idVenta))
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo abrir la orden de trabajo')
    } finally {
      setAbriendoOrden(false)
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
    setFirmaGuardada(firmaDe(['', '', '', '', 'AGENDADO', '', fechaBuscar, 'EFECTIVO', '', '', ''], []))
    setClienteNombre('')
    setClienteTelefono('')
    setDireccionTrabajo('')
    setDescripcionTrabajo('')
    setEstadoTrabajo('AGENDADO')
    setIdEmpleadoTecnico('')
    setHoraDesde('')
    setHoraHasta('')
    setObservacionesTecnico('')
    setNotas([])
    setFotos([])
    setArchivos([])
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
  // Terminado por el jefe y sin cobrar: bloqueado, solo queda "Cerrar y cobrar" o "Reabrir".
  const terminado = estadoVenta === 'EN_PROGRESO' && estadoTrabajoGuardado === 'TERMINADO'
  const soloLectura = estadoVenta === 'CONFIRMADA' || estadoVenta === 'ANULADA' || terminado
  const numeroCliente = clienteTelefono ? numeroWhatsapp(clienteTelefono) : null
  const hayCambios =
    firmaDe(
      [
        clienteNombre,
        clienteTelefono,
        direccionTrabajo,
        descripcionTrabajo,
        estadoTrabajo,
        idEmpleadoTecnico,
        fechaProgramada,
        medioPago,
        horaDesde,
        horaHasta,
        observacionesTecnico,
      ],
      carrito,
    ) !== firmaGuardada
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
    const sinPrecio = carrito.find((item) => !(item.precioUnitario > 0))
    if (sinPrecio) {
      setError(`Poné un precio mayor a cero para "${sinPrecio.descripcionProducto}"`)
      return
    }
    if (!medioPago) return
    if (
      !cerrar &&
      estadoTrabajo === 'TERMINADO' &&
      estadoTrabajoGuardado !== 'TERMINADO' &&
      !window.confirm(
        'Al marcarlo Terminado el trabajo queda bloqueado: no se pueden cambiar datos, artículos ni precios, y el técnico ya no puede actualizarlo. Después solo se puede cobrar o reabrir. ¿Seguir?',
      )
    ) {
      return
    }
    // Aviso (no bloqueo) si el técnico ya tiene otro trabajo abierto que se pisa en ese horario.
    if (!cerrar && idEmpleadoTecnico && horaDesde) {
      try {
        const delDia = await getAgendaTrabajos(fechaProgramada, fechaProgramada)
        const pisados = trabajosSuperpuestos(delDia, {
          idVenta,
          idEmpleadoTecnico: Number(idEmpleadoTecnico),
          fechaProgramada,
          horaDesde,
          horaHasta: horaHasta || null,
        })
        const detalle = pisados
          .map((t) => `#${t.idVenta} ${t.horaDesde?.slice(0, 5)}${t.horaHasta ? `–${t.horaHasta.slice(0, 5)}` : ''} en ${t.direccionTrabajo ?? 'sin dirección'}`)
          .join('\n')
        if (
          pisados.length > 0 &&
          !window.confirm(`Ojo: ${pisados[0].nombreTecnico ?? 'el técnico'} ya tiene trabajos que se superponen con este horario:\n\n${detalle}\n\n¿Guardar igual?`)
        ) {
          return
        }
      } catch {
        // Si no se pudo revisar la agenda, se guarda igual: el chequeo es solo una ayuda.
      }
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
        fechaProgramada,
        horaDesde: horaDesde || undefined,
        horaHasta: horaHasta || undefined,
        observacionesTecnico: observacionesTecnico || undefined,
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
        <EncabezadoPagina
          titulo="Trabajos a domicilio"
          acciones={
            <>
              <form
                className="trabajo-buscar-numero"
                onSubmit={(e) => {
                  e.preventDefault()
                  if (idBuscar) void cargarTrabajo(Number(idBuscar))
                }}
              >
                <input
                  type="number"
                  inputMode="numeric"
                  aria-label="Número de trabajo"
                  placeholder="N° de trabajo"
                  value={idBuscar}
                  onChange={(e) => setIdBuscar(e.target.value)}
                />
                <button type="submit" className="btn-secundario" disabled={cargando || !idBuscar}>
                  {cargando && <span className="spinner" aria-hidden="true" />}
                  {cargando ? 'Buscando…' : 'Buscar'}
                </button>
              </form>
              <button type="button" onClick={nuevoTrabajo}>
                + Nuevo trabajo
              </button>
            </>
          }
        />

        {mensaje && <p className="alerta alerta-ok">{mensaje}</p>}
        {error && <p className="alerta alerta-error">{error}</p>}

        <div className="segmentado trabajo-vista" role="group" aria-label="Vista de la agenda">
          <button type="button" aria-pressed={vistaAgenda === 'semana'} onClick={() => elegirVistaAgenda('semana')}>
            Semana
          </button>
          <button type="button" aria-pressed={vistaAgenda === 'mes'} onClick={() => elegirVistaAgenda('mes')}>
            Mes
          </button>
        </div>

        {vistaAgenda === 'semana' ? (
          <AgendaSemanal fecha={fechaBuscar} onCambiarFecha={setFechaBuscar} onAbrir={abrirDesdeLista} />
        ) : (
          <CalendarioTrabajos fecha={fechaBuscar} onCambiarFecha={setFechaBuscar} onAbrir={abrirDesdeLista} />
        )}
      </div>
    )
  }

  return (
    <div ref={raiz} className="trabajo">
      <button type="button" className="enlace-volver boton-volver" onClick={volverALista}>
        ← Volver a la agenda
      </button>

      <EncabezadoPagina
        titulo={idVenta ? `Trabajo #${idVenta}` : 'Nuevo trabajo'}
        etiqueta={estadoVisible && <span className={`trabajo-estado ${estadoVisible.clase}`}>{estadoVisible.etiqueta}</span>}
        subtitulo={
          fechaTrabajo
            ? `${estadoVenta === 'CONFIRMADA' ? 'Cobrado' : 'Cargado'} el ${formatoFechaHora(fechaTrabajo)}`
            : undefined
        }
        acciones={
          idVenta != null &&
          estadoVenta !== 'ANULADA' && (
            <>
              {!terminado && (
                <button type="button" className="btn-peligro-borde" onClick={eliminar} disabled={anulando || guardando}>
                  {anulando && <span className="spinner" aria-hidden="true" />}
                  Eliminar trabajo
                </button>
              )}
              <button type="button" className="btn-secundario" onClick={imprimirOrden} disabled={abriendoOrden}>
                {abriendoOrden && <span className="spinner" aria-hidden="true" />}
                {abriendoOrden ? 'Abriendo…' : 'Imprimir orden'}
              </button>
            </>
          )
        }
      />

      {terminado && (
        <section className="trabajo-terminado alerta alerta-aviso">
          <p>
            <strong>Trabajo terminado.</strong> Ya no se pueden cambiar datos, artículos, precios, notas ni fotos. Cobralo
            con los ítems tal cual están, o reabrilo si hay que corregir algo.
          </p>
          <div className="comprobante-acciones">
            <label>
              Forma de pago
              <select value={medioPago} onChange={(e) => setMedioPago(e.target.value as MedioPago)}>
                {!medioPago && <option value="">Elegí la forma de pago</option>}
                <option value="EFECTIVO">Efectivo</option>
                <option value="TRANSFERENCIA">Transferencia</option>
                <option value="TARJETA">Tarjeta</option>
              </select>
            </label>
            <button type="button" onClick={cobrarTerminado} disabled={guardando || !medioPago}>
              {guardando && <span className="spinner" aria-hidden="true" />}
              Cerrar y cobrar
            </button>
            <button type="button" className="btn-secundario" onClick={reabrir} disabled={guardando}>
              Reabrir
            </button>
          </div>
        </section>
      )}

      {estadoVenta === 'CONFIRMADA' && (
        <section className="alerta alerta-ok trabajo-cobrado-aviso">
          <span>Este trabajo ya se cobró, por eso no se puede editar.</span>
          <button type="button" className="btn-secundario btn-chico" onClick={verComprobante} disabled={abriendoComprobante}>
            {abriendoComprobante && <span className="spinner" aria-hidden="true" />}
            {abriendoComprobante ? 'Abriendo…' : 'Ver comprobante'}
          </button>
        </section>
      )}

      {error && <p className="alerta alerta-error">{error}</p>}
      {mensaje && <p className="alerta alerta-ok">{mensaje}</p>}

      <div className="trabajo-grilla">
        <fieldset className="trabajo-campos trabajo-principal" disabled={soloLectura}>
          <section className="tarjeta trabajo-seccion">
            <h3>Cliente y trabajo</h3>
            <div className="formulario-grilla">
              <label>
                Fecha del trabajo
                <input type="date" value={fechaProgramada} onChange={(e) => setFechaProgramada(e.target.value)} />
              </label>
              <div className="franja-horaria">
                <label>
                  Desde
                  <input type="time" value={horaDesde} onChange={(e) => setHoraDesde(e.target.value)} />
                </label>
                <label>
                  Hasta
                  <input type="time" value={horaHasta} onChange={(e) => setHoraHasta(e.target.value)} />
                </label>
              </div>
              <label>
                Nombre del cliente
                <input
                  autoComplete="off"
                  placeholder="Ej: Juan Pérez"
                  value={clienteNombre}
                  onChange={(e) => setClienteNombre(e.target.value)}
                />
              </label>
              <label>
                Teléfono
                <input
                  type="tel"
                  autoComplete="off"
                  placeholder="Ej: 11 2345-6789"
                  value={clienteTelefono}
                  onChange={(e) => setClienteTelefono(e.target.value)}
                />
              </label>
            </div>
            {/* Confirmación al cliente en un toque (Etapa 3): abre WhatsApp con el mensaje armado, sin
                usar la API. En la PC va por WhatsApp Web (la cuenta del local), siempre en la misma
                pestaña; en el celular, la app. Sin rel="noopener" para que la pestaña se pueda reusar. */}
            {numeroCliente && fechaProgramada && (
              <div className="trabajo-whatsapp">
                <a
                  className="boton-whatsapp"
                  href={linkWhatsapp(
                    numeroCliente,
                    mensajeConfirmacion({ clienteNombre, fechaProgramada, horaDesde, horaHasta, direccionTrabajo }),
                  )}
                  target={ES_CELULAR ? '_blank' : PESTANIA_WHATSAPP}
                  rel={ES_CELULAR ? 'noreferrer' : undefined}
                >
                  Confirmar turno por WhatsApp
                </a>
                {!ES_CELULAR && (
                  <small className="peticion-detalle">
                    Se abre en WhatsApp Web: en este navegador tiene que estar abierto el WhatsApp del local ({WHATSAPP_DEL_LOCAL}).
                  </small>
                )}
              </div>
            )}
            {clienteTelefono.trim() && !numeroCliente && (
              <small className="peticion-detalle">
                Para el botón de WhatsApp cargá el teléfono con código de área (ej: 11 5555-1234).
              </small>
            )}
            <label>
              Dirección
              <input
                autoComplete="off"
                placeholder="Ej: Av. Libertador 1450, entre San Martín y Belgrano, timbre 2B"
                value={direccionTrabajo}
                onChange={(e) => setDireccionTrabajo(e.target.value)}
              />
            </label>
            <HistorialCliente telefono={clienteTelefono} direccion={direccionTrabajo} idVentaActual={idVenta} />
            <label>
              Descripción del trabajo
              <textarea
                placeholder="Ej: Cambio de cerradura puerta de entrada, cliente perdió las llaves"
                value={descripcionTrabajo}
                onChange={(e) => setDescripcionTrabajo(e.target.value)}
              />
            </label>
            <label>
              Observaciones para el técnico
              <textarea
                placeholder="Ej: tocar timbre del portero, llevar cerradura de doble paleta"
                value={observacionesTecnico}
                onChange={(e) => setObservacionesTecnico(e.target.value)}
              />
            </label>
            <div className="formulario-grilla">
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
            </div>
          </section>

          <section className="tarjeta trabajo-seccion">
            <h3>Artículos y mano de obra</h3>
            {!soloLectura && (
              <>
                <span className="cobros-buscar-titulo">Artículo del stock</span>
                <BuscadorProductoCarrito
                  productos={productos}
                  datalistId="productos-datalist-domicilio"
                  onAgregar={agregarProductoAlCarrito}
                />
                <span className="cobros-buscar-titulo">Mano de obra o servicio</span>
                <div className="agregar-producto">
                  <input
                    className="buscador-producto-texto"
                    aria-label="Descripción de la mano de obra"
                    placeholder="Ej: Instalación de cerradura"
                    value={manualDescripcion}
                    onChange={(e) => setManualDescripcion(e.target.value)}
                  />
                  <input
                    type="number"
                    inputMode="decimal"
                    className="trabajo-precio-manual"
                    aria-label="Precio de la mano de obra"
                    min={0}
                    step="0.01"
                    placeholder="Precio"
                    value={manualPrecio}
                    onChange={(e) => setManualPrecio(e.target.value)}
                  />
                  <button type="button" onClick={agregarManoDeObra} disabled={!manualDescripcion.trim() || !manualPrecio}>
                    Agregar
                  </button>
                </div>
              </>
            )}

            {carrito.length === 0 ? (
              <p className="cobros-vacio">Todavía no hay artículos ni mano de obra.</p>
            ) : (
              <div className="tabla-scroll">
                <table className="trabajo-items">
                  <thead>
                    <tr>
                      <th>Descripción</th>
                      <th>Tipo</th>
                      <th className="celda-numero">Cantidad</th>
                      <th className="celda-numero">Precio</th>
                      <th className="celda-numero">Subtotal</th>
                      {!soloLectura && (
                        <th>
                          <span className="solo-lector">Acciones</span>
                        </th>
                      )}
                    </tr>
                  </thead>
                  <tbody>
                    {carrito.map((item, index) => (
                      <tr key={item.clientId}>
                        <td className="texto-fuerte">{item.descripcionProducto}</td>
                        <td>
                          <span className={`badge ${item.tipo === 'SERVICIO' ? 'badge-info' : ''}`}>
                            {item.tipo === 'SERVICIO' ? 'Mano de obra' : 'Artículo'}
                          </span>
                        </td>
                        <td className="celda-numero">{item.cantidad}</td>
                        <td className="celda-numero">
                          {soloLectura ? (
                            formatoPesos(item.precioUnitario)
                          ) : (
                            // Editable: un artículo puede cobrarse en el domicilio distinto que en mostrador.
                            // Solo cambia lo que se cobra; el costo (y por lo tanto la ganancia real) sale
                            // siempre del producto.
                            <input
                              className="precio-editable"
                              type="number"
                              inputMode="decimal"
                              min={0.01}
                              step="0.01"
                              aria-label={`Precio de ${item.descripcionProducto}`}
                              value={Number.isNaN(item.precioUnitario) ? '' : item.precioUnitario}
                              onChange={(e) => cambiarPrecio(index, e.target.value === '' ? Number.NaN : Number(e.target.value))}
                            />
                          )}
                          {(() => {
                            const precioMostrador = productos.find((p) => p.idProducto === item.idProducto)?.precioVenta
                            return item.idProducto != null && precioMostrador != null && precioMostrador !== item.precioUnitario ? (
                              <small className="precio-mostrador">Mostrador: {formatoPesos(precioMostrador)}</small>
                            ) : null
                          })()}
                        </td>
                        <td className="celda-numero texto-fuerte">
                          {Number.isNaN(item.precioUnitario) ? '—' : formatoPesos(item.cantidad * item.precioUnitario)}
                        </td>
                        {!soloLectura && (
                          <td className="celda-numero">
                            <button
                              type="button"
                              className="btn-peligro-borde btn-chico"
                              aria-label={`Quitar ${item.descripcionProducto}`}
                              onClick={() => quitarDelCarrito(index)}
                            >
                              Quitar
                            </button>
                          </td>
                        )}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        </fieldset>

        <aside className="tarjeta trabajo-lateral">
          <div className="cobros-total">
            <span>Total del trabajo</span>
            <strong>{formatoPesos(total)}</strong>
          </div>
          <dl className="trabajo-resumen">
            <div>
              <dt>Mano de obra</dt>
              <dd>{formatoPesos(manoDeObraTotal)}</dd>
            </div>
            <div>
              <dt>
                Para el técnico{' '}
                <small>({porcentajeTecnico != null ? `${porcentajeTecnico}% de la mano de obra` : 'sin asignar'})</small>
              </dt>
              <dd>{formatoPesos(comision)}</dd>
            </div>
            <div>
              <dt>Para el negocio</dt>
              <dd>{formatoPesos(total - comision)}</dd>
            </div>
            <div className="trabajo-resumen-ganancia">
              <dt>Ganancia (restando el costo de los artículos)</dt>
              <dd>{formatoPesos(gananciaNeta)}</dd>
            </div>
          </dl>

          <fieldset className="trabajo-campos" disabled={soloLectura}>
            <label>
              Forma de pago
              <select value={medioPago} onChange={(e) => setMedioPago(e.target.value as MedioPago)}>
                {!medioPago && <option value="">Sin especificar</option>}
                <option value="EFECTIVO">Efectivo</option>
                <option value="TRANSFERENCIA">Transferencia</option>
                <option value="TARJETA">Tarjeta</option>
              </select>
            </label>
          </fieldset>

          {!soloLectura && (
            <div className="trabajo-lateral-acciones">
              <button type="button" onClick={() => guardar(true)} disabled={guardando}>
                {guardando && <span className="spinner" aria-hidden="true" />}
                Cerrar y cobrar
              </button>
              <button type="button" className="btn-secundario" onClick={() => guardar(false)} disabled={guardando}>
                {guardando && <span className="spinner" aria-hidden="true" />}
                Guardar borrador
              </button>
            </div>
          )}
        </aside>
      </div>

      {idVenta != null && estadoVenta !== 'ANULADA' && (
        <section className="tarjeta trabajo-seccion">
          <h3>Notas</h3>
          <div className="trabajo-notas">
            {notas.length === 0 && <p className="peticion-detalle">Todavía no hay notas.</p>}
            {notas.map((n) => (
              <div key={n.idNota} className="trabajo-nota">
                <span className="texto-con-saltos">{n.texto}</span>
                <small>
                  {n.nombreEmpleado} — {formatoFechaHora(n.creadoEn)}
                </small>
              </div>
            ))}
          </div>
          {/* Las notas del jefe le llegan al técnico como aviso; las del técnico, al jefe. */}
          {!soloLectura && (
            <form className="trabajo-nota-form" onSubmit={agregarNota}>
              <textarea
                aria-label="Nota nueva"
                value={notaTexto}
                onChange={(e) => setNotaTexto(e.target.value)}
                placeholder="Ej: llevá la escalera grande, el cliente pidió que llames antes de llegar…"
                rows={2}
              />
              <button type="submit" disabled={agregandoNota || !notaTexto.trim()}>
                {agregandoNota && <span className="spinner" aria-hidden="true" />}
                Agregar nota (le avisa al técnico)
              </button>
            </form>
          )}
        </section>
      )}

      {estadoVenta !== 'ANULADA' && (
        <section className="tarjeta trabajo-seccion">
          <h3>Fotos, videos y audios</h3>
          {idVenta == null ? (
            <p className="peticion-detalle">Guardá el trabajo para poder adjuntarle fotos, videos o audios.</p>
          ) : (
            <AdjuntosTrabajo
              lado="jefe"
              idVenta={idVenta}
              fotos={fotos}
              onCambioFotos={setFotos}
              archivos={archivos}
              onCambioArchivos={setArchivos}
              idEmpleadoActual={sesion?.idEmpleado}
              puedeSubir={!soloLectura}
              ayuda="Adjuntá lo que te mande el cliente (fotos, videos, notas de voz) para que el técnico sepa con qué se va a encontrar. Acá también ves lo que suba él."
            />
          )}
        </section>
      )}
    </div>
  )
}
