import { useEffect, useState, type FormEvent } from 'react'
import { useAuth } from '../auth/AuthContext'
import { cargarStock, crearProducto, eliminarProducto, getProductos } from '../api/productos'
import { ApiRequestError } from '../api/client'
import { BarcodeInput } from '../components/BarcodeInput'
import type { Producto, ProductoRequest } from '../types/api'

const PRODUCTO_VACIO: ProductoRequest = {
  rubro: '',
  familia: '',
  marca: '',
  proveedor: '',
  codigoFabrica: '',
  descripcion: '',
  precioVenta: 0,
  precioCompra: undefined,
  stockActual: 0,
}

export function Productos() {
  const { sesion } = useAuth()
  const esAdmin = sesion?.rol === 'ADMIN'
  const [productos, setProductos] = useState<Producto[]>([])
  const [error, setError] = useState<string | null>(null)
  const [cargando, setCargando] = useState(true)

  const [filtroDescripcion, setFiltroDescripcion] = useState('')
  const [filtroMarca, setFiltroMarca] = useState('')

  const [nuevoProducto, setNuevoProducto] = useState<ProductoRequest>(PRODUCTO_VACIO)
  const [errorAlta, setErrorAlta] = useState<string | null>(null)
  const [ultimoCodigoGenerado, setUltimoCodigoGenerado] = useState<string | null>(null)
  const [agregando, setAgregando] = useState(false)
  const [eliminandoId, setEliminandoId] = useState<number | null>(null)

  const [cantidadStock, setCantidadStock] = useState('1')
  const [errorStock, setErrorStock] = useState<string | null>(null)
  const [mensajeStock, setMensajeStock] = useState<string | null>(null)
  const [cargandoStock, setCargandoStock] = useState(false)

  function cargarProductos() {
    return getProductos()
      .then(setProductos)
      .catch((err) =>
        setError(err instanceof ApiRequestError ? err.message : 'No se pudieron cargar los productos'),
      )
  }

  useEffect(() => {
    cargarProductos().finally(() => setCargando(false))
  }, [])

  const productosFiltrados = productos.filter((p) => {
    const matchDescripcion = p.descripcion.toLowerCase().includes(filtroDescripcion.toLowerCase())
    const matchMarca = p.marca.toLowerCase().includes(filtroMarca.toLowerCase())
    return matchDescripcion && matchMarca
  })

  async function onAgregarProducto(event: FormEvent) {
    event.preventDefault()
    setErrorAlta(null)
    setUltimoCodigoGenerado(null)
    setAgregando(true)
    try {
      const creado = await crearProducto(nuevoProducto)
      setProductos((actual) => [...actual, creado])
      setUltimoCodigoGenerado(creado.codigoInterno)
      setNuevoProducto(PRODUCTO_VACIO)
    } catch (err) {
      setErrorAlta(err instanceof ApiRequestError ? err.message : 'No se pudo agregar el producto')
    } finally {
      setAgregando(false)
    }
  }

  async function onEliminar(producto: Producto) {
    if (!window.confirm(`¿Eliminar "${producto.descripcion}" (${producto.marca})?`)) return
    setEliminandoId(producto.idProducto)
    try {
      await eliminarProducto(producto.idProducto)
      setProductos((actual) => actual.filter((p) => p.idProducto !== producto.idProducto))
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'No se pudo eliminar el producto')
    } finally {
      setEliminandoId(null)
    }
  }

  async function onEscanearParaCargarStock(codigo: string) {
    setErrorStock(null)
    setMensajeStock(null)
    const cantidad = Number(cantidadStock)
    if (!cantidad || cantidad <= 0) {
      setErrorStock('Ingresá una cantidad válida antes de escanear')
      return
    }
    setCargandoStock(true)
    try {
      const actualizado = await cargarStock({ codigo, cantidad })
      setProductos((actual) =>
        actual.map((p) => (p.idProducto === actualizado.idProducto ? actualizado : p)),
      )
      setMensajeStock(`Stock actualizado: ${actualizado.descripcion} ahora tiene ${actualizado.stockActual}`)
    } catch (err) {
      setErrorStock(
        err instanceof ApiRequestError ? err.message : `No se encontró ningún producto para el código ${codigo}`,
      )
    } finally {
      setCargandoStock(false)
    }
  }

  if (cargando) return <p>Cargando productos...</p>
  if (error) return <p className="error">{error}</p>

  return (
    <div>
      <h2>Productos</h2>

      <section>
        <h3>Filtrar</h3>
        <input
          placeholder="Filtrar por descripción"
          value={filtroDescripcion}
          onChange={(e) => setFiltroDescripcion(e.target.value)}
        />
        <input
          placeholder="Filtrar por marca"
          value={filtroMarca}
          onChange={(e) => setFiltroMarca(e.target.value)}
        />
      </section>

      {esAdmin && (
        <section>
          <h3>Cargar stock (escanear código de barras)</h3>
          <input
            type="number"
            min={1}
            placeholder="Cantidad a cargar"
            value={cantidadStock}
            onChange={(e) => setCantidadStock(e.target.value)}
          />
          <BarcodeInput onScan={onEscanearParaCargarStock} />
          {cargandoStock && (
            <p>
              <span className="spinner" />
              Actualizando stock...
            </p>
          )}
          {errorStock && <p className="error">{errorStock}</p>}
          {mensajeStock && <p className="resultado">{mensajeStock}</p>}
        </section>
      )}

      <table>
        <thead>
          <tr>
            <th>Descripción</th>
            <th>Marca</th>
            <th>Rubro</th>
            <th>Código interno</th>
            <th>Precio venta</th>
            <th>Stock</th>
            {esAdmin && <th />}
          </tr>
        </thead>
        <tbody>
          {productosFiltrados.map((producto) => (
            <tr key={producto.idProducto}>
              <td>{producto.descripcion}</td>
              <td>{producto.marca}</td>
              <td>{producto.rubro}</td>
              <td>{producto.codigoInterno}</td>
              <td>{producto.precioVenta != null ? producto.precioVenta.toFixed(2) : '—'}</td>
              <td>{producto.stockActual}</td>
              {esAdmin && (
                <td>
                  <button
                    type="button"
                    onClick={() => onEliminar(producto)}
                    disabled={eliminandoId === producto.idProducto}
                  >
                    {eliminandoId === producto.idProducto && <span className="spinner" />}
                    {eliminandoId === producto.idProducto ? 'Eliminando...' : 'Eliminar'}
                  </button>
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>

      {esAdmin && (
        <section>
          <h3>Agregar producto</h3>
          <form onSubmit={onAgregarProducto}>
            <label>
              Rubro (2 dígitos)
              <input
                maxLength={2}
                required
                value={nuevoProducto.rubro}
                onChange={(e) => setNuevoProducto({ ...nuevoProducto, rubro: e.target.value })}
              />
            </label>
            <label>
              Familia (2 dígitos)
              <input
                maxLength={2}
                required
                value={nuevoProducto.familia}
                onChange={(e) => setNuevoProducto({ ...nuevoProducto, familia: e.target.value })}
              />
            </label>
            <label>
              Marca (2 dígitos)
              <input
                maxLength={2}
                required
                value={nuevoProducto.marca}
                onChange={(e) => setNuevoProducto({ ...nuevoProducto, marca: e.target.value })}
              />
            </label>
            <label>
              Proveedor
              <input
                required
                value={nuevoProducto.proveedor}
                onChange={(e) => setNuevoProducto({ ...nuevoProducto, proveedor: e.target.value })}
              />
            </label>
            <label>
              Descripción
              <input
                required
                value={nuevoProducto.descripcion}
                onChange={(e) => setNuevoProducto({ ...nuevoProducto, descripcion: e.target.value })}
              />
            </label>
            <label>
              Código de fábrica (opcional, escanear o tipear)
              <BarcodeInput
                onScan={(codigo) => setNuevoProducto({ ...nuevoProducto, codigoFabrica: codigo })}
                placeholder="Código de fábrica"
              />
            </label>
            <label>
              Precio de venta
              <input
                type="number"
                step="0.01"
                min={0}
                required
                value={nuevoProducto.precioVenta}
                onChange={(e) => setNuevoProducto({ ...nuevoProducto, precioVenta: Number(e.target.value) })}
              />
            </label>
            <label>
              Precio de compra
              <input
                type="number"
                step="0.01"
                min={0}
                value={nuevoProducto.precioCompra ?? ''}
                onChange={(e) =>
                  setNuevoProducto({
                    ...nuevoProducto,
                    precioCompra: e.target.value ? Number(e.target.value) : undefined,
                  })
                }
              />
            </label>
            <label>
              Stock inicial
              <input
                type="number"
                min={0}
                required
                value={nuevoProducto.stockActual}
                onChange={(e) => setNuevoProducto({ ...nuevoProducto, stockActual: Number(e.target.value) })}
              />
            </label>
            {errorAlta && <p className="error">{errorAlta}</p>}
            <button type="submit" disabled={agregando}>
              {agregando && <span className="spinner" />}
              {agregando ? 'Agregando...' : 'Agregar producto'}
            </button>
          </form>
          {ultimoCodigoGenerado && (
            <p className="resultado">Producto creado con código interno: {ultimoCodigoGenerado}</p>
          )}
        </section>
      )}
    </div>
  )
}
