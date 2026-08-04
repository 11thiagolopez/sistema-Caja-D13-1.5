import { useEffect, useState, type FormEvent } from 'react'
import { crearEmpleado, desactivarEmpleado, getEmpleados } from '../api/empleados'
import { ApiRequestError } from '../api/client'
import type { EmpleadoRequest, EmpleadoResponse, Rol } from '../types/api'

const VENDEDOR_VACIO: EmpleadoRequest = {
  nombre: '',
  usuario: '',
  password: '',
  email: '',
  rol: 'VENDEDOR',
  comision: undefined,
}

export function Vendedores() {
  const [empleados, setEmpleados] = useState<EmpleadoResponse[]>([])
  const [error, setError] = useState<string | null>(null)
  const [cargando, setCargando] = useState(true)

  const [form, setForm] = useState<EmpleadoRequest>(VENDEDOR_VACIO)
  const [errorAlta, setErrorAlta] = useState<string | null>(null)
  const [agregando, setAgregando] = useState(false)
  const [eliminandoId, setEliminandoId] = useState<number | null>(null)

  function cargar() {
    setError(null)
    return getEmpleados()
      .then(setEmpleados)
      .catch((err) => setError(err instanceof ApiRequestError ? err.message : 'No se pudieron cargar los vendedores'))
  }

  useEffect(() => {
    cargar().finally(() => setCargando(false))
  }, [])

  async function onAgregar(e: FormEvent) {
    e.preventDefault()
    setErrorAlta(null)
    setAgregando(true)
    try {
      await crearEmpleado(form)
      setForm(VENDEDOR_VACIO)
      await cargar()
    } catch (err) {
      setErrorAlta(err instanceof ApiRequestError ? err.message : 'No se pudo agregar el vendedor')
    } finally {
      setAgregando(false)
    }
  }

  async function onDesactivar(empleado: EmpleadoResponse) {
    if (!window.confirm(`¿Dar de baja a "${empleado.nombre}"?`)) return
    setEliminandoId(empleado.idEmpleado)
    try {
      await desactivarEmpleado(empleado.idEmpleado)
      await cargar()
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo dar de baja al vendedor')
    } finally {
      setEliminandoId(null)
    }
  }

  if (cargando) return <p>Cargando...</p>
  if (error) return <p className="error">{error}</p>

  return (
    <div>
      <h2>Vendedores</h2>

      <table>
        <thead>
          <tr>
            <th>Nombre</th>
            <th>Usuario</th>
            <th>Rol</th>
            <th>Email</th>
            <th>Comisión %</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {empleados.map((e) => (
            <tr key={e.idEmpleado}>
              <td>{e.nombre}</td>
              <td>{e.usuario}</td>
              <td>{e.rol}</td>
              <td>{e.email}</td>
              <td>{e.comision != null ? e.comision : '—'}</td>
              <td>
                <button type="button" onClick={() => onDesactivar(e)} disabled={eliminandoId === e.idEmpleado}>
                  {eliminandoId === e.idEmpleado ? 'Dando de baja...' : 'Dar de baja'}
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      <section>
        <h3>Agregar vendedor</h3>
        <form onSubmit={onAgregar}>
          <label>
            Nombre
            <input required value={form.nombre} onChange={(e) => setForm({ ...form, nombre: e.target.value })} />
          </label>
          <label>
            Usuario
            <input required value={form.usuario} onChange={(e) => setForm({ ...form, usuario: e.target.value })} />
          </label>
          <label>
            Contraseña
            <input
              type="password"
              required
              value={form.password}
              onChange={(e) => setForm({ ...form, password: e.target.value })}
            />
          </label>
          <label>
            Email
            <input type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
          </label>
          <label>
            Rol
            <select value={form.rol} onChange={(e) => setForm({ ...form, rol: e.target.value as Rol })}>
              <option value="VENDEDOR">VENDEDOR</option>
              <option value="ADMIN">ADMIN</option>
            </select>
          </label>
          <label>
            Comisión % (sobre la ganancia de sus ventas)
            <input
              type="number"
              min={0}
              max={100}
              step="0.01"
              value={form.comision ?? ''}
              onChange={(e) => setForm({ ...form, comision: e.target.value ? Number(e.target.value) : undefined })}
            />
          </label>
          {errorAlta && <p className="error">{errorAlta}</p>}
          <button type="submit" disabled={agregando}>
            {agregando && <span className="spinner" />}
            {agregando ? 'Agregando...' : 'Agregar vendedor'}
          </button>
        </form>
      </section>
    </div>
  )
}
