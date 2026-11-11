import { useEffect, useState, type FormEvent } from 'react'
import { actualizarRecargo, getRecargos } from '../api/recargos'
import { ApiRequestError } from '../api/client'
import { formatoPesos } from '../utils/formato'
import { calcularRecargo, ETIQUETA_MEDIO_PAGO } from '../utils/recargo'
import type { RecargoResponse } from '../types/api'

function FilaRecargo({ recargo, onGuardado }: { recargo: RecargoResponse; onGuardado: (r: RecargoResponse) => void }) {
  const [valor, setValor] = useState(String(recargo.porcentaje))
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [ok, setOk] = useState(false)

  const porcentaje = Number(valor)
  const valido = valor !== '' && porcentaje >= 0 && porcentaje <= 90
  const ejemplo = valido ? 100 + calcularRecargo(100, porcentaje) : null

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setOk(false)
    setGuardando(true)
    try {
      onGuardado(await actualizarRecargo(recargo.medioPago, porcentaje))
      setOk(true)
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo guardar')
    } finally {
      setGuardando(false)
    }
  }

  return (
    <form className="reportes-tarjeta recargo-tarjeta" onSubmit={onSubmit}>
      <span>{ETIQUETA_MEDIO_PAGO[recargo.medioPago]}</span>
      <label>
        Comisión del posnet (%)
        <input
          type="number"
          min={0}
          max={90}
          step="0.01"
          value={valor}
          onChange={(e) => {
            setValor(e.target.value)
            setOk(false)
          }}
          required
        />
      </label>
      {ejemplo != null && (
        <small>
          Un producto de {formatoPesos(100)} se cobra {formatoPesos(ejemplo)} y quedan {formatoPesos(100)}.
        </small>
      )}
      {error && <p className="error">{error}</p>}
      {ok && <p className="resultado">Guardado.</p>}
      <button type="submit" disabled={guardando || !valido || porcentaje === recargo.porcentaje}>
        {guardando && <span className="spinner" />}
        {guardando ? 'Guardando...' : 'Guardar'}
      </button>
    </form>
  )
}

export function Recargos() {
  const [recargos, setRecargos] = useState<RecargoResponse[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    getRecargos()
      .then(setRecargos)
      .catch((err) => setError(err instanceof ApiRequestError ? err.message : 'No se pudieron cargar los recargos'))
  }, [])

  if (error) return <p className="error">{error}</p>
  if (!recargos) return <p>Cargando...</p>

  return (
    <div>
      <h2>Recargos de tarjeta</h2>
      <p className="caja-aclaracion">
        Es el % que se queda el posnet. En Cobros, al elegir crédito o débito, el sistema le cobra al cliente lo
        necesario para que, después de la comisión, quede el precio original. Caja, reportes y ganancias registran el
        precio original; el ticket y la factura salen por lo que pagó el cliente. Cambiar el % no modifica las ventas
        ya hechas.
      </p>
      <div className="reportes-tarjetas recargo-grilla">
        {recargos.map((r) => (
          <FilaRecargo
            key={r.medioPago}
            recargo={r}
            onGuardado={(nuevo) => setRecargos((prev) => prev?.map((x) => (x.medioPago === nuevo.medioPago ? nuevo : x)) ?? null)}
          />
        ))}
      </div>
    </div>
  )
}
