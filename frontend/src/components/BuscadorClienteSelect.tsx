import { useEffect, useState } from 'react'
import { crearCliente, getClientes } from '../api/clientes'
import { ApiRequestError } from '../api/client'
import type { ClienteDocTipo, ClienteResponse } from '../types/api'

function etiquetaCliente(cliente: ClienteResponse): string {
  return cliente.docNro ? `${cliente.nombre} (${cliente.docNro})` : cliente.nombre
}

interface BuscadorClienteSelectProps {
  idClienteSeleccionado: number | null
  onSeleccionar: (cliente: ClienteResponse | null) => void
}

// Buscador + alta rápida de Cliente vía <datalist> nativo, mismo patrón que
// BuscadorProductoCarrito (Cobros/Trabajo a domicilio/Compras ya resuelven así la búsqueda sin
// una librería de autocompletado). Usado desde el flujo de Nota de Crédito.
export function BuscadorClienteSelect({ idClienteSeleccionado, onSeleccionar }: BuscadorClienteSelectProps) {
  const [clientes, setClientes] = useState<ClienteResponse[]>([])
  const [texto, setTexto] = useState('')
  const [mostrarAlta, setMostrarAlta] = useState(false)
  const [nombreNuevo, setNombreNuevo] = useState('')
  const [docTipoNuevo, setDocTipoNuevo] = useState<ClienteDocTipo>(99)
  const [docNroNuevo, setDocNroNuevo] = useState('')
  const [creando, setCreando] = useState(false)
  const [errorAlta, setErrorAlta] = useState<string | null>(null)

  useEffect(() => {
    getClientes().then(setClientes).catch(() => setClientes([]))
  }, [])

  const seleccionado = clientes.find((c) => c.idCliente === idClienteSeleccionado) ?? null

  function onCambiarTexto(valor: string) {
    setTexto(valor)
    const match = clientes.find((c) => etiquetaCliente(c) === valor)
    onSeleccionar(match ?? null)
  }

  async function onCrearCliente() {
    setErrorAlta(null)
    setCreando(true)
    try {
      const nuevo = await crearCliente({
        nombre: nombreNuevo,
        docTipo: docTipoNuevo,
        docNro: docTipoNuevo === 99 ? undefined : docNroNuevo,
      })
      setClientes((prev) => [...prev, nuevo])
      setTexto(etiquetaCliente(nuevo))
      onSeleccionar(nuevo)
      setMostrarAlta(false)
      setNombreNuevo('')
      setDocNroNuevo('')
    } catch (err) {
      setErrorAlta(err instanceof ApiRequestError ? err.message : 'No se pudo crear el cliente')
    } finally {
      setCreando(false)
    }
  }

  return (
    <div>
      <label>
        Cliente
        <input
          list="clientes-datalist"
          placeholder="Buscar cliente por nombre o documento"
          value={seleccionado ? etiquetaCliente(seleccionado) : texto}
          onChange={(e) => onCambiarTexto(e.target.value)}
        />
      </label>
      <datalist id="clientes-datalist">
        {clientes.map((c) => (
          <option key={c.idCliente} value={etiquetaCliente(c)} />
        ))}
      </datalist>
      <button type="button" onClick={() => setMostrarAlta((v) => !v)}>
        {mostrarAlta ? 'Cancelar' : '+ Cliente nuevo'}
      </button>

      {mostrarAlta && (
        <div>
          <label>
            Nombre
            <input value={nombreNuevo} onChange={(e) => setNombreNuevo(e.target.value)} />
          </label>
          <label>
            Tipo de documento
            <select
              value={docTipoNuevo}
              onChange={(e) => setDocTipoNuevo(Number(e.target.value) as ClienteDocTipo)}
            >
              <option value={99}>Sin documento</option>
              <option value={96}>DNI</option>
              <option value={80}>CUIT</option>
            </select>
          </label>
          {docTipoNuevo !== 99 && (
            <label>
              Número de documento
              <input value={docNroNuevo} onChange={(e) => setDocNroNuevo(e.target.value)} />
            </label>
          )}
          {errorAlta && <p className="error">{errorAlta}</p>}
          <button type="button" onClick={onCrearCliente} disabled={creando || !nombreNuevo}>
            {creando ? 'Creando...' : 'Crear y usar este cliente'}
          </button>
        </div>
      )}
    </div>
  )
}
