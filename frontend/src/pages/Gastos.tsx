import { useEffect, useState, type FormEvent } from 'react'
import { useAuth } from '../auth/AuthContext'
import { crearGasto, getGastos } from '../api/gastos'
import { ApiRequestError } from '../api/client'
import { EncabezadoPagina } from '../components/EncabezadoPagina'
import { Modal } from '../components/Modal'
import { hoyIso } from '../utils/date'
import { formatoPesos } from '../utils/formato'
import type { GastoRequest, GastoResponse, MedioPagoProveedor, OrigenPago } from '../types/api'

const GASTO_VACIO = {
  nombre: '',
  importe: '',
  fecha: hoyIso(),
  categoria: '',
  medioPago: '' as MedioPagoProveedor | '',
  origen: '' as OrigenPago | '',
}

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
  const [altaAbierta, setAltaAbierta] = useState(false)

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
    if (!sesion || !form.nombre || !form.importe || !form.fecha || !form.medioPago || !form.origen) return
    setErrorAlta(null)
    setAgregando(true)
    try {
      const req: GastoRequest = {
        idEmpleado: sesion.idEmpleado,
        nombre: form.nombre,
        importe: Number(form.importe),
        fecha: form.fecha,
        categoria: form.categoria || undefined,
        medioPago: form.medioPago,
        origen: form.origen,
      }
      await crearGasto(req)
      setForm(GASTO_VACIO)
      setAltaAbierta(false)
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
      <EncabezadoPagina
        titulo="Gastos"
        subtitulo={!cargando && !error ? `${gastos.length} gastos · ${formatoPesos(total)} en el período` : undefined}
        acciones={
          <button type="button" onClick={() => setAltaAbierta(true)}>
            + Agregar gasto
          </button>
        }
      />

      <section className="tarjeta productos-filtros">
        <form onSubmit={onBuscar} className="historial-fechas">
          <label>
            Desde
            <input type="date" value={desde} onChange={(e) => setDesde(e.target.value)} />
          </label>
          <label>
            Hasta
            <input type="date" max={hoyIso()} value={hasta} onChange={(e) => setHasta(e.target.value)} />
          </label>
          <button type="submit" disabled={cargando}>
            {cargando && <span className="spinner" aria-hidden="true" />}
            {cargando ? 'Buscando…' : 'Buscar'}
          </button>
        </form>
      </section>

      {error && <p className="alerta alerta-error">{error}</p>}

      {!error && (
        <section className="tarjeta productos-tabla">
          {cargando ? (
            <p className="cobros-vacio">Cargando…</p>
          ) : gastos.length === 0 ? (
            <p className="cobros-vacio">No hay gastos en ese período.</p>
          ) : (
            <div className="tabla-scroll">
              <table>
                <thead>
                  <tr>
                    <th>Fecha</th>
                    <th>Nombre</th>
                    <th>Categoría</th>
                    <th>Forma de pago</th>
                    <th>Origen</th>
                    <th className="celda-numero">Importe</th>
                    <th>Registrado por</th>
                  </tr>
                </thead>
                <tbody>
                  {gastos.map((g) => (
                    <tr key={g.idGasto}>
                      <td className="celda-codigo">{g.fecha.split('-').reverse().join('/')}</td>
                      <td className="texto-fuerte">{g.nombre}</td>
                      <td>{g.categoria || '—'}</td>
                      <td>{g.medioPago === 'EFECTIVO' ? 'Efectivo' : g.medioPago === 'TRANSFERENCIA' ? 'Transferencia' : '—'}</td>
                      <td>
                        {g.origen === 'CAJA' ? (
                          <span className="badge badge-aviso">Sale de la caja</span>
                        ) : g.origen === 'EXTERNO' ? (
                          <span className="badge">Externo</span>
                        ) : (
                          '—'
                        )}
                      </td>
                      <td className="celda-numero texto-fuerte">{formatoPesos(g.importe)}</td>
                      <td className="texto-apagado">{g.empleadoRegistroNombre}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      )}

      {altaAbierta && (
        <Modal title="Agregar gasto" onClose={() => setAltaAbierta(false)}>
          <form onSubmit={onAgregar} className="formulario-grilla">
            <label className="campo-ancho">
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
                inputMode="decimal"
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
            <label className="campo-ancho">
              Categoría (opcional)
              <input
                placeholder="Ej. Servicios"
                value={form.categoria}
                onChange={(e) => setForm({ ...form, categoria: e.target.value })}
              />
            </label>
            <label>
              Forma de pago
              <select
                required
                value={form.medioPago}
                onChange={(e) => setForm({ ...form, medioPago: e.target.value as MedioPagoProveedor })}
              >
                <option value="">Elegí…</option>
                <option value="EFECTIVO">Efectivo</option>
                <option value="TRANSFERENCIA">Transferencia</option>
              </select>
            </label>
            <label>
              ¿De dónde sale la plata?
              <select
                required
                value={form.origen}
                onChange={(e) => setForm({ ...form, origen: e.target.value as OrigenPago })}
              >
                <option value="">Elegí…</option>
                <option value="CAJA">De la caja</option>
                <option value="EXTERNO">De afuera (no toca la caja)</option>
              </select>
            </label>
            {errorAlta && <p className="alerta alerta-error campo-ancho">{errorAlta}</p>}
            <div className="formulario-acciones campo-ancho">
              <button type="button" className="btn-secundario" onClick={() => setAltaAbierta(false)}>
                Cancelar
              </button>
              <button type="submit" disabled={agregando}>
                {agregando && <span className="spinner" aria-hidden="true" />}
                {agregando ? 'Agregando…' : 'Agregar gasto'}
              </button>
            </div>
          </form>
        </Modal>
      )}
    </div>
  )
}
