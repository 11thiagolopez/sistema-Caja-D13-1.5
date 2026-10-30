import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { actualizarCliente, crearCliente, eliminarCliente, getClientes } from '../api/clientes'
import { ApiRequestError } from '../api/client'
import type { ClienteDocTipo, ClienteRequest, ClienteResponse } from '../types/api'

const CLIENTE_VACIO: ClienteRequest = {
  nombre: '',
  docTipo: 99,
  docNro: '',
  telefono: '',
  email: '',
  direccion: '',
}

export function Clientes() {
  const [clientes, setClientes] = useState<ClienteResponse[]>([])
  const [error, setError] = useState<string | null>(null)
  const [cargando, setCargando] = useState(true)

  const [editandoId, setEditandoId] = useState<number | null>(null)
  const [form, setForm] = useState<ClienteRequest>(CLIENTE_VACIO)
  const [errorForm, setErrorForm] = useState<string | null>(null)
  const [guardando, setGuardando] = useState(false)
  const [eliminandoId, setEliminandoId] = useState<number | null>(null)

  function cargar() {
    setError(null)
    return getClientes()
      .then(setClientes)
      .catch((err) => setError(err instanceof ApiRequestError ? err.message : 'No se pudieron cargar los clientes'))
  }

  useEffect(() => {
    cargar().finally(() => setCargando(false))
  }, [])

  function onEditar(cliente: ClienteResponse) {
    setEditandoId(cliente.idCliente)
    setForm({
      nombre: cliente.nombre,
      docTipo: cliente.docTipo,
      docNro: cliente.docNro ?? '',
      telefono: cliente.telefono ?? '',
      email: cliente.email ?? '',
      direccion: cliente.direccion ?? '',
    })
  }

  function onCancelarEdicion() {
    setEditandoId(null)
    setForm(CLIENTE_VACIO)
    setErrorForm(null)
  }

  async function onGuardar(e: FormEvent) {
    e.preventDefault()
    setErrorForm(null)
    setGuardando(true)
    try {
      if (editandoId != null) {
        await actualizarCliente(editandoId, form)
      } else {
        await crearCliente(form)
      }
      onCancelarEdicion()
      await cargar()
    } catch (err) {
      setErrorForm(err instanceof ApiRequestError ? err.message : 'No se pudo guardar el cliente')
    } finally {
      setGuardando(false)
    }
  }

  async function onEliminar(cliente: ClienteResponse) {
    if (!window.confirm(`¿Dar de baja a "${cliente.nombre}"?`)) return
    setEliminandoId(cliente.idCliente)
    try {
      await eliminarCliente(cliente.idCliente)
      await cargar()
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo dar de baja al cliente')
    } finally {
      setEliminandoId(null)
    }
  }

  if (cargando) return <p>Cargando...</p>
  if (error) return <p className="error">{error}</p>

  return (
    <div>
      <h2>Clientes</h2>

      <table>
        <thead>
          <tr>
            <th>Nombre</th>
            <th>Documento</th>
            <th>Teléfono</th>
            <th>Email</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {clientes.map((c) => (
            <tr key={c.idCliente}>
              <td>{c.nombre}</td>
              <td>{c.docTipo === 99 ? '—' : `${c.docTipo === 80 ? 'CUIT' : 'DNI'} ${c.docNro ?? ''}`}</td>
              <td>{c.telefono || '—'}</td>
              <td>{c.email || '—'}</td>
              <td>
                <Link to={`/clientes/${c.idCliente}/cuenta-corriente`}>Cuenta corriente</Link>
                {' · '}
                <button type="button" onClick={() => onEditar(c)}>
                  Editar
                </button>
                {' · '}
                <button type="button" onClick={() => onEliminar(c)} disabled={eliminandoId === c.idCliente}>
                  {eliminandoId === c.idCliente ? 'Dando de baja...' : 'Dar de baja'}
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      <section>
        <h3>{editandoId != null ? 'Editar cliente' : 'Agregar cliente'}</h3>
        <form onSubmit={onGuardar}>
          <label>
            Nombre
            <input required value={form.nombre} onChange={(e) => setForm({ ...form, nombre: e.target.value })} />
          </label>
          <label>
            Tipo de documento
            <select
              value={form.docTipo}
              onChange={(e) => setForm({ ...form, docTipo: Number(e.target.value) as ClienteDocTipo })}
            >
              <option value={99}>Sin documento</option>
              <option value={96}>DNI</option>
              <option value={80}>CUIT</option>
            </select>
          </label>
          {form.docTipo !== 99 && (
            <label>
              Número de documento
              <input
                required
                value={form.docNro ?? ''}
                onChange={(e) => setForm({ ...form, docNro: e.target.value })}
              />
            </label>
          )}
          <label>
            Teléfono
            <input value={form.telefono ?? ''} onChange={(e) => setForm({ ...form, telefono: e.target.value })} />
          </label>
          <label>
            Email
            <input
              type="email"
              value={form.email ?? ''}
              onChange={(e) => setForm({ ...form, email: e.target.value })}
            />
          </label>
          <label>
            Dirección
            <input value={form.direccion ?? ''} onChange={(e) => setForm({ ...form, direccion: e.target.value })} />
          </label>
          {errorForm && <p className="error">{errorForm}</p>}
          <button type="submit" disabled={guardando}>
            {guardando && <span className="spinner" />}
            {guardando ? 'Guardando...' : editandoId != null ? 'Guardar cambios' : 'Agregar cliente'}
          </button>
          {editandoId != null && (
            <button type="button" onClick={onCancelarEdicion}>
              Cancelar
            </button>
          )}
        </form>
      </section>
    </div>
  )
}
