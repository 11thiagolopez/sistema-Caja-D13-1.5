import { useEffect, useState } from 'react'
import { Modal } from './Modal'
import {
  actualizarProveedor,
  crearProveedor,
  eliminarProveedor,
  getProveedores,
} from '../api/proveedores'
import { ApiRequestError } from '../api/client'
import type { ProveedorRequest, ProveedorResponse } from '../types/api'

interface ProveedoresModalProps {
  onClose: () => void
}

const PROVEEDOR_VACIO: ProveedorRequest = { nombre: '', contacto: '', telefono: '', email: '' }

export function ProveedoresModal({ onClose }: ProveedoresModalProps) {
  const [proveedores, setProveedores] = useState<ProveedorResponse[]>([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [editandoId, setEditandoId] = useState<number | null>(null)
  const [form, setForm] = useState<ProveedorRequest>(PROVEEDOR_VACIO)
  const [guardando, setGuardando] = useState(false)
  const [errorForm, setErrorForm] = useState<string | null>(null)
  const [eliminandoId, setEliminandoId] = useState<number | null>(null)

  function cargar() {
    setError(null)
    return getProveedores()
      .then(setProveedores)
      .catch((err) => setError(err instanceof ApiRequestError ? err.message : 'No se pudieron cargar los proveedores'))
  }

  useEffect(() => {
    cargar().finally(() => setCargando(false))
  }, [])

  function onEditar(p: ProveedorResponse) {
    setEditandoId(p.idProveedor)
    setForm({ nombre: p.nombre, contacto: p.contacto ?? '', telefono: p.telefono ?? '', email: p.email ?? '' })
  }

  function onCancelarEdicion() {
    setEditandoId(null)
    setForm(PROVEEDOR_VACIO)
    setErrorForm(null)
  }

  async function onGuardar(e: React.FormEvent) {
    e.preventDefault()
    setErrorForm(null)
    setGuardando(true)
    try {
      if (editandoId != null) {
        await actualizarProveedor(editandoId, form)
      } else {
        await crearProveedor(form)
      }
      setForm(PROVEEDOR_VACIO)
      setEditandoId(null)
      await cargar()
    } catch (err) {
      setErrorForm(err instanceof ApiRequestError ? err.message : 'No se pudo guardar el proveedor')
    } finally {
      setGuardando(false)
    }
  }

  async function onEliminar(id: number) {
    if (!window.confirm('¿Dar de baja este proveedor?')) return
    setEliminandoId(id)
    try {
      await eliminarProveedor(id)
      await cargar()
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo dar de baja el proveedor')
    } finally {
      setEliminandoId(null)
    }
  }

  return (
    <Modal title="Proveedores" onClose={onClose} wide>
      <form onSubmit={onGuardar}>
        <label>
          Nombre
          <input required value={form.nombre} onChange={(e) => setForm({ ...form, nombre: e.target.value })} />
        </label>
        <label>
          Contacto
          <input value={form.contacto} onChange={(e) => setForm({ ...form, contacto: e.target.value })} />
        </label>
        <label>
          Teléfono
          <input value={form.telefono} onChange={(e) => setForm({ ...form, telefono: e.target.value })} />
        </label>
        <label>
          Email
          <input type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
        </label>
        {errorForm && <p className="error">{errorForm}</p>}
        <button type="submit" disabled={guardando}>
          {guardando && <span className="spinner" />}
          {editandoId != null ? 'Guardar cambios' : 'Agregar proveedor'}
        </button>
        {editandoId != null && (
          <button type="button" onClick={onCancelarEdicion}>
            Cancelar
          </button>
        )}
      </form>

      {cargando && <p>Cargando...</p>}
      {error && <p className="error">{error}</p>}

      {!cargando && !error && (
        <table>
          <thead>
            <tr>
              <th>Nombre</th>
              <th>Contacto</th>
              <th>Teléfono</th>
              <th>Email</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {proveedores.map((p) => (
              <tr key={p.idProveedor}>
                <td>{p.nombre}</td>
                <td>{p.contacto}</td>
                <td>{p.telefono}</td>
                <td>{p.email}</td>
                <td>
                  <button type="button" onClick={() => onEditar(p)}>
                    Editar
                  </button>
                  <button
                    type="button"
                    onClick={() => onEliminar(p.idProveedor)}
                    disabled={eliminandoId === p.idProveedor}
                  >
                    {eliminandoId === p.idProveedor ? 'Eliminando...' : 'Dar de baja'}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </Modal>
  )
}
