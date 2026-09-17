import { useEffect, useState, type FormEvent, type KeyboardEvent, type ReactNode } from 'react'
import { useAuth } from '../auth/AuthContext'
import {
  actualizarProducto,
  cargarStock,
  crearProducto,
  eliminarProducto,
  getProductos,
} from '../api/productos'
import { getMarcas } from '../api/marcas'
import { getProveedores } from '../api/proveedores'
import { ApiRequestError } from '../api/client'
import { BarcodeInput } from '../components/BarcodeInput'
import { EtiquetaImprimible } from '../components/EtiquetaImprimible'
import { claseFilaStock, colorStock, ETIQUETA_COLOR_STOCK, type ColorStock } from '../utils/stockColor'
import type {
  MarcaResponse,
  Producto,
  ProductoRequest,
  ProductoUpdateRequest,
  ProveedorResponse,
} from '../types/api'

type CampoEditable = 'descripcion' | 'marca' | 'precioVenta' | 'precioCompra' | 'stockActual' | 'codigoFabrica'

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
  const [marcas, setMarcas] = useState<MarcaResponse[]>([])
  const [proveedores, setProveedores] = useState<ProveedorResponse[]>([])
  const [error, setError] = useState<string | null>(null)
  const [cargando, setCargando] = useState(true)

  const [filtroDescripcion, setFiltroDescripcion] = useState('')
  const [filtroMarca, setFiltroMarca] = useState('')
  const [filtroProveedor, setFiltroProveedor] = useState('')
  const [filtroColorStock, setFiltroColorStock] = useState<ColorStock | ''>('')

  const [nuevoProducto, setNuevoProducto] = useState<ProductoRequest>(PRODUCTO_VACIO)
  // % de ganancia deseado sobre el costo (100% = vender al doble) — mismo mecanismo que
  // ComprasNueva.tsx: autocompleta precioVenta a partir de precioCompra, pero lo deja editable
  // por si se quiere pisar el cálculo a mano.
  const [gananciaPctAlta, setGananciaPctAlta] = useState('')
  const [errorAlta, setErrorAlta] = useState<string | null>(null)
  const [ultimoCodigoGenerado, setUltimoCodigoGenerado] = useState<string | null>(null)
  const [ultimoProductoCreado, setUltimoProductoCreado] = useState<Producto | null>(null)
  const [etiquetaProducto, setEtiquetaProducto] = useState<Producto | null>(null)
  const [marcaInfoAlta, setMarcaInfoAlta] = useState<{ nombre: string; codigo: string; esNueva: boolean } | null>(
    null,
  )
  const [agregando, setAgregando] = useState(false)
  const [eliminandoId, setEliminandoId] = useState<number | null>(null)

  const [cantidadStock, setCantidadStock] = useState('1')
  const [errorStock, setErrorStock] = useState<string | null>(null)
  const [mensajeStock, setMensajeStock] = useState<string | null>(null)
  const [cargandoStock, setCargandoStock] = useState(false)

  const [celdaEditando, setCeldaEditando] = useState<{ id: number; campo: CampoEditable } | null>(null)
  const [valorEdicion, setValorEdicion] = useState('')
  const [guardandoId, setGuardandoId] = useState<number | null>(null)
  const [errorEdicion, setErrorEdicion] = useState<string | null>(null)

  function cargarProductos() {
    return getProductos()
      .then(setProductos)
      .catch((err) =>
        setError(err instanceof ApiRequestError ? err.message : 'No se pudieron cargar los productos'),
      )
  }

  useEffect(() => {
    cargarProductos().finally(() => setCargando(false))
    getMarcas().then(setMarcas)
    // Solo ADMIN tiene permiso sobre /api/proveedores (ver SecurityConfig) — pedirlo para
    // VENDEDOR también daba 403 y rompía la carga de la página entera.
    if (esAdmin) {
      getProveedores().then(setProveedores)
    }
  }, [esAdmin])

  // Derivado de los productos ya cargados (no de GET /api/proveedores, que es ADMIN-only — ver
  // SecurityConfig): así el filtro funciona también para VENDEDOR, que puede ver Productos pero
  // no tiene permiso sobre el catálogo de proveedores.
  const proveedoresDisponibles = Array.from(
    new Set(productos.map((p) => p.proveedor).filter((p): p is string => Boolean(p))),
  ).sort((a, b) => a.localeCompare(b))

  const productosFiltrados = productos.filter((p) => {
    const matchDescripcion = p.descripcion.toLowerCase().includes(filtroDescripcion.toLowerCase())
    const matchMarca = (p.marca ?? '').toLowerCase().includes(filtroMarca.toLowerCase())
    const matchProveedor = !filtroProveedor || p.proveedor === filtroProveedor
    const matchColorStock = !filtroColorStock || colorStock(p.stockActual) === filtroColorStock
    return matchDescripcion && matchMarca && matchProveedor && matchColorStock
  })

  // Mismo mecanismo que ComprasNueva.tsx (precioVenta = precioCompra × (1 + %/100), 100% = vender
  // al doble): acá no hay ARS/USD como en Compras (el alta de producto siempre fue en pesos), así
  // que el cálculo es directo sin pasar por la cotización del día.
  function precioVentaCalculado(precioCompra: number | undefined, pctTexto: string): number | undefined {
    const pct = Number(pctTexto)
    if (!pctTexto || Number.isNaN(pct) || precioCompra == null) return undefined
    return Number((precioCompra * (1 + pct / 100)).toFixed(2))
  }

  function onCambiarPrecioCompraAlta(texto: string) {
    const precioCompra = texto ? Number(texto) : undefined
    const calculado = precioVentaCalculado(precioCompra, gananciaPctAlta)
    setNuevoProducto((actual) => ({
      ...actual,
      precioCompra,
      ...(calculado != null ? { precioVenta: calculado } : {}),
    }))
  }

  function onCambiarGananciaPctAlta(texto: string) {
    setGananciaPctAlta(texto)
    const calculado = precioVentaCalculado(nuevoProducto.precioCompra, texto)
    if (calculado != null) {
      setNuevoProducto((actual) => ({ ...actual, precioVenta: calculado }))
    }
  }

  async function onAgregarProducto(event: FormEvent) {
    event.preventDefault()
    setErrorAlta(null)
    setUltimoCodigoGenerado(null)
    setUltimoProductoCreado(null)
    setMarcaInfoAlta(null)
    setAgregando(true)
    try {
      const nombreMarcaTipeado = nuevoProducto.marca.trim().toLowerCase()
      const marcaYaExistia = marcas.some((m) => m.nombre.trim().toLowerCase() === nombreMarcaTipeado)

      const creado = await crearProducto(nuevoProducto)
      setProductos((actual) => [...actual, creado])
      setUltimoCodigoGenerado(creado.codigoInterno)
      setUltimoProductoCreado(creado)
      if (creado.marca && creado.numeroMarca) {
        setMarcaInfoAlta({ nombre: creado.marca, codigo: creado.numeroMarca, esNueva: !marcaYaExistia })
      }
      setNuevoProducto(PRODUCTO_VACIO)
      setGananciaPctAlta('')
      getMarcas().then(setMarcas)
      getProveedores().then(setProveedores)
    } catch (err) {
      setErrorAlta(err instanceof ApiRequestError ? err.message : 'No se pudo agregar el producto')
    } finally {
      setAgregando(false)
    }
  }

  async function onEliminar(producto: Producto) {
    if (!window.confirm(`¿Eliminar "${producto.descripcion}" (${producto.marca ?? 'sin marca'})?`)) return
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

  function valorActualDeCampo(producto: Producto, campo: CampoEditable): string {
    switch (campo) {
      case 'descripcion':
        return producto.descripcion
      case 'marca':
        return producto.marca ?? ''
      case 'precioVenta':
        return producto.precioVenta != null ? String(producto.precioVenta) : ''
      case 'precioCompra':
        return producto.precioCompra != null ? String(producto.precioCompra) : ''
      case 'stockActual':
        return String(producto.stockActual)
      case 'codigoFabrica':
        return producto.codigoFabrica ?? ''
    }
  }

  function empezarEdicion(producto: Producto, campo: CampoEditable) {
    if (!esAdmin) return
    setErrorEdicion(null)
    setCeldaEditando({ id: producto.idProducto, campo })
    setValorEdicion(valorActualDeCampo(producto, campo))
  }

  function cancelarEdicion() {
    setCeldaEditando(null)
    setErrorEdicion(null)
  }

  async function confirmarEdicion() {
    if (!celdaEditando) return
    const { id, campo } = celdaEditando
    const valor = valorEdicion.trim()

    if (campo === 'precioVenta' || campo === 'precioCompra' || campo === 'stockActual') {
      if (valor === '' || Number.isNaN(Number(valor))) {
        setErrorEdicion('Ingresá un número válido')
        return
      }
    } else if (campo !== 'codigoFabrica' && valor === '') {
      // codigoFabrica es el único campo donde "" es válido: limpia el código de fábrica y el
      // producto vuelve a usar codigoInterno como codigoBarras (ver ProductoService.actualizar).
      setErrorEdicion('No puede quedar vacío')
      return
    }

    const cambios: ProductoUpdateRequest =
      campo === 'precioVenta'
        ? { precioVenta: Number(valor) }
        : campo === 'precioCompra'
          ? { precioCompra: Number(valor) }
          : campo === 'stockActual'
            ? { stockActual: Number(valor) }
            : campo === 'marca'
              ? { marca: valor }
              : campo === 'codigoFabrica'
                ? { codigoFabrica: valor }
                : { descripcion: valor }

    // Se sale del modo edición antes de esperar la respuesta: si el valor no cambió, evita
    // reintentar el guardado por el blur que dispara el propio input al desmontarse.
    setCeldaEditando(null)
    setGuardandoId(id)
    setErrorEdicion(null)
    try {
      const actualizado = await actualizarProducto(id, cambios)
      setProductos((actual) => actual.map((p) => (p.idProducto === id ? actualizado : p)))
    } catch (err) {
      setErrorEdicion(err instanceof ApiRequestError ? err.message : 'No se pudo guardar el cambio')
    } finally {
      setGuardandoId(null)
    }
  }

  function onKeyDownEdicion(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'Enter') confirmarEdicion()
    if (event.key === 'Escape') cancelarEdicion()
  }

  function celdaEditable(
    producto: Producto,
    campo: CampoEditable,
    valorMostrado: ReactNode,
    tipo: 'text' | 'number',
    paso?: string,
  ) {
    if (!esAdmin) return <td>{valorMostrado}</td>

    const editando = celdaEditando?.id === producto.idProducto && celdaEditando.campo === campo
    if (editando) {
      return (
        <td className="celda-editando">
          <input
            type={tipo}
            step={paso}
            min={campo === 'stockActual' ? 0 : undefined}
            autoFocus
            value={valorEdicion}
            onChange={(e) => setValorEdicion(e.target.value)}
            onKeyDown={onKeyDownEdicion}
            onBlur={confirmarEdicion}
          />
          {errorEdicion && <p className="error">{errorEdicion}</p>}
        </td>
      )
    }

    return (
      <td
        className="celda-editable"
        onClick={() => empezarEdicion(producto, campo)}
        title="Tocar para editar"
      >
        {guardandoId === producto.idProducto ? <span className="spinner" /> : valorMostrado}
      </td>
    )
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
        {/* Marca/proveedor/color de stock quedan colapsados: con las 4 opciones de color más los
            proveedores reales del catálogo (pueden ser varias decenas), tenerlos siempre abiertos
            junto a la búsqueda rápida por descripción hacía la barra de filtros demasiado larga. */}
        <details className="filtros-avanzados">
          <summary>Más filtros (marca, proveedor, color de stock)</summary>
          <div className="agregar-producto">
            <input
              placeholder="Filtrar por marca"
              value={filtroMarca}
              onChange={(e) => setFiltroMarca(e.target.value)}
            />
            <select value={filtroProveedor} onChange={(e) => setFiltroProveedor(e.target.value)}>
              <option value="">Todos los proveedores</option>
              {proveedoresDisponibles.map((p) => (
                <option key={p} value={p}>
                  {p}
                </option>
              ))}
            </select>
            <select
              value={filtroColorStock}
              onChange={(e) => setFiltroColorStock(e.target.value as ColorStock | '')}
            >
              <option value="">Todos los colores de stock</option>
              {(Object.keys(ETIQUETA_COLOR_STOCK) as ColorStock[]).map((color) => (
                <option key={color} value={color}>
                  {ETIQUETA_COLOR_STOCK[color]}
                </option>
              ))}
            </select>
          </div>
        </details>
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

      {esAdmin && (
        <p className="ayuda-edicion">
          Tocá una celda de descripción, marca, precio de venta, precio de compra, stock o código
          de fábrica para editarla.
        </p>
      )}

      <table>
        <thead>
          <tr>
            <th>Descripción</th>
            <th>Marca</th>
            <th>Rubro</th>
            <th>Proveedor</th>
            <th>Código interno</th>
            <th>Código de fábrica</th>
            <th>Precio venta</th>
            <th>Precio compra</th>
            <th>USD venta</th>
            <th>USD compra</th>
            <th>Stock</th>
            <th />
            {esAdmin && <th />}
          </tr>
        </thead>
        <tbody>
          {productosFiltrados.map((producto) => (
            <tr
              key={producto.idProducto}
              className={claseFilaStock(producto.stockActual)}
              title={ETIQUETA_COLOR_STOCK[colorStock(producto.stockActual)]}
            >
              {celdaEditable(producto, 'descripcion', producto.descripcion, 'text')}
              {celdaEditable(producto, 'marca', producto.marca ?? '—', 'text')}
              <td>{producto.rubro}</td>
              <td>{producto.proveedor || '—'}</td>
              <td>{producto.codigoInterno}</td>
              {celdaEditable(producto, 'codigoFabrica', producto.codigoFabrica ?? '—', 'text')}
              {celdaEditable(
                producto,
                'precioVenta',
                producto.precioVenta != null ? producto.precioVenta.toFixed(2) : '—',
                'number',
                '0.01',
              )}
              {celdaEditable(
                producto,
                'precioCompra',
                producto.precioCompra != null ? producto.precioCompra.toFixed(2) : '—',
                'number',
                '0.01',
              )}
              <td>{producto.precioVentaUsd != null ? `USD ${producto.precioVentaUsd.toFixed(2)}` : '—'}</td>
              <td>{producto.precioCompraUsd != null ? `USD ${producto.precioCompraUsd.toFixed(2)}` : '—'}</td>
              {celdaEditable(producto, 'stockActual', producto.stockActual, 'number', '1')}
              <td>
                <button type="button" onClick={() => setEtiquetaProducto(producto)}>
                  Imprimir etiqueta
                </button>
              </td>
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
              Marca (escribí el nombre, no es un código — no importa mayúsculas/minúsculas)
              <input
                required
                list="marcas-datalist"
                placeholder="Ej. KALOP"
                value={nuevoProducto.marca}
                onChange={(e) => setNuevoProducto({ ...nuevoProducto, marca: e.target.value })}
              />
              <datalist id="marcas-datalist">
                {marcas.map((m) => (
                  <option key={m.idMarca} value={m.nombre} />
                ))}
              </datalist>
              <small>Si la marca no existe todavía, se crea sola y te avisamos qué código le tocó.</small>
            </label>
            <label>
              Proveedor
              <input
                required
                list="proveedores-datalist"
                value={nuevoProducto.proveedor}
                onChange={(e) => setNuevoProducto({ ...nuevoProducto, proveedor: e.target.value })}
              />
              <datalist id="proveedores-datalist">
                {proveedores.map((p) => (
                  <option key={p.idProveedor} value={p.nombre} />
                ))}
              </datalist>
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
              <input
                value={nuevoProducto.codigoFabrica ?? ''}
                onChange={(e) => setNuevoProducto({ ...nuevoProducto, codigoFabrica: e.target.value })}
                onKeyDown={(e) => {
                  // El lector físico manda un Enter solo apenas termina de escanear — sin este
                  // preventDefault, ese Enter dispara el submit del formulario entero (todavía
                  // incompleto: faltan descripción, precio, etc.), no solo confirma este campo.
                  // A diferencia de BarcodeInput (pensado para "escanear y accionar ya", como en
                  // Cobros o Cargar stock), acá el código tiene que quedar visible y cargado en el
                  // campo hasta que se manden todos los datos juntos con "Agregar producto".
                  if (e.key === 'Enter') e.preventDefault()
                }}
                placeholder="Código de fábrica"
              />
              {!nuevoProducto.codigoFabrica && (
                <small>
                  Sin código de fábrica: se va a usar el código interno (se genera al guardar) como
                  código de barras.
                </small>
              )}
            </label>
            <label>
              Precio de compra
              <input
                type="number"
                step="0.01"
                min={0}
                value={nuevoProducto.precioCompra ?? ''}
                onChange={(e) => onCambiarPrecioCompraAlta(e.target.value)}
              />
            </label>
            <label>
              % Ganancia
              <input
                type="number"
                step="1"
                min={0}
                placeholder="%"
                value={gananciaPctAlta}
                onChange={(e) => onCambiarGananciaPctAlta(e.target.value)}
              />
              <small>Recargo sobre el costo (100% = vender al doble) — autocompleta el precio de venta.</small>
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
              <small>Se autocompleta con precio de compra + % ganancia, pero se puede pisar a mano.</small>
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
          {ultimoProductoCreado && !ultimoProductoCreado.codigoFabrica && (
            <p className="resultado">
              Sin código de fábrica: se usará el código interno {ultimoProductoCreado.codigoInterno}{' '}
              como código de barras.
            </p>
          )}
          {ultimoProductoCreado && (
            <button type="button" onClick={() => setEtiquetaProducto(ultimoProductoCreado)}>
              Imprimir etiqueta
            </button>
          )}
          {marcaInfoAlta && (
            <p className="resultado">
              {marcaInfoAlta.esNueva
                ? `Se creó la marca "${marcaInfoAlta.nombre}" y se le asignó el código N° ${marcaInfoAlta.codigo}.`
                : `Marca "${marcaInfoAlta.nombre}", código N° ${marcaInfoAlta.codigo}.`}
            </p>
          )}
        </section>
      )}

      {etiquetaProducto && (
        <EtiquetaImprimible
          codigoBarras={etiquetaProducto.codigoBarras ?? etiquetaProducto.codigoInterno}
          descripcion={etiquetaProducto.descripcion}
          precioVenta={etiquetaProducto.precioVenta}
          onCerrar={() => setEtiquetaProducto(null)}
        />
      )}
    </div>
  )
}
