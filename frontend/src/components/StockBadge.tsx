// Rangos exactos pedidos por el dueño: verde >=5, amarillo 3-4, naranja 1-2, rojo 0. Compartido
// entre Productos.tsx (tabla + filtro) y Presupuestos.tsx (stock del ítem seleccionado) — el
// único lugar donde el stock se puede mostrar coloreado, ya que en el resto de la app (Cobros,
// Trabajo a domicilio, Compras) el producto se busca vía <datalist>, que no soporta HTML/CSS por
// opción.
export type ColorStock = 'verde' | 'amarillo' | 'naranja' | 'rojo'

export function colorStock(stock: number): ColorStock {
  if (stock <= 0) return 'rojo'
  if (stock <= 2) return 'naranja'
  if (stock <= 4) return 'amarillo'
  return 'verde'
}

export const ETIQUETA_COLOR_STOCK: Record<ColorStock, string> = {
  verde: 'Stock alto (5 o más)',
  amarillo: 'Stock medio (3-4)',
  naranja: 'Stock bajo (1-2)',
  rojo: 'Sin stock (0)',
}

interface StockBadgeProps {
  stock: number
}

export function StockBadge({ stock }: StockBadgeProps) {
  const color = colorStock(stock)
  return (
    <span className={`stock-badge stock-${color}`} title={ETIQUETA_COLOR_STOCK[color]}>
      {stock}
    </span>
  )
}
