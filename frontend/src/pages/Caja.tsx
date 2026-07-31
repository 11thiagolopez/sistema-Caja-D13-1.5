import { useEffect, useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import { abrirCaja, cerrarCaja, confirmarRetiro, getResumenDia, solicitarRetiro } from '../api/caja'
import { ApiRequestError } from '../api/client'
import type { MedioPago, ResumenDiaResponse } from '../types/api'

export function Caja() {
  const { sesion } = useAuth()
  const esAdmin = sesion?.rol === 'ADMIN'
  const [resumen, setResumen] = useState<ResumenDiaResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [montoInicial, setMontoInicial] = useState('')
  const [monto, setMonto] = useState('')
  const [motivo, setMotivo] = useState('')
  const [medioPago, setMedioPago] = useState<MedioPago>('EFECTIVO')
  const [mensajeSolicitud, setMensajeSolicitud] = useState<string | null>(null)
  const [idSolicitud, setIdSolicitud] = useState('')
  const [codigo, setCodigo] = useState('')
  const [abriendo, setAbriendo] = useState(false)
  const [cerrando, setCerrando] = useState(false)
  const [solicitando, setSolicitando] = useState(false)
  const [confirmando, setConfirmando] = useState(false)

  function cargarResumen() {
    setError(null)
    getResumenDia()
      .then(setResumen)
      .catch((err) =>
        setError(err instanceof ApiRequestError ? err.message : 'No se pudo cargar el resumen del día'),
      )
  }

  // VENDEDOR no debe ni disparar esta llamada: GET /api/caja/resumen-dia es exclusivo de ADMIN.
  useEffect(() => {
    if (esAdmin) cargarResumen()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [esAdmin])

  async function onAbrirCaja() {
    if (!sesion || !montoInicial) return
    setError(null)
    setAbriendo(true)
    try {
      await abrirCaja({ idEmpleado: sesion.idEmpleado, montoInicial: Number(montoInicial) })
      setMontoInicial('')
      if (esAdmin) cargarResumen()
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo abrir la caja')
    } finally {
      setAbriendo(false)
    }
  }

  async function onCerrarCaja() {
    setError(null)
    setCerrando(true)
    try {
      await cerrarCaja()
      if (esAdmin) cargarResumen()
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo cerrar la caja')
    } finally {
      setCerrando(false)
    }
  }

  async function onSolicitarRetiro() {
    if (!sesion || !monto || !motivo) return
    setError(null)
    setMensajeSolicitud(null)
    setSolicitando(true)
    try {
      const solicitud = await solicitarRetiro({
        idEmpleado: sesion.idEmpleado,
        monto: Number(monto),
        motivo,
        medioPago,
      })
      setMensajeSolicitud(
        `Retiro solicitado (#${solicitud.idSolicitud}). Se envió un código de autorización a los administradores.`,
      )
      // Precargamos el id de solicitud abajo (sigue editable): quien pidió el retiro es
      // normalmente quien lo va a confirmar apenas el admin le pase el código.
      setIdSolicitud(String(solicitud.idSolicitud))
      setMonto('')
      setMotivo('')
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo solicitar el retiro')
    } finally {
      setSolicitando(false)
    }
  }

  async function onConfirmarRetiro() {
    if (!idSolicitud || !codigo) return
    setError(null)
    setConfirmando(true)
    try {
      await confirmarRetiro({ idSolicitud: Number(idSolicitud), codigo })
      setIdSolicitud('')
      setCodigo('')
      if (esAdmin) cargarResumen()
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo confirmar el retiro')
    } finally {
      setConfirmando(false)
    }
  }

  return (
    <div>
      <h2>Caja</h2>
      {error && <p className="error">{error}</p>}

      <section>
        <h3>Sesión de caja</h3>
        <input
          type="number"
          placeholder="Monto inicial"
          value={montoInicial}
          onChange={(e) => setMontoInicial(e.target.value)}
        />
        <button type="button" onClick={onAbrirCaja} disabled={abriendo || !montoInicial}>
          {abriendo && <span className="spinner" />}
          {abriendo ? 'Abriendo...' : 'Abrir caja'}
        </button>
        <button type="button" onClick={onCerrarCaja} disabled={cerrando}>
          {cerrando && <span className="spinner" />}
          {cerrando ? 'Cerrando...' : 'Cerrar caja'}
        </button>
      </section>

      <section>
        <h3>Solicitar retiro</h3>
        <input
          type="number"
          placeholder="Monto"
          value={monto}
          onChange={(e) => setMonto(e.target.value)}
        />
        <input placeholder="Motivo" value={motivo} onChange={(e) => setMotivo(e.target.value)} />
        <select value={medioPago} onChange={(e) => setMedioPago(e.target.value as MedioPago)}>
          <option value="EFECTIVO">Efectivo</option>
          <option value="TRANSFERENCIA">Transferencia</option>
          <option value="TARJETA">Tarjeta</option>
        </select>
        <button type="button" onClick={onSolicitarRetiro} disabled={solicitando || !monto || !motivo}>
          {solicitando && <span className="spinner" />}
          {solicitando ? 'Solicitando...' : 'Solicitar'}
        </button>
        {mensajeSolicitud && <p className="resultado">{mensajeSolicitud}</p>}
      </section>

      <section>
        <h3>Confirmar retiro</h3>
        <p>
          El código de autorización le llega por email al ADMIN — pedíselo (llamada, WhatsApp,
          etc.) y escribilo acá para terminar el retiro. El n° de solicitud también está en el
          asunto de ese email ("solicitud #...").
        </p>
        <input
          type="number"
          placeholder="N° de solicitud"
          value={idSolicitud}
          onChange={(e) => setIdSolicitud(e.target.value)}
        />
        <input placeholder="Código" value={codigo} onChange={(e) => setCodigo(e.target.value)} />
        <button type="button" onClick={onConfirmarRetiro} disabled={confirmando || !idSolicitud || !codigo}>
          {confirmando && <span className="spinner" />}
          {confirmando ? 'Confirmando...' : 'Confirmar retiro'}
        </button>
      </section>

      {esAdmin && resumen && (
        <section>
          <h3>Resumen del día</h3>
          <ul>
            <li>Monto inicial: {resumen.montoInicial.toFixed(2)}</li>
            <li>Ventas efectivo: {resumen.ventasEfectivo.toFixed(2)}</li>
            <li>Ventas transferencia: {resumen.ventasTransferencia.toFixed(2)}</li>
            <li>Ventas tarjeta: {resumen.ventasTarjeta.toFixed(2)}</li>
            <li>Retiros efectivo: {resumen.retirosEfectivo.toFixed(2)}</li>
            <li>Retiros transferencia: {resumen.retirosTransferencia.toFixed(2)}</li>
            <li>
              <strong>Efectivo final: {resumen.efectivoFinal.toFixed(2)}</strong>
            </li>
            <li>Total digital: {resumen.totalDigital.toFixed(2)}</li>
            <li>
              <strong>Caja total del día: {resumen.cajaTotalDelDia.toFixed(2)}</strong>
            </li>
          </ul>
        </section>
      )}
    </div>
  )
}
