import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  agregarNotaMiTrabajo,
  cambiarEstadoMiTrabajo,
  getMiTrabajo,
  getMisTrabajos,
} from '../api/misTrabajos'
import { ApiRequestError } from '../api/client'
import { hoyIso } from '../utils/date'
import { formatoPesos } from '../utils/formato'
import { estadoVisibleTrabajo } from '../utils/estadoTrabajo'
import type { EstadoTrabajoTecnico, MiTrabajoResponse } from '../types/api'

const BOTONES_ESTADO: { valor: EstadoTrabajoTecnico; etiqueta: string }[] = [
  { valor: 'EN_CAMINO', etiqueta: 'En camino' },
  { valor: 'EN_CURSO', etiqueta: 'Llegué' },
  { valor: 'TERMINADO', etiqueta: 'Terminado' },
]

function horario(trabajo: MiTrabajoResponse['trabajo']): string {
  if (!trabajo.horaDesde) return ''
  const desde = trabajo.horaDesde.slice(0, 5)
  const hasta = trabajo.horaHasta?.slice(0, 5)
  return hasta ? `${desde} a ${hasta}` : `desde las ${desde}`
}

function estaCerrado(trabajo: MiTrabajoResponse['trabajo']): boolean {
  return trabajo.estado === 'ANULADA' || trabajo.estadoTrabajo === 'CERRADO_Y_COBRADO'
    || trabajo.estadoTrabajo === 'CANCELADO'
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
  const terminados = trabajos.filter((t) => estaCerrado(t.trabajo) || t.trabajo.estadoTrabajo === 'TERMINADO')
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
          {t.trabajo.fechaProgramada} {horario(t.trabajo)}
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

  if (error) {
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
      <button type="button" className="boton-link" onClick={() => navigate('/mis-trabajos')}>
        ← Mis trabajos
      </button>

      <h2>{v.clienteNombre}</h2>
      <p className="trabajo-detalle-direccion">{v.direccionTrabajo ?? 'Sin dirección cargada'}</p>
      <p>
        {v.fechaProgramada} {horario(v)}
      </p>
      {v.descripcionTrabajo && <p>{v.descripcionTrabajo}</p>}
      {v.observacionesTecnico && (
        <p className="trabajo-detalle-observaciones">Observaciones del jefe: {v.observacionesTecnico}</p>
      )}
      <p>Total: {formatoPesos(v.totalVenta)}</p>

      <div className="botones-grandes">
        {v.direccionTrabajo && (
          <a
            className="boton-grande"
            href={`https://maps.google.com/?q=${encodeURIComponent(v.direccionTrabajo)}`}
            target="_blank"
            rel="noreferrer"
          >
            Cómo llegar
          </a>
        )}
        {v.clienteTelefono && (
          <a className="boton-grande" href={`tel:${v.clienteTelefono}`}>
            Llamar al cliente
          </a>
        )}
      </div>

      {error && <p className="error">{error}</p>}

      {cerrado ? (
        <p className="peticion-detalle">
          Este trabajo ya está {v.estadoTrabajo === 'CANCELADO' ? 'cancelado' : 'cerrado'}: no se puede actualizar.
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

      <h3>Notas</h3>
      <div className="trabajo-notas">
        {trabajo.notas.length === 0 && <p className="peticion-detalle">Todavía no hay notas.</p>}
        {trabajo.notas.map((n) => (
          <div key={n.idNota} className="trabajo-nota">
            <span>{n.texto}</span>
            <small>
              {n.nombreEmpleado} — {n.creadoEn.slice(0, 16).replace('T', ' ')}
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
    </div>
  )
}
