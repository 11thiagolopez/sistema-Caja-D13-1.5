import { Fragment, useEffect, useState, type FormEvent } from 'react'
import { useAuth } from '../auth/AuthContext'
import { getProductos } from '../api/productos'
import { getMarcas } from '../api/marcas'
import { getProveedores } from '../api/proveedores'
import { registrarCompra } from '../api/compras'
import { getCotizacionActual } from '../api/cotizacion'
import { ApiRequestError } from '../api/client'
import { hoyIso } from '../utils/date'
import type { CompraItemRequest, MarcaResponse, MedioPago, Producto, ProveedorResponse } from '../types/api'

type Moneda = 'ARS' | 'USD'

function etiquetaProducto(producto: Producto): string {
  return `${producto.descripcion} - ${producto.marca ?? 'sin marca'} (${producto.codigoInterno})`
}

// El valor canónico de precioCompraUnitario/precioVentaUnitario siempre es pesos, igual que lo
// que espera el backend (CompraItemRequest) — monedaCompra/monedaVenta son puramente de UI, para
// mostrar/tipear ese mismo valor en USD. pesosDesde/formatearEnMoneda son las dos únicas
// conversiones: tipear no convierte nada (se guarda crudo), solo cambiar el <select> de moneda
// dispara una conversión puntual.
function pesosDesde(texto: string, moneda: Moneda, cotizacion: number): number {
  const numero = Number(texto) || 0
  return moneda === 'USD' ? numero * cotizacion : numero
}

function formatearEnMoneda(pesos: number, moneda: Moneda, cotizacion: number): string {
  const valor = moneda === 'USD' ? pesos / cotizacion : pesos
  return valor.toFixed(2)
}

interface FilaCompra {
  id: number
  texto: string
  cantidad: string
  precioCompraUnitario: string
  monedaCompra: Moneda
  gananciaPct: string
  precioVentaUnitario: string
  monedaVenta: Moneda
  nuevoRubro: string
  nuevoFamilia: string
  nuevoMarca: string
  nuevoDescripcion: string
  nuevoCodigoFabrica: string
}

let proximoId = 1
function filaVacia(): FilaCompra {
  return {
    id: proximoId++,
    texto: '',
    cantidad: '1',
    precioCompraUnitario: '',
    monedaCompra: 'ARS',
    gananciaPct: '',
    precioVentaUnitario: '',
    monedaVenta: 'ARS',
    nuevoRubro: '',
    nuevoFamilia: '',
    nuevoMarca: '',
    nuevoDescripcion: '',
    nuevoCodigoFabrica: '',
  }
}

export function ComprasNueva() {
  const { sesion } = useAuth()
  const [productos, setProductos] = useState<Producto[]>([])
  const [marcas, setMarcas] = useState<MarcaResponse[]>([])
  const [proveedores, setProveedores] = useState<ProveedorResponse[]>([])
  const [cotizacion, setCotizacion] = useState<number | null>(null)

  const [fecha, setFecha] = useState(hoyIso())
  const [proveedorNombre, setProveedorNombre] = useState('')
  const [medioPago, setMedioPago] = useState<MedioPago>('EFECTIVO')
  const [filas, setFilas] = useState<FilaCompra[]>([filaVacia()])

  const [error, setError] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)
  const [mensaje, setMensaje] = useState<string | null>(null)

  useEffect(() => {
    getProductos().then(setProductos)
    getMarcas().then(setMarcas)
    getProveedores().then(setProveedores)
    getCotizacionActual().then((c) => setCotizacion(c?.valorVenta ?? null))
  }, [])

  function productoDeFila(fila: FilaCompra): Producto | undefined {
    return productos.find((p) => etiquetaProducto(p) === fila.texto)
  }

  function actualizarFila(id: number, cambios: Partial<FilaCompra>) {
    setFilas((actual) => actual.map((f) => (f.id === id ? { ...f, ...cambios } : f)))
  }

  function agregarFila() {
    setFilas((actual) => [...actual, filaVacia()])
  }

  function quitarFila(id: number) {
    setFilas((actual) => (actual.length > 1 ? actual.filter((f) => f.id !== id) : actual))
  }

  // Recalcula precio venta a partir de % de ganancia (recargo sobre el costo: 100% = vender al
  // doble). Se llama después de tocar precio compra, moneda de compra o % de ganancia — si el %
  // está vacío no hace nada (venta queda editable a mano, como siempre).
  function recalcularVenta(filaActualizada: FilaCompra): Partial<FilaCompra> {
    const pct = Number(filaActualizada.gananciaPct)
    if (!filaActualizada.gananciaPct || Number.isNaN(pct) || cotizacion == null) return {}
    const pesosCompra = pesosDesde(filaActualizada.precioCompraUnitario, filaActualizada.monedaCompra, cotizacion)
    const pesosVenta = pesosCompra * (1 + pct / 100)
    return { precioVentaUnitario: formatearEnMoneda(pesosVenta, filaActualizada.monedaVenta, cotizacion) }
  }

  function onCambiarPrecioCompra(fila: FilaCompra, texto: string) {
    const actualizada = { ...fila, precioCompraUnitario: texto }
    actualizarFila(fila.id, { precioCompraUnitario: texto, ...recalcularVenta(actualizada) })
  }

  function onCambiarGananciaPct(fila: FilaCompra, texto: string) {
    const actualizada = { ...fila, gananciaPct: texto }
    actualizarFila(fila.id, { gananciaPct: texto, ...recalcularVenta(actualizada) })
  }

  function onCambiarMonedaCompra(fila: FilaCompra, nuevaMoneda: Moneda) {
    if (cotizacion == null) return
    const nuevoTexto = fila.precioCompraUnitario
      ? formatearEnMoneda(pesosDesde(fila.precioCompraUnitario, fila.monedaCompra, cotizacion), nuevaMoneda, cotizacion)
      : ''
    const actualizada = { ...fila, monedaCompra: nuevaMoneda, precioCompraUnitario: nuevoTexto }
    actualizarFila(fila.id, {
      monedaCompra: nuevaMoneda,
      precioCompraUnitario: nuevoTexto,
      ...recalcularVenta(actualizada),
    })
  }

  function onCambiarMonedaVenta(fila: FilaCompra, nuevaMoneda: Moneda) {
    if (cotizacion == null) return
    const nuevoTexto = fila.precioVentaUnitario
      ? formatearEnMoneda(pesosDesde(fila.precioVentaUnitario, fila.monedaVenta, cotizacion), nuevaMoneda, cotizacion)
      : ''
    actualizarFila(fila.id, { monedaVenta: nuevaMoneda, precioVentaUnitario: nuevoTexto })
  }

  function subtotalFila(fila: FilaCompra): number {
    if (cotizacion == null) return 0
    const cantidad = Number(fila.cantidad) || 0
    const pesos = pesosDesde(fila.precioCompraUnitario, fila.monedaCompra, cotizacion)
    return cantidad * pesos
  }

  const total = filas.reduce((acc, f) => acc + subtotalFila(f), 0)

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    if (!sesion || cotizacion == null) return
    setError(null)
    setMensaje(null)

    if (new Date(fecha) > new Date(hoyIso())) {
      setError('La fecha de la compra no puede ser futura')
      return
    }

    const items: CompraItemRequest[] = []
    for (const fila of filas) {
      const cantidad = Number(fila.cantidad)
      const precioCompraUnitario = pesosDesde(fila.precioCompraUnitario, fila.monedaCompra, cotizacion)
      if (!cantidad || !precioCompraUnitario) continue

      const precioVentaUnitario = fila.precioVentaUnitario
        ? pesosDesde(fila.precioVentaUnitario, fila.monedaVenta, cotizacion)
        : undefined
      const productoExistente = productoDeFila(fila)

      if (productoExistente) {
        items.push({ idProducto: productoExistente.idProducto, cantidad, precioCompraUnitario, precioVentaUnitario })
      } else if (fila.texto && fila.nuevoRubro && fila.nuevoFamilia && fila.nuevoMarca && fila.nuevoDescripcion) {
        items.push({
          nuevoProducto: {
            rubro: fila.nuevoRubro,
            familia: fila.nuevoFamilia,
            marca: fila.nuevoMarca,
            descripcion: fila.nuevoDescripcion,
            codigoFabrica: fila.nuevoCodigoFabrica || undefined,
          },
          cantidad,
          precioCompraUnitario,
          precioVentaUnitario,
        })
      }
    }

    if (items.length === 0) {
      setError('Agregá al menos un renglón completo (producto, cantidad y precio de compra)')
      return
    }

    setEnviando(true)
    try {
      const compra = await registrarCompra({ idEmpleado: sesion.idEmpleado, fecha, proveedorNombre, medioPago, items })
      setMensaje(`Compra #${compra.idCompra} registrada. Total: ${compra.totalCompra.toFixed(2)}`)
      setFilas([filaVacia()])
      setProveedorNombre('')
      getProductos().then(setProductos)
      getMarcas().then(setMarcas)
      getProveedores().then(setProveedores)
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo registrar la compra')
    } finally {
      setEnviando(false)
    }
  }

  if (cotizacion == null) {
    return (
      <div>
        <h2>Agregar compra</h2>
        <p>
          <span className="spinner" /> Cargando cotización del día...
        </p>
      </div>
    )
  }

  return (
    <div>
      <h2>Agregar compra</h2>

      <form onSubmit={onSubmit}>
        <label>
          Fecha
          <input type="date" required max={hoyIso()} value={fecha} onChange={(e) => setFecha(e.target.value)} />
        </label>
        <label>
          Proveedor
          <input
            required
            list="proveedores-compra-datalist"
            value={proveedorNombre}
            onChange={(e) => setProveedorNombre(e.target.value)}
          />
          <datalist id="proveedores-compra-datalist">
            {proveedores.map((p) => (
              <option key={p.idProveedor} value={p.nombre} />
            ))}
          </datalist>
        </label>
        <label>
          Medio de pago
          <select value={medioPago} onChange={(e) => setMedioPago(e.target.value as MedioPago)}>
            <option value="EFECTIVO">Efectivo</option>
            <option value="TRANSFERENCIA">Transferencia</option>
            <option value="TARJETA">Tarjeta</option>
          </select>
        </label>

        <table>
          <thead>
            <tr>
              <th>Producto</th>
              <th>Cantidad</th>
              <th>Precio compra</th>
              <th>% Ganancia</th>
              <th>Precio venta</th>
              <th>Subtotal</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {filas.map((fila) => {
              const existente = productoDeFila(fila)
              const esNuevo = fila.texto.length > 0 && !existente
              return (
                <Fragment key={fila.id}>
                  <tr>
                    <td>
                      <input
                        list="productos-compra-datalist"
                        placeholder="Buscar o escribir un producto nuevo"
                        value={fila.texto}
                        onChange={(e) => actualizarFila(fila.id, { texto: e.target.value })}
                      />
                    </td>
                    <td>
                      <input
                        type="number"
                        min={1}
                        value={fila.cantidad}
                        onChange={(e) => actualizarFila(fila.id, { cantidad: e.target.value })}
                      />
                    </td>
                    <td className="celda-precio-moneda">
                      <input
                        type="number"
                        min={0}
                        step="0.01"
                        value={fila.precioCompraUnitario}
                        onChange={(e) => onCambiarPrecioCompra(fila, e.target.value)}
                      />
                      <select
                        value={fila.monedaCompra}
                        onChange={(e) => onCambiarMonedaCompra(fila, e.target.value as Moneda)}
                      >
                        <option value="ARS">ARS</option>
                        <option value="USD">USD</option>
                      </select>
                    </td>
                    <td>
                      <input
                        type="number"
                        min={0}
                        step="1"
                        placeholder="%"
                        value={fila.gananciaPct}
                        onChange={(e) => onCambiarGananciaPct(fila, e.target.value)}
                      />
                    </td>
                    <td className="celda-precio-moneda">
                      <input
                        type="number"
                        min={0}
                        step="0.01"
                        value={fila.precioVentaUnitario}
                        onChange={(e) => actualizarFila(fila.id, { precioVentaUnitario: e.target.value })}
                      />
                      <select
                        value={fila.monedaVenta}
                        onChange={(e) => onCambiarMonedaVenta(fila, e.target.value as Moneda)}
                      >
                        <option value="ARS">ARS</option>
                        <option value="USD">USD</option>
                      </select>
                    </td>
                    <td>{subtotalFila(fila).toFixed(2)}</td>
                    <td>
                      <button type="button" onClick={() => quitarFila(fila.id)}>
                        Quitar
                      </button>
                    </td>
                  </tr>
                  {esNuevo && (
                    <tr>
                      <td colSpan={7}>
                        <em>"{fila.texto}" no está en el catálogo — completá los datos para darlo de alta:</em>
                        <div className="agregar-producto">
                          <input
                            placeholder="Rubro (2 dígitos)"
                            maxLength={2}
                            value={fila.nuevoRubro}
                            onChange={(e) => actualizarFila(fila.id, { nuevoRubro: e.target.value })}
                          />
                          <input
                            placeholder="Familia (2 dígitos)"
                            maxLength={2}
                            value={fila.nuevoFamilia}
                            onChange={(e) => actualizarFila(fila.id, { nuevoFamilia: e.target.value })}
                          />
                          <input
                            list="marcas-compra-datalist"
                            placeholder="Marca"
                            value={fila.nuevoMarca}
                            onChange={(e) => actualizarFila(fila.id, { nuevoMarca: e.target.value })}
                          />
                          <input
                            placeholder="Descripción"
                            value={fila.nuevoDescripcion}
                            onChange={(e) => actualizarFila(fila.id, { nuevoDescripcion: e.target.value })}
                          />
                          <input
                            placeholder="Código de fábrica (opcional)"
                            value={fila.nuevoCodigoFabrica}
                            onChange={(e) => actualizarFila(fila.id, { nuevoCodigoFabrica: e.target.value })}
                          />
                        </div>
                      </td>
                    </tr>
                  )}
                </Fragment>
              )
            })}
          </tbody>
        </table>
        <datalist id="productos-compra-datalist">
          {productos.map((producto) => (
            <option key={producto.idProducto} value={etiquetaProducto(producto)} />
          ))}
        </datalist>
        <datalist id="marcas-compra-datalist">
          {marcas.map((m) => (
            <option key={m.idMarca} value={m.nombre} />
          ))}
        </datalist>

        <button type="button" onClick={agregarFila}>
          Agregar fila
        </button>

        <p>
          <strong>Total: {total.toFixed(2)}</strong>
        </p>

        {error && <p className="error">{error}</p>}
        {mensaje && <p className="resultado">{mensaje}</p>}
        <button type="submit" disabled={enviando}>
          {enviando && <span className="spinner" />}
          {enviando ? 'Registrando...' : 'Registrar compra'}
        </button>
      </form>
    </div>
  )
}
