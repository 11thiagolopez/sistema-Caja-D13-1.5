import { useEffect, useState, type FormEvent } from 'react'
import { useAuth } from '../auth/AuthContext'
import { crearGasto, getGastos } from '../api/gastos'
import { ApiRequestError } from '../api/client'
import { hoyIso } from '../utils/date'
import type { GastoRequest, GastoResponse } from '../types/api'

const GASTO_VACIO = { nombre: '', importe: '', fecha: hoyIso(), categoria: '' }

export function Gastos() {
  const { sesion } = useAuth()
  const [gastos, setGastos] = useState<GastoResponse[]>([])
  const [desde, setDesde] = useState(hoyIso().slice(0, 8) + '01') // primer día del mes actual
  const [hasta, setHasta] = useState(hoyIso())
  const [error, setError] = useState<string | null>(null)
  const [cargando, setCargando] = useState(true)

  const [form, setForm] = useState(GASTO_VACIO)
  const [errorAlta, setErrorAlta] = useState<string | null>(null)
  const [agregando, setAgregando] = useState(false)

  function cargar() {
    setError(null)
    return getGastos(desde, hasta)
      .then(setGastos)
      .catch((err) => setError(err instanceof ApiRequestError ? err.message : 'No se pudieron cargar los gastos'))
  }

  useEffect(() => {
    cargar().finally(() => setCargando(false))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  function onBuscar(e: FormEvent) {
    e.preventDefault()
    setCargando(true)
    cargar().finally(() => setCargando(false))
  }

  async function onAgregar(e: FormEvent) {
    e.preventDefault()
    if (!sesion || !form.nombre || !form.importe || !form.fecha) return
    setErrorAlta(null)
    setAgregando(true)
    try {
      const req: GastoRequest = {
        idEmpleado: sesion.idEmpleado,
        nombre: form.nombre,
        importe: Number(form.importe),
        fecha: form.fecha,
        categoria: form.categoria || undefined,
      }
      await crearGasto(req)
      setForm(GASTO_VACIO)
      await cargar()
    } catch (err) {
      setErrorAlta(err instanceof ApiRequestError ? err.message : 'No se pudo agregar el gasto')
    } finally {
      setAgregando(false)
    }
  }

  const total = gastos.reduce((acc, g) => acc + g.importe, 0)

  return (
    <div>
      <h2>Gastos operativos</h2>

      <section>
        <h3>Agregar gasto</h3>
        <form onSubmit={onAgregar}>
          <label>
            Nombre
            <input
              required
              placeholder="Ej. Pago de luz"
              value={form.nombre}
              onChange={(e) => setForm({ ...form, nombre: e.target.value })}
            />
          </label>
          <label>
            Importe
            <input
              type="number"
              min={0.01}
              step="0.01"
              required
              value={form.importe}
              onChange={(e) => setForm({ ...form, importe: e.target.value })}
            />
          </label>
          <label>
            Fecha
            <input
              type="date"
              required
              max={hoyIso()}
              value={form.fecha}
              onChange={(e) => setForm({ ...form, fecha: e.target.value })}
            />
          </label>
          <label>
            Categoría (opcional)
            <input value={form.categoria} onChange={(e) => setForm({ ...form, categoria: e.target.value })} />
          </label>
          {errorAlta && <p className="error">{errorAlta}</p>}
          <button type="submit" disabled={agregando}>
            {agregando && <span className="spinner" />}
            {agregando ? 'Agregando...' : 'Agregar gasto'}
          </button>
        </form>
      </section>

      <section>
        <h3>Consultar</h3>
        <form onSubmit={onBuscar}>
          <label>
            Desde
            <input type="date" value={desde} onChange={(e) => setDesde(e.target.value)} />
          </label>
          <label>
            Hasta
            <input type="date" max={hoyIso()} value={hasta} onChange={(e) => setHasta(e.target.value)} />
          </label>
          <button type="submit">Buscar</button>
        </form>

        {cargando && <p>Cargando...</p>}
        {error && <p className="error">{error}</p>}

        {!cargando && !error && (
          <>
            <table>
              <thead>
                <tr>
                  <th>Fecha</th>
                  <th>Nombre</th>
                  <th>Categoría</th>
                  <th>Importe</th>
                  <th>Registrado por</th>
                </tr>
              </thead>
              <tbody>
                {gastos.map((g) => (
                  <tr key={g.idGasto}>
                    <td>{g.fecha}</td>
                    <td>{g.nombre}</td>
                    <td>{g.categoria}</td>
                    <td>{g.importe.toFixed(2)}</td>
                    <td>{g.empleadoRegistroNombre}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <p>
              <strong>Total del período: {total.toFixed(2)}</strong>
            </p>
          </>
        )}
      </section>
    </div>
  )
}
