import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent, type ReactNode } from 'react'
import { useSearchParams } from 'react-router-dom'
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
import { EncabezadoPagina } from '../components/EncabezadoPagina'
import { Modal } from '../components/Modal'
import { formatoPesos } from '../utils/formato'
import { EtiquetaImprimible } from '../components/EtiquetaImprimible'
import { claseFilaStock, colorStock, ETIQUETA_COLOR_STOCK, type ColorStock } from '../utils/stockColor'
import type {
  MarcaResponse,
  Producto,
  ProductoRequest,
  ProductoUpdateRequest,
  ProveedorResponse,
} from '../types/api'

type CampoEditable =
  | 'descripcion'
  | 'marca'
  | 'proveedor'
  | 'precioVenta'
  | 'precioCompra'
  | 'stockActual'
  | 'codigoFabrica'

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
const POR_PAGINA = 100


export function Productos() {
  const { sesion } = useAuth()
  const esAdmin = sesion?.rol === 'ADMIN'
  const [productos, setProductos] = useState<Producto[]>([])
  const [marcas, setMarcas] = useState<MarcaResponse[]>([])
  const [proveedores, setProveedores] = useState<ProveedorResponse[]>([])
  const [error, setError] = useState<string | null>(null)
  const [cargando, setCargando] = useState(true)

  const [searchParams] = useSearchParams()
  const [filtroDescripcion, setFiltroDescripcion] = useState(() => searchParams.get('buscar') ?? '')
  // El buscador de la barra de arriba trae el producto elegido en ?buscar=.
  const buscarEnUrl = searchParams.get('buscar')
  useEffect(() => {
    if (buscarEnUrl != null) setFiltroDescripcion(buscarEnUrl)
  }, [buscarEnUrl])
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
  // Agregar producto y Cargar stock se abren desde los botones de arriba a la derecha.
  const [modalProductos, setModalProductos] = useState<'alta' | 'stock' | null>(null)

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

  // Con miles de productos, dibujar la tabla entera (decenas de miles de elementos) hacía que la
  // pantalla tardara segundos en aparecer y en responder a la rueda del mouse. Se muestran de a
  // POR_PAGINA y se suman más solos al llegar al final (o con el botón). Al cambiar un filtro,
  // vuelve a empezar desde la primera página.
  const claveFiltros = [filtroDescripcion, filtroMarca, filtroProveedor, filtroColorStock].join('|')
  const [paginacion, setPaginacion] = useState({ clave: claveFiltros, cantidad: POR_PAGINA })
  const cantidadVisible = paginacion.clave === claveFiltros ? paginacion.cantidad : POR_PAGINA
  const productosVisibles = productosFiltrados.slice(0, cantidadVisible)
  const quedan = productosFiltrados.length - productosVisibles.length
  const finDeLista = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const marcador = finDeLista.current
    if (!marcador || quedan <= 0) return
    const observador = new IntersectionObserver(
      (entradas) => {
        if (entradas.some((e) => e.isIntersecting)) {
          setPaginacion({ clave: claveFiltros, cantidad: cantidadVisible + POR_PAGINA })
        }
      },
      { rootMargin: '600px' },
    )
    observador.observe(marcador)
    return () => observador.disconnect()
  }, [claveFiltros, cantidadVisible, quedan])

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
      case 'proveedor':
        return producto.proveedor ?? ''
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
              : campo === 'proveedor'
                ? { proveedor: valor }
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

  // Celda de la tabla que el ADMIN edita tocándola. `etiqueta` se muestra en el celular, donde cada
  // producto se ve como tarjeta (sin encabezados de columna); `debajo` agrega un dato en chico.
  function celdaEditable({
    producto,
    campo,
    valor,
    tipo,
    paso,
    clase,
    etiqueta,
    debajo,
  }: {
    producto: Producto
    campo: CampoEditable
    valor: ReactNode
    tipo: 'text' | 'number'
    paso?: string
    clase?: string
    etiqueta: string
    debajo?: ReactNode
  }) {
    const contenido = (
      <>
        {valor}
        {debajo}
      </>
    )
    if (!esAdmin) {
      return (
        <td className={clase} data-label={etiqueta}>
          {contenido}
        </td>
      )
    }

    const editando = celdaEditando?.id === producto.idProducto && celdaEditando.campo === campo
    if (editando) {
      return (
        <td className={`celda-editando ${clase ?? ''}`} data-label={etiqueta}>
          <input
            type={tipo}
            step={paso}
            inputMode={tipo === 'number' ? 'decimal' : undefined}
            aria-label={etiqueta}
            min={campo === 'stockActual' ? 0 : undefined}
            autoFocus
            list={campo === 'proveedor' ? 'proveedores-edicion-datalist' : undefined}
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
        className={`celda-editable ${clase ?? ''}`}
        data-label={etiqueta}
        onClick={() => empezarEdicion(producto, campo)}
        title="Tocar para editar"
      >
        {guardandoId === producto.idProducto ? <span className="spinner" aria-hidden="true" /> : contenido}
      </td>
    )
  }

  if (cargando) return <p>Cargando productos…</p>
  if (error) return <p className="alerta alerta-error">{error}</p>

  const hayFiltros = Boolean(filtroDescripcion || filtroMarca || filtroProveedor || filtroColorStock)
  const precioUsd = (valor: number | null) => (valor != null ? <small className="celda-sub">USD {valor.toFixed(2)}</small> : null)

  return (
    <div className="productos">
      <EncabezadoPagina
        titulo="Productos"
        subtitulo={
          hayFiltros
            ? `Mostrando ${productosFiltrados.length} de ${productos.length} productos`
            : `${productos.length} productos`
        }
        acciones={
          esAdmin && (
            <>
              <button type="button" className="btn-secundario" onClick={() => setModalProductos('stock')}>
                Cargar stock
              </button>
              <button type="button" onClick={() => setModalProductos('alta')}>
                + Agregar producto
              </button>
            </>
          )
        }
      />

      <section className="tarjeta productos-filtros">
        <div className="productos-filtros-fila">
          <label className="productos-filtro-texto">
            Buscar
            <input
              type="search"
              placeholder="Descripción del producto…"
              value={filtroDescripcion}
              onChange={(e) => setFiltroDescripcion(e.target.value)}
            />
          </label>
          <label>
            Marca
            <input placeholder="Todas" value={filtroMarca} onChange={(e) => setFiltroMarca(e.target.value)} />
          </label>
          <label>
            Proveedor
            <select value={filtroProveedor} onChange={(e) => setFiltroProveedor(e.target.value)}>
              <option value="">Todos</option>
              {proveedoresDisponibles.map((p) => (
                <option key={p} value={p}>
                  {p}
                </option>
              ))}
            </select>
          </label>
          {hayFiltros && (
            <button
              type="button"
              className="btn-suave"
              onClick={() => {
                setFiltroDescripcion('')
                setFiltroMarca('')
                setFiltroProveedor('')
                setFiltroColorStock('')
              }}
            >
              Limpiar filtros
            </button>
          )}
        </div>
        {/* Leyenda de colores de stock: tocar uno filtra por ese color, tocarlo de nuevo lo saca. */}
        <div className="stock-leyenda" role="group" aria-label="Filtrar por stock">
          {(Object.keys(ETIQUETA_COLOR_STOCK) as ColorStock[]).map((color) => (
            <button
              key={color}
              type="button"
              className={`stock-chip stock-chip-${color}`}
              aria-pressed={filtroColorStock === color}
              onClick={() => setFiltroColorStock((actual) => (actual === color ? '' : color))}
            >
              <span className="stock-chip-punto" aria-hidden="true" />
              {ETIQUETA_COLOR_STOCK[color]}
            </button>
          ))}
        </div>
      </section>

      {esAdmin && (
        <p className="ayuda-edicion">
          Tocá la descripción, marca, proveedor, código de fábrica, precios o stock para editarlos.
        </p>
      )}

      <section className="tarjeta productos-tabla">
        {productosFiltrados.length === 0 ? (
          <p className="cobros-vacio">
            {productos.length === 0 ? 'Todavía no hay productos cargados.' : 'Ningún producto coincide con los filtros.'}
          </p>
        ) : (
          <div className="tabla-scroll">
            <table className="tabla-productos">
              <thead>
                <tr>
                  <th>Producto</th>
                  <th>Marca</th>
                  <th>Proveedor</th>
                  <th>Cód. de fábrica</th>
                  <th className="celda-numero">Venta</th>
                  <th className="celda-numero">Compra</th>
                  <th className="celda-numero">Stock</th>
                  <th>
                    <span className="solo-lector">Acciones</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {productosVisibles.map((producto) => (
                  <tr
                    key={producto.idProducto}
                    className={claseFilaStock(producto.stockActual)}
                    title={ETIQUETA_COLOR_STOCK[colorStock(producto.stockActual)]}
                  >
                    {celdaEditable({
                      producto,
                      campo: 'descripcion',
                      valor: <span className="producto-nombre">{producto.descripcion}</span>,
                      tipo: 'text',
                      clase: 'celda-producto',
                      etiqueta: 'Producto',
                      debajo: <small className="celda-sub">{producto.codigoInterno}</small>,
                    })}
                    {celdaEditable({ producto, campo: 'marca', valor: producto.marca ?? '—', tipo: 'text', etiqueta: 'Marca' })}
                    {celdaEditable({
                      producto,
                      campo: 'proveedor',
                      valor: producto.proveedor || '—',
                      tipo: 'text',
                      etiqueta: 'Proveedor',
                    })}
                    {celdaEditable({
                      producto,
                      campo: 'codigoFabrica',
                      valor: producto.codigoFabrica ?? '—',
                      tipo: 'text',
                      clase: 'celda-codigo',
                      etiqueta: 'Cód. de fábrica',
                    })}
                    {celdaEditable({
                      producto,
                      campo: 'precioVenta',
                      valor: <strong>{producto.precioVenta != null ? formatoPesos(producto.precioVenta) : '—'}</strong>,
                      tipo: 'number',
                      paso: '0.01',
                      clase: 'celda-numero celda-monto',
                      etiqueta: 'Precio de venta',
                      debajo: precioUsd(producto.precioVentaUsd),
                    })}
                    {celdaEditable({
                      producto,
                      campo: 'precioCompra',
                      valor: producto.precioCompra != null ? formatoPesos(producto.precioCompra) : '—',
                      tipo: 'number',
                      paso: '0.01',
                      clase: 'celda-numero celda-monto',
                      etiqueta: 'Precio de compra',
                      debajo: precioUsd(producto.precioCompraUsd),
                    })}
                    {celdaEditable({
                      producto,
                      campo: 'stockActual',
                      valor: <strong>{producto.stockActual}</strong>,
                      tipo: 'number',
                      paso: '1',
                      clase: 'celda-numero celda-stock',
                      etiqueta: 'Stock',
                    })}
                    <td className="celda-acciones">
                      <div className="productos-acciones">
                        <button
                          type="button"
                          className="btn-icono"
                          onClick={() => setEtiquetaProducto(producto)}
                          aria-label={`Imprimir etiqueta de ${producto.descripcion}`}
                          title="Imprimir etiqueta"
                        >
                          <svg viewBox="0 0 24 24" aria-hidden="true">
                            <path d="M7 9V3h10v6M7 17H4v-7h16v7h-3M7 14h10v7H7z" />
                          </svg>
                          <span className="texto-solo-celular">Etiqueta</span>
                        </button>
                        {esAdmin && (
                          <button
                            type="button"
                            className="btn-icono btn-icono-peligro"
                            onClick={() => onEliminar(producto)}
                            disabled={eliminandoId === producto.idProducto}
                            aria-label={`Eliminar ${producto.descripcion}`}
                            title="Eliminar producto"
                          >
                            {eliminandoId === producto.idProducto ? (
                              <span className="spinner" aria-hidden="true" />
                            ) : (
                              <svg viewBox="0 0 24 24" aria-hidden="true">
                                <path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3" />
                              </svg>
                            )}
                            <span className="texto-solo-celular">Eliminar</span>
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {quedan > 0 && (
          <div ref={finDeLista} className="productos-mas">
            <button
              type="button"
              className="btn-suave"
              onClick={() => setPaginacion({ clave: claveFiltros, cantidad: cantidadVisible + POR_PAGINA })}
            >
              Mostrar más ({quedan} {quedan === 1 ? 'producto' : 'productos'} más)
            </button>
          </div>
        )}
      </section>
      {/* Sugerencias para editar el proveedor en línea (solo ADMIN carga el catálogo). */}
      <datalist id="proveedores-edicion-datalist">
        {proveedores.map((p) => (
          <option key={p.idProveedor} value={p.nombre} />
        ))}
      </datalist>

      {modalProductos === 'stock' && (
        <Modal title="Cargar stock" onClose={() => setModalProductos(null)}>
          <p className="texto-apagado">
            Poné cuántas unidades entraron y escaneá el código: se suman al stock del producto.
          </p>
          <label>
            Cantidad a sumar
            <input
              type="number"
              inputMode="numeric"
              min={1}
              value={cantidadStock}
              onChange={(e) => setCantidadStock(e.target.value)}
            />
          </label>
          <label>
            Código de barras
            <BarcodeInput onScan={onEscanearParaCargarStock} />
          </label>
          {cargandoStock && (
            <p>
              <span className="spinner" aria-hidden="true" /> Actualizando stock…
            </p>
          )}
          {errorStock && <p className="alerta alerta-error">{errorStock}</p>}
          {mensajeStock && <p className="alerta alerta-ok">{mensajeStock}</p>}
        </Modal>
      )}

      {modalProductos === 'alta' && (
        <Modal title="Agregar producto" onClose={() => setModalProductos(null)} wide>
          {ultimoProductoCreado && (
            <div className="alerta alerta-ok producto-creado">
              <p>
                Producto creado con código interno <strong>{ultimoCodigoGenerado}</strong>.
                {!ultimoProductoCreado.codigoFabrica && ' Ese código se usa como código de barras.'}
                {marcaInfoAlta &&
                  (marcaInfoAlta.esNueva
                    ? ` Se creó la marca "${marcaInfoAlta.nombre}" con el código N° ${marcaInfoAlta.codigo}.`
                    : ` Marca "${marcaInfoAlta.nombre}", código N° ${marcaInfoAlta.codigo}.`)}
              </p>
              <button
                type="button"
                className="btn-secundario btn-chico"
                onClick={() => setEtiquetaProducto(ultimoProductoCreado)}
              >
                Imprimir etiqueta
              </button>
            </div>
          )}
          <form onSubmit={onAgregarProducto} className="formulario-grilla">
            <label className="campo-ancho">
              Descripción
              <input
                required
                value={nuevoProducto.descripcion}
                onChange={(e) => setNuevoProducto({ ...nuevoProducto, descripcion: e.target.value })}
              />
            </label>
            <label>
              Rubro (2 dígitos)
              <input
                maxLength={2}
                inputMode="numeric"
                required
                value={nuevoProducto.rubro}
                onChange={(e) => setNuevoProducto({ ...nuevoProducto, rubro: e.target.value })}
              />
            </label>
            <label>
              Familia (2 dígitos)
              <input
                maxLength={2}
                inputMode="numeric"
                required
                value={nuevoProducto.familia}
                onChange={(e) => setNuevoProducto({ ...nuevoProducto, familia: e.target.value })}
              />
            </label>
            <label>
              Marca
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
              <small>Escribí el nombre. Si no existe, se crea sola.</small>
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
            <label className="campo-ancho">
              Código de fábrica (opcional: escanealo o tipealo)
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
              />
              {!nuevoProducto.codigoFabrica && (
                <small>Sin código de fábrica se usa el código interno (se genera al guardar) como código de barras.</small>
              )}
            </label>
            <label>
              Precio de compra
              <input
                type="number"
                inputMode="decimal"
                step="0.01"
                min={0}
                value={nuevoProducto.precioCompra ?? ''}
                onChange={(e) => onCambiarPrecioCompraAlta(e.target.value)}
              />
            </label>
            <label>
              % de ganancia
              <input
                type="number"
                inputMode="decimal"
                step="1"
                min={0}
                placeholder="Ej. 100 = vender al doble"
                value={gananciaPctAlta}
                onChange={(e) => onCambiarGananciaPctAlta(e.target.value)}
              />
            </label>
            <label>
              Precio de venta
              <input
                type="number"
                inputMode="decimal"
                step="0.01"
                min={0}
                required
                value={nuevoProducto.precioVenta}
                onChange={(e) => setNuevoProducto({ ...nuevoProducto, precioVenta: Number(e.target.value) })}
              />
              <small>Se calcula con compra + % de ganancia; lo podés cambiar a mano.</small>
            </label>
            <label>
              Stock inicial
              <input
                type="number"
                inputMode="numeric"
                min={0}
                required
                value={nuevoProducto.stockActual}
                onChange={(e) => setNuevoProducto({ ...nuevoProducto, stockActual: Number(e.target.value) })}
              />
            </label>
            {errorAlta && <p className="alerta alerta-error campo-ancho">{errorAlta}</p>}
            <div className="formulario-acciones campo-ancho">
              <button type="button" className="btn-secundario" onClick={() => setModalProductos(null)}>
                Cerrar
              </button>
              <button type="submit" disabled={agregando}>
                {agregando && <span className="spinner" aria-hidden="true" />}
                {agregando ? 'Agregando…' : 'Agregar producto'}
              </button>
            </div>
          </form>
        </Modal>
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
