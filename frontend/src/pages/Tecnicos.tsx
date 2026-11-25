import { useEffect, useState, type FormEvent } from 'react'
import { actualizarTecnico, crearTecnico, desactivarEmpleado, getEmpleados } from '../api/empleados'
import { ApiRequestError } from '../api/client'
import type { EmpleadoResponse, TecnicoRequest } from '../types/api'

function textoError(err: unknown, porDefecto: string): string {
  return err instanceof ApiRequestError ? err.message : porDefecto
}

// Un técnico dado de alta sin acceso queda con un usuario "tecnico-<uuid>" que nadie conoce (la
// tabla exige usuario). Espejo de EmpleadoService.PREFIJO_USUARIO_SIN_ACCESO: el backend no deja
// elegir un usuario real con este prefijo, así que alcanza para saber si tiene acceso.
const PREFIJO_SIN_ACCESO = 'tecnico-'

function tieneAcceso(tecnico: EmpleadoResponse): boolean {
  return !tecnico.usuario.startsWith(PREFIJO_SIN_ACCESO)
}

function FilaTecnico({ tecnico, onCambio }: { tecnico: EmpleadoResponse; onCambio: () => Promise<void> }) {
  const conAcceso = tieneAcceso(tecnico)
  const [nombre, setNombre] = useState(tecnico.nombre)
  const [comision, setComision] = useState(String(tecnico.comision ?? ''))
  const [editandoAcceso, setEditandoAcceso] = useState(false)
  const [usuario, setUsuario] = useState(conAcceso ? tecnico.usuario : '')
  const [password, setPassword] = useState('')
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const cambioDatos = nombre !== tecnico.nombre || comision !== String(tecnico.comision ?? '')
  const cambioAcceso = editandoAcceso && (usuario !== (conAcceso ? tecnico.usuario : '') || password !== '')
  // Darle acceso por primera vez exige las dos cosas; a uno que ya tiene, la contraseña es opcional.
  const accesoIncompleto = editandoAcceso && (!usuario.trim() || (!conAcceso && !password))

  async function guardar() {
    setError(null)
    setGuardando(true)
    const req: TecnicoRequest = { nombre, comision: Number(comision) }
    if (editandoAcceso) {
      req.usuario = usuario.trim()
      if (password) req.password = password
    }
    try {
      await actualizarTecnico(tecnico.idEmpleado, req)
      await onCambio()
    } catch (err) {
      setError(textoError(err, 'No se pudo guardar el técnico'))
    } finally {
      setGuardando(false)
    }
  }

  async function darDeBaja() {
    if (!window.confirm(`¿Dar de baja a "${tecnico.nombre}"? Sus trabajos ya cobrados no cambian y ya no va a poder entrar al sistema.`)) return
    setError(null)
    setGuardando(true)
    try {
      await desactivarEmpleado(tecnico.idEmpleado)
      await onCambio()
    } catch (err) {
      setError(textoError(err, 'No se pudo dar de baja al técnico'))
      setGuardando(false)
    }
  }

  return (
    <article className="tecnico-fila">
      <label>
        Nombre
        <input value={nombre} onChange={(e) => setNombre(e.target.value)} />
      </label>
      <label>
        Comisión %
        <input type="number" min={0} max={100} step="0.01" value={comision} onChange={(e) => setComision(e.target.value)} />
      </label>

      <p className="tecnico-acceso">
        {conAcceso ? (
          <>
            Entra al sistema con el usuario <strong>{tecnico.usuario}</strong>
          </>
        ) : (
          'Sin acceso al sistema'
        )}
        {!editandoAcceso && (
          <button type="button" className="boton-secundario" onClick={() => setEditandoAcceso(true)}>
            {conAcceso ? 'Cambiar usuario o contraseña' : 'Darle acceso'}
          </button>
        )}
      </p>

      {editandoAcceso && (
        <>
          <label>
            Usuario
            <input autoComplete="off" value={usuario} onChange={(e) => setUsuario(e.target.value)} />
          </label>
          <label>
            {conAcceso ? 'Contraseña nueva (vacío = no cambia)' : 'Contraseña'}
            <input type="password" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} />
          </label>
        </>
      )}

      <div className="comprobante-acciones tecnico-acciones">
        <button
          type="button"
          onClick={guardar}
          disabled={guardando || (!cambioDatos && !cambioAcceso) || !nombre.trim() || comision === '' || accesoIncompleto}
        >
          Guardar
        </button>
        {editandoAcceso && (
          <button type="button" onClick={() => setEditandoAcceso(false)} disabled={guardando}>
            Cancelar
          </button>
        )}
        <button type="button" className="boton-rechazar" onClick={darDeBaja} disabled={guardando}>
          Dar de baja
        </button>
      </div>
      {error && <p className="error">{error}</p>}
    </article>
  )
}

export function Tecnicos() {
  const [tecnicos, setTecnicos] = useState<EmpleadoResponse[]>([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [nombre, setNombre] = useState('')
  const [comision, setComision] = useState('')
  const [conAcceso, setConAcceso] = useState(true)
  const [usuario, setUsuario] = useState('')
  const [password, setPassword] = useState('')
  const [agregando, setAgregando] = useState(false)
  const [errorAlta, setErrorAlta] = useState<string | null>(null)

  async function cargar() {
    setError(null)
    try {
      const empleados = await getEmpleados()
      setTecnicos(empleados.filter((e) => e.rol === 'TECNICO'))
    } catch (err) {
      setError(textoError(err, 'No se pudieron cargar los técnicos'))
    }
  }

  useEffect(() => {
    cargar().finally(() => setCargando(false))
  }, [])

  async function agregar(e: FormEvent) {
    e.preventDefault()
    setErrorAlta(null)
    setAgregando(true)
    const req: TecnicoRequest = { nombre, comision: Number(comision) }
    if (conAcceso) {
      req.usuario = usuario.trim()
      req.password = password
    }
    try {
      await crearTecnico(req)
      setNombre('')
      setComision('')
      setUsuario('')
      setPassword('')
      await cargar()
    } catch (err) {
      setErrorAlta(textoError(err, 'No se pudo agregar el técnico'))
    } finally {
      setAgregando(false)
    }
  }

  if (cargando) return <p>Cargando...</p>
  if (error) return <p className="error">{error}</p>

  return (
    <div>
      <h2>Técnicos</h2>
      <p>
        La comisión es el % de la mano de obra de cada trabajo que se lleva el técnico; el resto queda para el negocio.
        Los artículos no pagan comisión. Si la cambiás, se aplica a los trabajos que se cobren desde ahora.
      </p>
      <p>
        Un técnico con acceso entra al sistema (también desde el celular) y ve <strong>solo sus trabajos a domicilio</strong>:
        los que le asignás desde la agenda. No ve caja, ventas, productos ni nada más. Desde ahí marca &quot;En camino&quot;,
        &quot;En curso&quot; y &quot;Terminado&quot; y deja notas; vos te enterás por los avisos.
      </p>

      {tecnicos.length === 0 && <p>Todavía no hay técnicos.</p>}
      {tecnicos.map((tecnico) => (
        <FilaTecnico
          key={`${tecnico.idEmpleado}-${tecnico.nombre}-${tecnico.comision}-${tecnico.usuario}`}
          tecnico={tecnico}
          onCambio={cargar}
        />
      ))}

      <section>
        <h3>Agregar técnico</h3>
        <form onSubmit={agregar}>
          <label>
            Nombre
            <input required value={nombre} onChange={(e) => setNombre(e.target.value)} />
          </label>
          <label>
            Comisión % sobre la mano de obra
            <input
              required
              type="number"
              min={0}
              max={100}
              step="0.01"
              placeholder="Ej: 50"
              value={comision}
              onChange={(e) => setComision(e.target.value)}
            />
          </label>
          <label className="checkbox-label">
            <input type="checkbox" checked={conAcceso} onChange={(e) => setConAcceso(e.target.checked)} />
            Puede entrar al sistema para ver y actualizar sus trabajos
          </label>
          {conAcceso && (
            <>
              <label>
                Usuario
                <input required autoComplete="off" value={usuario} onChange={(e) => setUsuario(e.target.value)} />
              </label>
              <label>
                Contraseña
                <input
                  required
                  type="password"
                  autoComplete="new-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                />
              </label>
            </>
          )}
          {errorAlta && <p className="error">{errorAlta}</p>}
          <button type="submit" disabled={agregando}>
            {agregando && <span className="spinner" />}
            {agregando ? 'Agregando...' : 'Agregar técnico'}
          </button>
        </form>
      </section>
    </div>
  )
}
