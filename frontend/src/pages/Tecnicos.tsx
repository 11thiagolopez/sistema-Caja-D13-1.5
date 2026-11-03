import { useEffect, useState, type FormEvent } from 'react'
import { actualizarTecnico, crearTecnico, desactivarEmpleado, getEmpleados } from '../api/empleados'
import { ApiRequestError } from '../api/client'
import type { EmpleadoResponse } from '../types/api'

function textoError(err: unknown, porDefecto: string): string {
  return err instanceof ApiRequestError ? err.message : porDefecto
}

function FilaTecnico({ tecnico, onCambio }: { tecnico: EmpleadoResponse; onCambio: () => Promise<void> }) {
  const [nombre, setNombre] = useState(tecnico.nombre)
  const [comision, setComision] = useState(String(tecnico.comision ?? ''))
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const hayCambios = nombre !== tecnico.nombre || comision !== String(tecnico.comision ?? '')

  async function guardar() {
    setError(null)
    setGuardando(true)
    try {
      await actualizarTecnico(tecnico.idEmpleado, { nombre, comision: Number(comision) })
      await onCambio()
    } catch (err) {
      setError(textoError(err, 'No se pudo guardar el técnico'))
    } finally {
      setGuardando(false)
    }
  }

  async function darDeBaja() {
    if (!window.confirm(`¿Dar de baja a "${tecnico.nombre}"? Sus trabajos ya cobrados no cambian.`)) return
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
      <div className="comprobante-acciones tecnico-acciones">
        <button type="button" onClick={guardar} disabled={guardando || !hayCambios || !nombre.trim() || comision === ''}>
          Guardar
        </button>
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
    try {
      await crearTecnico({ nombre, comision: Number(comision) })
      setNombre('')
      setComision('')
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

      {tecnicos.length === 0 && <p>Todavía no hay técnicos.</p>}
      {tecnicos.map((tecnico) => (
        <FilaTecnico key={`${tecnico.idEmpleado}-${tecnico.nombre}-${tecnico.comision}`} tecnico={tecnico} onCambio={cargar} />
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
