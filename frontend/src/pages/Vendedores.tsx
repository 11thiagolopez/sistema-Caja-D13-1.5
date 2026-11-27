import { useEffect, useState, type FormEvent } from 'react'
import { crearEmpleado, desactivarEmpleado, getEmpleados } from '../api/empleados'
import { ApiRequestError } from '../api/client'
import { EncabezadoPagina } from '../components/EncabezadoPagina'
import { Modal } from '../components/Modal'
import type { EmpleadoRequest, EmpleadoResponse, Rol } from '../types/api'

const VENDEDOR_VACIO: EmpleadoRequest = {
  nombre: '',
  usuario: '',
  password: '',
  email: '',
  rol: 'VENDEDOR',
  comision: undefined,
}
const NOMBRE_ROL: Record<string, string> = { ADMIN: 'Administrador', VENDEDOR: 'Vendedor', TECNICO: 'Técnico' }


export function Vendedores() {
  const [empleados, setEmpleados] = useState<EmpleadoResponse[]>([])
  const [error, setError] = useState<string | null>(null)
  const [cargando, setCargando] = useState(true)

  const [form, setForm] = useState<EmpleadoRequest>(VENDEDOR_VACIO)
  const [errorAlta, setErrorAlta] = useState<string | null>(null)
  const [agregando, setAgregando] = useState(false)
  const [altaAbierta, setAltaAbierta] = useState(false)
  const [eliminandoId, setEliminandoId] = useState<number | null>(null)

  function cargar() {
    setError(null)
    return getEmpleados()
      .then((todos) => setEmpleados(todos.filter((e) => e.rol !== 'TECNICO')))
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
      setAltaAbierta(false)
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

  if (cargando) return <p className="texto-apagado">Cargando…</p>
  if (error) return <p className="alerta alerta-error">{error}</p>

  return (
    <div>
      <EncabezadoPagina
        titulo="Vendedores"
        subtitulo={`${empleados.length} ${empleados.length === 1 ? 'usuario' : 'usuarios'} del sistema`}
        acciones={
          <button type="button" onClick={() => setAltaAbierta(true)}>
            + Agregar vendedor
          </button>
        }
      />

      <section className="tarjeta productos-tabla">
        {empleados.length === 0 ? (
          <p className="cobros-vacio">Todavía no hay vendedores.</p>
        ) : (
          <div className="tabla-scroll">
            <table>
              <thead>
                <tr>
                  <th>Nombre</th>
                  <th>Usuario</th>
                  <th>Rol</th>
                  <th>Email</th>
                  <th>
                    <span className="solo-lector">Acciones</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {empleados.map((e) => (
                  <tr key={e.idEmpleado}>
                    <td className="texto-fuerte">{e.nombre}</td>
                    <td className="texto-apagado">{e.usuario}</td>
                    <td>
                      <span className={`badge ${e.rol === 'ADMIN' ? 'badge-info' : ''}`}>{NOMBRE_ROL[e.rol] ?? e.rol}</span>
                    </td>
                    <td>{e.email || '—'}</td>
                    <td className="celda-numero">
                      <button
                        type="button"
                        className="btn-peligro-borde btn-chico"
                        onClick={() => onDesactivar(e)}
                        disabled={eliminandoId === e.idEmpleado}
                      >
                        {eliminandoId === e.idEmpleado ? 'Dando de baja…' : 'Dar de baja'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {altaAbierta && (
        <Modal title="Agregar vendedor" onClose={() => setAltaAbierta(false)}>
          <form onSubmit={onAgregar} className="formulario-grilla">
            <label className="campo-ancho">
              Nombre
              <input required value={form.nombre} onChange={(e) => setForm({ ...form, nombre: e.target.value })} />
            </label>
            <label>
              Usuario
              <input
                required
                autoCapitalize="none"
                autoComplete="off"
                spellCheck={false}
                value={form.usuario}
                onChange={(e) => setForm({ ...form, usuario: e.target.value })}
              />
            </label>
            <label>
              Contraseña
              <input
                type="password"
                autoComplete="new-password"
                required
                value={form.password}
                onChange={(e) => setForm({ ...form, password: e.target.value })}
              />
            </label>
            <label>
              Email (opcional)
              <input
                type="email"
                autoComplete="off"
                spellCheck={false}
                value={form.email}
                onChange={(e) => setForm({ ...form, email: e.target.value })}
              />
            </label>
            <label>
              Rol
              <select value={form.rol} onChange={(e) => setForm({ ...form, rol: e.target.value as Rol })}>
                <option value="VENDEDOR">Vendedor</option>
                <option value="ADMIN">Administrador</option>
              </select>
            </label>
            {errorAlta && <p className="alerta alerta-error campo-ancho">{errorAlta}</p>}
            <div className="formulario-acciones campo-ancho">
              <button type="button" className="btn-secundario" onClick={() => setAltaAbierta(false)}>
                Cancelar
              </button>
              <button type="submit" disabled={agregando}>
                {agregando && <span className="spinner" aria-hidden="true" />}
                {agregando ? 'Agregando…' : 'Agregar vendedor'}
              </button>
            </div>
          </form>
        </Modal>
      )}
    </div>
  )
}
