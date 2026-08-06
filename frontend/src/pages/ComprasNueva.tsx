import { Fragment, useEffect, useState, type FormEvent } from 'react'
import { useAuth } from '../auth/AuthContext'
import { getProductos } from '../api/productos'
import { getMarcas } from '../api/marcas'
import { getProveedores } from '../api/proveedores'
import { registrarCompra } from '../api/compras'
import { ApiRequestError } from '../api/client'
import { hoyIso } from '../utils/date'
import type { CompraItemRequest, MarcaResponse, MedioPago, Producto, ProveedorResponse } from '../types/api'

function etiquetaProducto(producto: Producto): string {
  return `${producto.descripcion} - ${producto.marca ?? 'sin marca'} (${producto.codigoInterno})`
}

interface FilaCompra {
  id: number
  texto: string
  cantidad: string
  precioCompraUnitario: string
  precioVentaUnitario: string
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
    precioVentaUnitario: '',
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

  function subtotalFila(fila: FilaCompra): number {
    const cantidad = Number(fila.cantidad) || 0
    const precio = Number(fila.precioCompraUnitario) || 0
    return cantidad * precio
  }

  const total = filas.reduce((acc, f) => acc + subtotalFila(f), 0)

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    if (!sesion) return
    setError(null)
    setMensaje(null)

    if (new Date(fecha) > new Date(hoyIso())) {
      setError('La fecha de la compra no puede ser futura')
      return
    }

    const items: CompraItemRequest[] = []
    for (const fila of filas) {
      const cantidad = Number(fila.cantidad)
      const precioCompraUnitario = Number(fila.precioCompraUnitario)
      if (!cantidad || !precioCompraUnitario) continue

      const precioVentaUnitario = fila.precioVentaUnitario ? Number(fila.precioVentaUnitario) : undefined
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
                    <td>
                      <input
                        type="number"
                        min={0}
                        step="0.01"
                        value={fila.precioCompraUnitario}
                        onChange={(e) => actualizarFila(fila.id, { precioCompraUnitario: e.target.value })}
                      />
                    </td>
                    <td>
                      <input
                        type="number"
                        min={0}
                        step="0.01"
                        value={fila.precioVentaUnitario}
                        onChange={(e) => actualizarFila(fila.id, { precioVentaUnitario: e.target.value })}
                      />
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
                      <td colSpan={6}>
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
