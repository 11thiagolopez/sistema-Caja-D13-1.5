import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { getProductos } from '../api/productos'
import { buscarClientes } from '../api/clientes'
import { formatoPesos } from '../utils/formato'
import type { Producto, Rol } from '../types/api'

interface Resultado {
  clave: string
  grupo: 'Productos' | 'Clientes' | 'Ventas'
  titulo: string
  detalle: string
  destino: string
}

const MAX_POR_GRUPO = 5

function normalizar(texto: string): string {
  return texto
    .toLowerCase()
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
}

/**
 * Buscador de la barra de arriba: productos (todos los roles), clientes y número de venta (solo
 * ADMIN, que es quien tiene permiso sobre esas pantallas). Los productos se traen una sola vez al
 * primer uso y se filtran acá; los clientes se buscan en el servidor.
 */
export function BuscadorGlobal({ rol }: { rol: Rol | undefined }) {
  const navigate = useNavigate()
  const esAdmin = rol === 'ADMIN'
  const idLista = useId()
  const inputRef = useRef<HTMLInputElement>(null)
  const productosRef = useRef<Producto[] | null>(null)
  const [texto, setTexto] = useState('')
  const [abierto, setAbierto] = useState(false)
  const [resultados, setResultados] = useState<Resultado[]>([])
  const [buscando, setBuscando] = useState(false)
  const [activo, setActivo] = useState(0)

  // Ctrl+K (o Cmd+K) lleva al buscador desde cualquier pantalla.
  useEffect(() => {
    function onKey(e: globalThis.KeyboardEvent) {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        inputRef.current?.focus()
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  useEffect(() => {
    const consulta = texto.trim()
    if (consulta.length < 2) {
      setResultados([])
      setBuscando(false)
      return
    }
    let vigente = true
    setBuscando(true)
    const timer = window.setTimeout(async () => {
      const q = normalizar(consulta)
      const encontrados: Resultado[] = []

      const numero = consulta.replace(/^#/, '')
      if (esAdmin && /^\d+$/.test(numero)) {
        encontrados.push({
          clave: `venta-${numero}`,
          grupo: 'Ventas',
          titulo: `Venta #${numero}`,
          detalle: 'Ver en el historial de ventas',
          destino: `/ventas/historial?venta=${numero}`,
        })
      }

      try {
        if (!productosRef.current) productosRef.current = await getProductos()
        productosRef.current
          .filter(
            (p) =>
              p.activo &&
              (normalizar(p.descripcion).includes(q) ||
                normalizar(p.codigoBarras ?? '').includes(q) ||
                normalizar(p.codigoInterno).includes(q)),
          )
          .slice(0, MAX_POR_GRUPO)
          .forEach((p) =>
            encontrados.push({
              clave: `producto-${p.idProducto}`,
              grupo: 'Productos',
              titulo: p.descripcion,
              detalle: [p.marca, p.precioVenta != null ? formatoPesos(p.precioVenta) : null, `Stock ${p.stockActual}`]
                .filter(Boolean)
                .join(' · '),
              destino: `/productos?buscar=${encodeURIComponent(p.descripcion)}`,
            }),
          )
      } catch {
        // Sin productos (error de red): se muestra lo demás.
      }

      if (esAdmin) {
        try {
          const clientes = await buscarClientes(consulta)
          clientes.slice(0, MAX_POR_GRUPO).forEach((c) =>
            encontrados.push({
              clave: `cliente-${c.idCliente}`,
              grupo: 'Clientes',
              titulo: c.nombre,
              detalle: [c.docNro, c.telefono].filter(Boolean).join(' · ') || 'Cliente',
              destino: `/clientes?id=${c.idCliente}`,
            }),
          )
        } catch {
          // Igual que arriba.
        }
      }

      if (!vigente) return
      setResultados(encontrados)
      setActivo(0)
      setBuscando(false)
    }, 250)
    return () => {
      vigente = false
      window.clearTimeout(timer)
    }
  }, [texto, esAdmin])

  function elegir(resultado: Resultado) {
    navigate(resultado.destino)
    setTexto('')
    setAbierto(false)
    inputRef.current?.blur()
  }

  function onKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'Escape') {
      setAbierto(false)
      inputRef.current?.blur()
      return
    }
    if (!resultados.length) return
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setAbierto(true)
      setActivo((a) => (a + 1) % resultados.length)
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setActivo((a) => (a - 1 + resultados.length) % resultados.length)
    } else if (e.key === 'Enter') {
      e.preventDefault()
      elegir(resultados[activo])
    }
  }

  const mostrarPanel = abierto && texto.trim().length >= 2
  const angosto = typeof window !== 'undefined' && window.matchMedia('(max-width: 768px)').matches
  const placeholder = angosto ? 'Buscar…' : esAdmin ? 'Buscar producto, cliente o venta…' : 'Buscar producto…'

  return (
    <div className="buscador">
      <svg className="buscador-icono" viewBox="0 0 24 24" aria-hidden="true">
        <circle cx="11" cy="11" r="7" />
        <path d="M20 20l-4-4" />
      </svg>
      <input
        ref={inputRef}
        type="search"
        role="combobox"
        aria-label={esAdmin ? 'Buscar producto, cliente o venta' : 'Buscar producto'}
        aria-expanded={mostrarPanel}
        aria-controls={idLista}
        aria-activedescendant={mostrarPanel && resultados[activo] ? `${idLista}-${activo}` : undefined}
        autoComplete="off"
        spellCheck={false}
        placeholder={placeholder}
        value={texto}
        onChange={(e) => {
          setTexto(e.target.value)
          setAbierto(true)
        }}
        onFocus={() => setAbierto(true)}
        // Con un retraso: si no, el clic en un resultado llega cuando el panel ya se cerró.
        onBlur={() => window.setTimeout(() => setAbierto(false), 150)}
        onKeyDown={onKeyDown}
      />
      <kbd className="buscador-atajo" aria-hidden="true">
        Ctrl K
      </kbd>
      {mostrarPanel && (
        <div className="buscador-panel" id={idLista} role="listbox" aria-label="Resultados de la búsqueda">
          {buscando && resultados.length === 0 && <p className="buscador-vacio">Buscando…</p>}
          {!buscando && resultados.length === 0 && (
            <p className="buscador-vacio">No encontramos nada con “{texto.trim()}”.</p>
          )}
          {resultados.map((r, i) => (
            <div key={r.clave}>
              {(i === 0 || resultados[i - 1].grupo !== r.grupo) && (
                <div className="buscador-grupo" aria-hidden="true">
                  {r.grupo}
                </div>
              )}
              <div
                id={`${idLista}-${i}`}
                role="option"
                aria-selected={i === activo}
                className={`buscador-item ${i === activo ? 'buscador-item-activo' : ''}`}
                onMouseDown={(e) => e.preventDefault()}
                onMouseEnter={() => setActivo(i)}
                onClick={() => elegir(r)}
              >
                <span className="buscador-item-titulo">{r.titulo}</span>
                <span className="buscador-item-detalle">{r.detalle}</span>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
