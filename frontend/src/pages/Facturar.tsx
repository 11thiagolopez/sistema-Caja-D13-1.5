import { useEffect, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { BuscadorClienteSelect } from '../components/BuscadorClienteSelect'
import { consultarPadron, getClientes } from '../api/clientes'
import { descargarPdfFactura, facturarVenta, getFactura } from '../api/facturas'
import { getVenta, getVentas } from '../api/ventas'
import { ApiRequestError } from '../api/client'
import { hoyIso } from '../utils/date'
import { CONDICIONES_IVA, CONSUMIDOR_FINAL, esCuitValido } from '../utils/cuit'
import type { ClienteDocTipo, ClienteResponse, FacturaFiscalResponse, VentaResponse } from '../types/api'

interface VentaFacturable {
  venta: VentaResponse
  factura: FacturaFiscalResponse | null
}

// Flujo como el de ARCA: se elige un cliente guardado o se tipea el documento y se autocompleta el
// nombre (primero del catálogo, después del padrón). Después se elige la venta y se emite.
export function Facturar() {
  const [params] = useSearchParams()
  const idVentaInicial = Number(params.get('idVenta')) || null

  const [clientes, setClientes] = useState<ClienteResponse[]>([])
  const [idCliente, setIdCliente] = useState<number | null>(null)
  const [docTipo, setDocTipo] = useState<ClienteDocTipo>(80)
  const [docNro, setDocNro] = useState('')
  const [nombre, setNombre] = useState('')
  const [condicionIva, setCondicionIva] = useState<number | ''>('')
  const [consultando, setConsultando] = useState(false)
  const [aviso, setAviso] = useState<string | null>(null)
  const [errorPadron, setErrorPadron] = useState<string | null>(null)

  const [desde, setDesde] = useState(hoyIso().slice(0, 8) + '01')
  const [hasta, setHasta] = useState(hoyIso())
  const [ventas, setVentas] = useState<VentaFacturable[]>([])
  const [idVenta, setIdVenta] = useState<number | null>(idVentaInicial)
  const [buscando, setBuscando] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [facturando, setFacturando] = useState(false)
  const [emitida, setEmitida] = useState<FacturaFiscalResponse | null>(null)
  const [abriendoPdf, setAbriendoPdf] = useState(false)

  async function buscarVentas(event?: FormEvent) {
    event?.preventDefault()
    setError(null)
    setBuscando(true)
    try {
      const resultado = (await getVentas(desde, hasta)).filter((v) => v.estado === 'CONFIRMADA')
      if (idVentaInicial && !resultado.some((v) => v.idVenta === idVentaInicial)) {
        const inicial = await getVenta(idVentaInicial)
        if (inicial.estado === 'CONFIRMADA') resultado.unshift(inicial)
      }
      const conFactura = await Promise.all(
        resultado.map(async (venta) => ({ venta, factura: await getFactura(venta.idVenta) })),
      )
      setVentas(conFactura.filter((v) => v.factura?.estado !== 'EMITIDA'))
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudieron cargar las ventas')
    } finally {
      setBuscando(false)
    }
  }

  useEffect(() => {
    getClientes().then(setClientes).catch(() => setClientes([]))
    buscarVentas()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (docTipo === 99 || !docNro) return
    const guardado = clientes.find((c) => c.docTipo === docTipo && c.docNro === docNro)
    if (guardado) {
      setNombre((actual) => actual || guardado.nombre)
      if (guardado.condicionIva != null) setCondicionIva(guardado.condicionIva)
    }
    if (docTipo !== 80 || !esCuitValido(docNro) || guardado?.condicionIva != null) return

    let cancelado = false
    setConsultando(true)
    consultarPadron(docNro)
      .then((contribuyente) => {
        if (cancelado) return
        setNombre((actual) => actual || contribuyente.nombre)
        if (contribuyente.condicionIva != null) setCondicionIva(contribuyente.condicionIva)
        setAviso(contribuyente.advertencia)
      })
      .catch((err) => {
        if (cancelado) return
        const motivo = err instanceof ApiRequestError ? err.message : 'No se pudo consultar ARCA'
        setErrorPadron(`${motivo}. Podés cargar los datos a mano.`)
      })
      .finally(() => {
        if (!cancelado) setConsultando(false)
      })
    return () => {
      cancelado = true
      setConsultando(false)
    }
  }, [docTipo, docNro, clientes])

  function limpiarDatosAutocompletados() {
    setNombre('')
    setCondicionIva('')
    setAviso(null)
    setErrorPadron(null)
  }

  function onCambiarDocTipo(valor: ClienteDocTipo) {
    setIdCliente(null)
    setDocTipo(valor)
    setDocNro('')
    limpiarDatosAutocompletados()
  }

  function onCambiarDocNro(valor: string) {
    setIdCliente(null)
    setDocNro(valor.replace(/\D/g, ''))
    limpiarDatosAutocompletados()
  }

  function onSeleccionarCliente(cliente: ClienteResponse | null) {
    setIdCliente(cliente?.idCliente ?? null)
    if (!cliente) return
    limpiarDatosAutocompletados()
    setDocTipo(cliente.docTipo)
    setDocNro(cliente.docNro ?? '')
    setNombre(cliente.docTipo === 99 ? '' : cliente.nombre)
    setCondicionIva(cliente.condicionIva ?? '')
  }

  const cuitInvalido = docTipo === 80 && docNro.length === 11 && !esCuitValido(docNro)
  const receptorCompleto =
    docTipo === 99 ||
    (!!nombre && !!docNro && !cuitInvalido && (docTipo === 96 ? docNro.length >= 7 : esCuitValido(docNro) && condicionIva !== ''))
  const seleccionada = ventas.find((v) => v.venta.idVenta === idVenta) ?? null

  async function onFacturar() {
    if (!seleccionada || !receptorCompleto) return
    const receptor = docTipo === 99 ? 'Consumidor Final' : `${nombre} (${docNro})`
    const total = seleccionada.venta.totalVenta.toFixed(2)
    const pregunta = `¿Emitir Factura C a ${receptor} por $${total}? Es un comprobante real ante ARCA y no se puede deshacer.`
    if (!window.confirm(pregunta)) return

    setError(null)
    setEmitida(null)
    setFacturando(true)
    try {
      const factura = await facturarVenta(seleccionada.venta.idVenta, {
        clienteDocTipo: docTipo,
        clienteDocNro: docTipo === 99 ? undefined : docNro,
        clienteNombre: docTipo === 99 ? undefined : nombre,
        condicionIvaReceptor: docTipo === 99 ? CONSUMIDOR_FINAL : condicionIva === '' ? undefined : condicionIva,
      })
      if (factura.estado === 'EMITIDA') {
        setEmitida(factura)
        setVentas((actual) => actual.filter((v) => v.venta.idVenta !== factura.idVenta))
        setIdVenta(null)
        getClientes().then(setClientes).catch(() => undefined)
      } else {
        setVentas((actual) => actual.map((v) => (v.venta.idVenta === factura.idVenta ? { ...v, factura } : v)))
        setError(factura.errorDetalle ?? 'ARCA no autorizó el comprobante')
      }
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo facturar la venta')
    } finally {
      setFacturando(false)
    }
  }

  async function onVerPdf(id: number) {
    setAbriendoPdf(true)
    try {
      await descargarPdfFactura(id)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo abrir el PDF')
    } finally {
      setAbriendoPdf(false)
    }
  }

  return (
    <div>
      <h2>Facturar</h2>

      <section>
        <h3>1. Cliente</h3>
        <BuscadorClienteSelect idClienteSeleccionado={idCliente} onSeleccionar={onSeleccionarCliente} />
        <label>
          Tipo de documento
          <select value={docTipo} onChange={(e) => onCambiarDocTipo(Number(e.target.value) as ClienteDocTipo)}>
            <option value={80}>CUIT</option>
            <option value={96}>DNI</option>
            <option value={99}>Consumidor Final</option>
          </select>
        </label>
        {docTipo !== 99 && (
          <>
            <label>
              {docTipo === 80 ? 'CUIT (sin guiones)' : 'DNI'}
              <input
                inputMode="numeric"
                maxLength={docTipo === 80 ? 11 : 8}
                value={docNro}
                onChange={(e) => onCambiarDocNro(e.target.value)}
              />
            </label>
            {cuitInvalido && <p className="error">El CUIT no es válido, revisá los números.</p>}
            {consultando && (
              <p>
                <span className="spinner" /> Consultando ARCA...
              </p>
            )}
            {errorPadron && <p className="error">{errorPadron}</p>}
            {aviso && <p className="error">ARCA informa: {aviso}</p>}
            <label>
              Nombre / Razón social
              <input value={nombre} onChange={(e) => setNombre(e.target.value)} />
            </label>
            <label>
              Condición frente al IVA{docTipo === 96 ? ' (si no elegís, Consumidor Final)' : ''}
              <select
                value={condicionIva}
                onChange={(e) => setCondicionIva(e.target.value === '' ? '' : Number(e.target.value))}
              >
                <option value="">Elegí...</option>
                {CONDICIONES_IVA.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.nombre}
                  </option>
                ))}
              </select>
            </label>
          </>
        )}
      </section>

      <section>
        <h3>2. Venta</h3>
        <form onSubmit={buscarVentas}>
          <label>
            Desde
            <input type="date" value={desde} onChange={(e) => setDesde(e.target.value)} />
          </label>
          <label>
            Hasta
            <input type="date" value={hasta} onChange={(e) => setHasta(e.target.value)} />
          </label>
          <button type="submit" disabled={buscando}>
            {buscando && <span className="spinner" />}
            {buscando ? 'Buscando...' : 'Buscar'}
          </button>
        </form>
        <table>
          <thead>
            <tr>
              <th />
              <th>#</th>
              <th>Fecha</th>
              <th>Medio de pago</th>
              <th>Total</th>
              <th>Último intento</th>
            </tr>
          </thead>
          <tbody>
            {!buscando && ventas.length === 0 && (
              <tr>
                <td colSpan={6}>No hay ventas sin facturar en este período.</td>
              </tr>
            )}
            {ventas.map(({ venta, factura }) => (
              <tr key={venta.idVenta}>
                <td>
                  <input
                    type="radio"
                    name="venta"
                    aria-label={`Elegir venta ${venta.idVenta}`}
                    checked={idVenta === venta.idVenta}
                    onChange={() => setIdVenta(venta.idVenta)}
                  />
                </td>
                <td>{venta.idVenta}</td>
                <td>{venta.fecha}</td>
                <td>{venta.medioPago}</td>
                <td>{venta.totalVenta.toFixed(2)}</td>
                <td>{factura?.estado === 'ERROR' ? `Error: ${factura.errorDetalle}` : '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      <section>
        <h3>3. Emitir</h3>
        {error && <p className="error">{error}</p>}
        <button type="button" onClick={onFacturar} disabled={facturando || !seleccionada || !receptorCompleto}>
          {facturando && <span className="spinner" />}
          {facturando
            ? 'Facturando...'
            : seleccionada?.factura?.estado === 'ERROR'
              ? 'Reintentar factura'
              : 'Emitir factura'}
        </button>
        {emitida && (
          <div className="resultado">
            <p>
              Factura Nº {String(emitida.puntoVenta).padStart(4, '0')}-{String(emitida.numero).padStart(8, '0')} emitida
              {emitida.clienteNombre ? ` a ${emitida.clienteNombre}` : ''}. CAE {emitida.cae}
            </p>
            <button type="button" onClick={() => onVerPdf(emitida.idVenta)} disabled={abriendoPdf}>
              {abriendoPdf ? 'Abriendo...' : 'Ver PDF'}
            </button>
          </div>
        )}
      </section>
    </div>
  )
}
