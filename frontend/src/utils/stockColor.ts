// Rangos exactos pedidos por el dueño: verde >=5, amarillo 3-4, naranja 1-2, rojo 0. El color se
// pinta en el fondo de la FILA entera (no en el número de stock) para que se note de un vistazo
// rápido recorriendo la tabla, sin tener que enfocar la columna de stock puntualmente. Compartido
// entre Productos.tsx (tabla + filtro) y Presupuestos.tsx (fila del carrito) — el único lugar
// donde el stock se puede mostrar coloreado, ya que en el resto de la app (Cobros, Trabajo a
// domicilio, Compras) el producto se busca vía <datalist>, que no soporta HTML/CSS por opción.
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

export function claseFilaStock(stock: number): string {
  return `fila-stock-${colorStock(stock)}`
}
