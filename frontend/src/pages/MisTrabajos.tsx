import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  agregarNotaMiTrabajo,
  cambiarEstadoMiTrabajo,
  getMiTrabajo,
  getMisTrabajos,
} from '../api/misTrabajos'
import { ApiRequestError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { AdjuntosTrabajo } from '../components/AdjuntosTrabajo'
import { hoyIso } from '../utils/date'
import { formatoDiaLargo, formatoFechaHora, formatoPesos } from '../utils/formato'
import { estadoVisibleTrabajo, trabajoBloqueado } from '../utils/estadoTrabajo'
import type { EstadoTrabajoTecnico, MiTrabajoResponse } from '../types/api'

const BOTONES_ESTADO: { valor: EstadoTrabajoTecnico; etiqueta: string }[] = [
  { valor: 'EN_CAMINO', etiqueta: 'En camino' },
  { valor: 'EN_CURSO', etiqueta: 'Llegué' },
  // Es un aviso para el jefe (TERMINADO_TECNICO): el trabajo se cierra cuando él lo confirma.
  { valor: 'TERMINADO_TECNICO', etiqueta: 'Terminé' },
]

function horario(trabajo: MiTrabajoResponse['trabajo']): string {
  if (!trabajo.horaDesde) return ''
  const desde = trabajo.horaDesde.slice(0, 5)
  const hasta = trabajo.horaHasta?.slice(0, 5)
  return hasta ? `${desde} a ${hasta}` : `desde las ${desde}`
}

// Terminado por el jefe, cobrado o cancelado: ya no se cambia el estado ni se agregan notas o fotos.
function estaCerrado(trabajo: MiTrabajoResponse['trabajo']): boolean {
  return trabajoBloqueado(trabajo)
}

export function MisTrabajos() {
  const { id } = useParams<{ id: string }>()
  return id ? <Detalle id={Number(id)} /> : <Lista />
}

function Lista() {
  const navigate = useNavigate()
  const [trabajos, setTrabajos] = useState<MiTrabajoResponse[]>([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    getMisTrabajos()
      .then(setTrabajos)
      .catch(() => setError('No se pudieron cargar tus trabajos'))
      .finally(() => setCargando(false))
  }, [])

  if (cargando) return <p className="peticion-detalle">Cargando...</p>
  if (error) return <p className="error">{error}</p>

  const hoy = hoyIso()
  const terminados = trabajos.filter((t) => estaCerrado(t.trabajo) || t.trabajo.estadoTrabajo === 'TERMINADO_TECNICO')
  const activos = trabajos.filter((t) => !terminados.includes(t))
  const deHoy = activos.filter((t) => t.trabajo.fechaProgramada === hoy)
  const proximos = activos.filter((t) => t.trabajo.fechaProgramada !== hoy)

  function fila(t: MiTrabajoResponse) {
    const { clase, etiqueta } = estadoVisibleTrabajo(t.trabajo)
    return (
      <button
        key={t.trabajo.idVenta}
        type="button"
        className={`trabajo-fila ${clase}`}
        onClick={() => navigate(`/mis-trabajos/${t.trabajo.idVenta}`)}
      >
        <strong>{t.trabajo.direccionTrabajo ?? 'Sin dirección'}</strong>
        <span>
          {t.trabajo.fechaProgramada ? formatoDiaLargo(t.trabajo.fechaProgramada) : 'Sin fecha'} {horario(t.trabajo)}
        </span>
        <span className="trabajo-fila-estado">{etiqueta}</span>
      </button>
    )
  }

  return (
    <div className="mis-trabajos">
      <h2>Mis trabajos</h2>
      {trabajos.length === 0 && <p className="peticion-detalle">No tenés trabajos asignados.</p>}
      {deHoy.length > 0 && (
        <section>
          <h3>Hoy</h3>
          <div className="trabajo-lista">{deHoy.map(fila)}</div>
        </section>
      )}
      {proximos.length > 0 && (
        <section>
          <h3>Próximos</h3>
          <div className="trabajo-lista">{proximos.map(fila)}</div>
        </section>
      )}
      {terminados.length > 0 && (
        <section>
          <h3>Terminados</h3>
          <div className="trabajo-lista">{terminados.map(fila)}</div>
        </section>
      )}
    </div>
  )
}

function Detalle({ id }: { id: number }) {
  const navigate = useNavigate()
  const { sesion } = useAuth()
  const [trabajo, setTrabajo] = useState<MiTrabajoResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [actualizando, setActualizando] = useState(false)
  const [notaTexto, setNotaTexto] = useState('')
  const [agregandoNota, setAgregandoNota] = useState(false)

  function cargar() {
    getMiTrabajo(id)
      .then(setTrabajo)
      .catch((err) => setError(err instanceof ApiRequestError ? err.message : 'No se pudo cargar el trabajo'))
  }

  useEffect(cargar, [id])

  if (error && !trabajo) {
    return (
      <div className="mis-trabajos">
        <p className="error">{error}</p>
        <button type="button" onClick={() => navigate('/mis-trabajos')}>
          Volver
        </button>
      </div>
    )
  }
  if (!trabajo) return <p className="peticion-detalle">Cargando...</p>

  const v = trabajo.trabajo
  const cerrado = estaCerrado(v)
  const { clase, etiqueta } = estadoVisibleTrabajo(v)

  async function cambiarEstado(estado: EstadoTrabajoTecnico) {
    setActualizando(true)
    setError(null)
    try {
      setTrabajo(await cambiarEstadoMiTrabajo(id, estado))
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo actualizar el estado')
    } finally {
      setActualizando(false)
    }
  }

  async function agregarNota(e: FormEvent) {
    e.preventDefault()
    if (!notaTexto.trim()) return
    setAgregandoNota(true)
    setError(null)
    try {
      await agregarNotaMiTrabajo(id, notaTexto.trim())
      setNotaTexto('')
      cargar()
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo agregar la nota')
    } finally {
      setAgregandoNota(false)
    }
  }

  return (
    <div className="mis-trabajos mis-trabajos-detalle">
      <button type="button" className="boton-link mis-trabajos-volver" onClick={() => navigate('/mis-trabajos')}>
        ← Mis trabajos
      </button>

      <header className="mi-trabajo-encabezado">
        <h2>Trabajo #{v.idVenta}</h2>
        <span className={`trabajo-estado ${clase}`}>{etiqueta}</span>
      </header>

      {/* Sin teléfono ni "Llamar al cliente" a propósito: el contacto con el cliente lo maneja el
          jefe (pedido del dueño). El backend tampoco lo manda (TrabajoTecnicoResponse). */}
      <section className="mi-trabajo-tarjeta">
        <dl className="mi-trabajo-datos">
          <dt>Cliente</dt>
          <dd>{v.clienteNombre || '—'}</dd>
          <dt>Dirección</dt>
          <dd>
            {v.direccionTrabajo ?? 'Sin dirección cargada'}
            {v.direccionTrabajo && (
              <a
                className="boton-grande mi-trabajo-como-llegar"
                href={`https://maps.google.com/?q=${encodeURIComponent(v.direccionTrabajo)}`}
                target="_blank"
                rel="noreferrer"
              >
                Cómo llegar
              </a>
            )}
          </dd>
          <dt>Fecha</dt>
          <dd>{v.fechaProgramada ? formatoDiaLargo(v.fechaProgramada) : '—'}</dd>
          {v.horaDesde && (
            <>
              <dt>Horario</dt>
              <dd>{horario(v)}</dd>
            </>
          )}
          <dt>Trabajo</dt>
          <dd className="texto-con-saltos">{v.descripcionTrabajo || '—'}</dd>
          {v.observacionesTecnico && (
            <>
              <dt>Indicaciones del jefe</dt>
              <dd className="texto-con-saltos mi-trabajo-observaciones">{v.observacionesTecnico}</dd>
            </>
          )}
        </dl>
      </section>

      {v.detalles.length > 0 && (
        <section className="mi-trabajo-tarjeta">
          <h3>Materiales y mano de obra</h3>
          <ul className="mi-trabajo-items">
            {v.detalles.map((d, i) => (
              <li key={i}>
                <span>
                  {d.cantidad} × {d.descripcion}
                </span>
                <span>{formatoPesos(d.subtotal)}</span>
              </li>
            ))}
            <li className="mi-trabajo-total">
              <span>Total</span>
              <span>{formatoPesos(v.totalVenta)}</span>
            </li>
          </ul>
        </section>
      )}

      <section className="mi-trabajo-tarjeta">
        <h3>Estado</h3>
        {cerrado ? (
          <p className="peticion-detalle">
            {v.estado === 'ANULADA'
              ? 'Este trabajo se canceló.'
              : 'El jefe ya cerró este trabajo: no se puede actualizar.'}
          </p>
        ) : (
          <div className="botones-grandes">
            {BOTONES_ESTADO.map((b) => (
              <button
                key={b.valor}
                type="button"
                className={`boton-grande${v.estadoTrabajo === b.valor ? ' boton-grande-actual' : ''}`}
                disabled={actualizando}
                onClick={() => cambiarEstado(b.valor)}
              >
                {b.etiqueta}
              </button>
            ))}
          </div>
        )}
        {error && <p className="error">{error}</p>}
      </section>

      <section className="mi-trabajo-tarjeta">
        <h3>Fotos, videos y audios</h3>
        <AdjuntosTrabajo
          lado="tecnico"
          idVenta={v.idVenta}
          fotos={trabajo.fotos}
          onCambioFotos={(fotos) => setTrabajo((actual) => (actual ? { ...actual, fotos } : actual))}
          archivos={trabajo.archivos ?? []}
          onCambioArchivos={(archivos) => setTrabajo((actual) => (actual ? { ...actual, archivos } : actual))}
          idEmpleadoActual={sesion?.idEmpleado}
          puedeSubir={!cerrado}
          ayuda="Lo que te pasa el jefe y lo tuyo. Con Antes y Después sacá una foto o video de cómo estaba y de cómo quedó (sirven de comprobante); con Adjuntar sumás cualquier otra cosa, también audios."
        />
      </section>

      <section className="mi-trabajo-tarjeta">
        <h3>Notas</h3>
        <div className="trabajo-notas">
          {trabajo.notas.length === 0 && <p className="peticion-detalle">Todavía no hay notas.</p>}
          {trabajo.notas.map((n) => (
            <div key={n.idNota} className="trabajo-nota">
              <span className="texto-con-saltos">{n.texto}</span>
              <small>
                {n.nombreEmpleado} — {formatoFechaHora(n.creadoEn)}
              </small>
            </div>
          ))}
        </div>
        {!cerrado && (
          <form className="trabajo-nota-form" onSubmit={agregarNota}>
            <textarea
              value={notaTexto}
              onChange={(e) => setNotaTexto(e.target.value)}
              placeholder="Ej: falta un repuesto, el cliente no estaba..."
              rows={2}
            />
            <button type="submit" disabled={agregandoNota || !notaTexto.trim()}>
              {agregandoNota && <span className="spinner" />}
              Agregar nota
            </button>
          </form>
        )}
      </section>
    </div>
  )
}
