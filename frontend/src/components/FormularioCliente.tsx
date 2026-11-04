import { useEffect, useState } from 'react'
import { actualizarCliente, consultarPadron, crearCliente } from '../api/clientes'
import { ApiRequestError } from '../api/client'
import { CONDICIONES_FORMULARIO, CONSUMIDOR_FINAL, esCuitValido, nombreCondicionIva } from '../utils/cuit'
import type { ClienteDocTipo, ClienteResponse } from '../types/api'

interface FormularioClienteProps {
  // Con cliente, edita. Sin cliente, da de alta prellenando con lo que se venía buscando.
  cliente?: ClienteResponse | null
  textoInicial?: string
  onGuardado: (cliente: ClienteResponse) => void
  onCancelar: () => void
  // Consumidor Final sin ningún dato: no se guarda un cliente vacío, sigue sin cliente.
  onSinDatos?: () => void
}

// Sin <form> a propósito: se despliega dentro del formulario de Cobros, y un <form> anidado no es
// HTML válido. La condición frente al IVA va primero porque define qué datos son obligatorios.
export function FormularioCliente({ cliente, textoInicial = '', onGuardado, onCancelar, onSinDatos }: FormularioClienteProps) {
  const soloDigitos = /^\d+$/.test(textoInicial)
  const [condicionIva, setCondicionIva] = useState<number | null>(cliente ? cliente.condicionIva : CONSUMIDOR_FINAL)
  const [docTipo, setDocTipo] = useState<ClienteDocTipo>(
    cliente?.docTipo ?? (soloDigitos ? (textoInicial.length === 11 ? 80 : 96) : 99),
  )
  const [docNro, setDocNro] = useState(cliente?.docNro ?? (soloDigitos ? textoInicial : ''))
  const [nombre, setNombre] = useState(cliente?.nombre ?? (soloDigitos ? '' : textoInicial))
  const [telefono, setTelefono] = useState(cliente?.telefono ?? '')
  const [email, setEmail] = useState(cliente?.email ?? '')
  const [direccion, setDireccion] = useState(cliente?.direccion ?? '')
  const [avisoPadron, setAvisoPadron] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [guardando, setGuardando] = useState(false)

  const esContribuyente = condicionIva != null && condicionIva !== CONSUMIDOR_FINAL
  const docTipoEfectivo: ClienteDocTipo = esContribuyente ? 80 : docTipo
  const cuitInvalido = docTipoEfectivo === 80 && docNro.length === 11 && !esCuitValido(docNro)

  // Solo al dar de alta: al editar no se pisa lo que ya estaba cargado.
  const cuitParaConsultar = !cliente && docTipoEfectivo === 80 && esCuitValido(docNro) ? docNro : null

  useEffect(() => {
    if (!cuitParaConsultar) return
    let cancelado = false
    setAvisoPadron('Consultando ARCA...')
    consultarPadron(cuitParaConsultar)
      .then((contribuyente) => {
        if (cancelado) return
        setNombre((actual) => actual || contribuyente.nombre)
        setDireccion((actual) => actual || (contribuyente.domicilio ?? ''))
        if (contribuyente.condicionIva != null) {
          setCondicionIva((actual) => (actual === CONSUMIDOR_FINAL ? actual : contribuyente.condicionIva))
        }
        setAvisoPadron(contribuyente.advertencia ? `ARCA informa: ${contribuyente.advertencia}` : null)
      })
      .catch((err) => {
        if (cancelado) return
        const motivo = err instanceof ApiRequestError ? err.message : 'No se pudo consultar ARCA'
        setAvisoPadron(`${motivo}. Podés cargar los datos a mano.`)
      })
    return () => {
      cancelado = true
      setAvisoPadron(null)
    }
  }, [cuitParaConsultar])

  const opciones = CONDICIONES_FORMULARIO.some((c) => c.id === condicionIva) || condicionIva == null
    ? CONDICIONES_FORMULARIO
    : [...CONDICIONES_FORMULARIO, { id: condicionIva, nombre: nombreCondicionIva(condicionIva) }]

  function validar(): string | null {
    if (condicionIva == null) return 'Elegí la condición frente al IVA'
    if (esContribuyente && !nombre.trim()) return 'La razón social es obligatoria'
    if (docTipoEfectivo === 80 && !esCuitValido(docNro)) return 'El CUIT no es válido, revisá los números'
    if (docTipoEfectivo === 96 && docNro.length < 7) return 'El DNI no es válido, revisá los números'
    return null
  }

  async function onGuardar() {
    const sinDatos = !esContribuyente && docTipoEfectivo === 99 && !nombre.trim()
    if (!cliente && sinDatos && onSinDatos) {
      onSinDatos()
      return
    }
    const invalido = validar()
    if (invalido) {
      setError(invalido)
      return
    }
    setError(null)
    setGuardando(true)
    try {
      const datos = {
        nombre: nombre.trim(),
        docTipo: docTipoEfectivo,
        docNro: docTipoEfectivo === 99 ? undefined : docNro,
        condicionIva,
        telefono,
        email,
        direccion,
      }
      onGuardado(cliente ? await actualizarCliente(cliente.idCliente, datos) : await crearCliente(datos))
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo guardar el cliente')
    } finally {
      setGuardando(false)
    }
  }

  return (
    <div
      className="formulario-cliente"
      // Dentro de Cobros, un Enter acá no puede registrar la venta.
      onKeyDown={(e) => {
        if (e.key === 'Enter' && e.target instanceof HTMLInputElement) e.preventDefault()
      }}
    >
      <label>
        Condición frente al IVA
        <select
          value={condicionIva ?? ''}
          onChange={(e) => setCondicionIva(e.target.value === '' ? null : Number(e.target.value))}
        >
          {condicionIva == null && <option value="">Elegí...</option>}
          {opciones.map((c) => (
            <option key={c.id} value={c.id}>
              {c.nombre}
            </option>
          ))}
        </select>
      </label>

      {!esContribuyente && (
        <label>
          Documento (opcional)
          <select value={docTipo} onChange={(e) => setDocTipo(Number(e.target.value) as ClienteDocTipo)}>
            <option value={99}>Sin documento</option>
            <option value={96}>DNI</option>
            <option value={80}>CUIT</option>
          </select>
        </label>
      )}
      {docTipoEfectivo !== 99 && (
        <label>
          {docTipoEfectivo === 80 ? 'CUIT (sin guiones)' : 'DNI'}
          <input
            inputMode="numeric"
            maxLength={docTipoEfectivo === 80 ? 11 : 8}
            value={docNro}
            onChange={(e) => {
              setError(null)
              setDocNro(e.target.value.replace(/\D/g, ''))
            }}
          />
        </label>
      )}
      {cuitInvalido && <p className="error">El CUIT no es válido, revisá los números.</p>}
      {avisoPadron && <p>{avisoPadron}</p>}

      <label>
        {esContribuyente ? 'Razón social' : 'Nombre (opcional)'}
        <input value={nombre} onChange={(e) => setNombre(e.target.value)} />
      </label>
      <label>
        Email
        <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
      </label>
      <label>
        Teléfono
        <input value={telefono} onChange={(e) => setTelefono(e.target.value)} />
      </label>
      <label>
        Dirección
        <input value={direccion} onChange={(e) => setDireccion(e.target.value)} />
      </label>

      {error && <p className="error">{error}</p>}
      <div className="comprobante-acciones">
        <button type="button" onClick={onGuardar} disabled={guardando}>
          {guardando && <span className="spinner" />}
          {guardando ? 'Guardando...' : cliente ? 'Guardar cambios' : 'Guardar y usar'}
        </button>
        <button type="button" className="boton-secundario" onClick={onCancelar}>
          Cancelar
        </button>
      </div>
    </div>
  )
}
