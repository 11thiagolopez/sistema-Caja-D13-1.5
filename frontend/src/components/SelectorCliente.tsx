import { useEffect, useState } from 'react'
import { buscarClientes } from '../api/clientes'
import { ApiRequestError } from '../api/client'
import { etiquetaDocumento, nombreCondicionIva } from '../utils/cuit'
import { FormularioCliente } from './FormularioCliente'
import type { ClienteResponse } from '../types/api'

const ESPERA_BUSQUEDA_MS = 250

interface SelectorClienteProps {
  value: ClienteResponse | null
  onChange: (cliente: ClienteResponse | null) => void
  // No se puede cambiar: la venta ya está en la cuenta corriente de ese cliente.
  bloqueado?: boolean
}

// Único selector de clientes del sistema. Busca en el servidor, y no sobre un listado cargado de
// antemano, porque el VENDEDOR no puede ver todos los clientes: solo los que busca.
export function SelectorCliente({ value, onChange, bloqueado }: SelectorClienteProps) {
  const [texto, setTexto] = useState('')
  const [resultados, setResultados] = useState<ClienteResponse[]>([])
  const [buscando, setBuscando] = useState(false)
  const [mostrarAlta, setMostrarAlta] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const consulta = texto.trim()

  useEffect(() => {
    if (!consulta) {
      setResultados([])
      return
    }
    let cancelado = false
    setBuscando(true)
    const espera = setTimeout(() => {
      buscarClientes(consulta)
        .then((clientes) => {
          if (cancelado) return
          setResultados(clientes)
          setError(null)
        })
        .catch((err) => {
          if (cancelado) return
          setError(err instanceof ApiRequestError ? err.message : 'No se pudo buscar')
        })
        .finally(() => {
          if (!cancelado) setBuscando(false)
        })
    }, ESPERA_BUSQUEDA_MS)
    return () => {
      cancelado = true
      clearTimeout(espera)
    }
  }, [consulta])

  function elegir(cliente: ClienteResponse | null) {
    onChange(cliente)
    setTexto('')
    setResultados([])
    setMostrarAlta(false)
  }

  if (value) {
    return (
      <div className="selector-cliente-elegido">
        <div>
          <strong>{value.nombre}</strong>
          <small>
            {etiquetaDocumento(value.docTipo, value.docNro)} · {nombreCondicionIva(value.condicionIva)}
          </small>
        </div>
        {!bloqueado && (
          <button type="button" className="boton-secundario" onClick={() => elegir(null)}>
            Cambiar
          </button>
        )}
      </div>
    )
  }

  if (mostrarAlta) {
    return (
      <FormularioCliente
        textoInicial={consulta}
        onGuardado={elegir}
        onCancelar={() => setMostrarAlta(false)}
        onSinDatos={() => elegir(null)}
      />
    )
  }

  const sinResultados = !!consulta && !buscando && !error && resultados.length === 0

  return (
    <div className="selector-cliente">
      <label>
        Cliente
        <input
          placeholder="Nombre, razón social, CUIT o DNI"
          value={texto}
          onChange={(e) => setTexto(e.target.value)}
          // Dentro de Cobros, un Enter acá no puede registrar la venta.
          onKeyDown={(e) => {
            if (e.key === 'Enter') e.preventDefault()
          }}
        />
      </label>
      {buscando && (
        <p>
          <span className="spinner" /> Buscando...
        </p>
      )}
      {error && <p className="error">{error}</p>}
      {resultados.length > 0 && (
        <ul className="selector-cliente-resultados">
          {resultados.map((c) => (
            <li key={c.idCliente}>
              <button type="button" onClick={() => elegir(c)}>
                <strong>{c.nombre}</strong>
                <small>{etiquetaDocumento(c.docTipo, c.docNro)}</small>
              </button>
            </li>
          ))}
        </ul>
      )}
      {sinResultados && <p>No hay clientes con "{consulta}".</p>}
      <button type="button" className="boton-secundario" onClick={() => setMostrarAlta(true)}>
        + Cliente nuevo
      </button>
    </div>
  )
}
