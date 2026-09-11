import { useRef, useState } from 'react'

interface ItemDeCarrito {
  idProducto?: number
  cantidad: number
  precioUnitario: number
}

// clientId: id local generado al agregar la fila, estable mientras la fila exista en el carrito
// (a diferencia del índice del array, no se corre si se agrega/saca otra fila antes). Sirve como
// key de React en la tabla — hoy las filas no tienen inputs propios así que usar el índice no
// causaba bugs visibles, pero es frágil ante un futuro cambio (ver auditoría).
type ItemConId<T> = T & { clientId: number }

// Carrito compartido por Cobros, Presupuestos y Trabajo a domicilio (antes cada página tenía su
// propia copia de esta misma lógica): agregar un producto ya presente en el carrito suma la
// cantidad en vez de duplicar la fila; un ítem manual (sin idProducto, ej. mano de obra o un
// trabajo de precio libre) siempre se agrega como fila nueva.
export function useCarrito<T extends ItemDeCarrito>() {
  const [carrito, setCarrito] = useState<ItemConId<T>[]>([])
  const siguienteId = useRef(1)

  function agregarOFusionar(item: T) {
    setCarrito((actual) => {
      if (item.idProducto != null) {
        const indiceExistente = actual.findIndex((i) => i.idProducto === item.idProducto)
        if (indiceExistente >= 0) {
          const copia = [...actual]
          copia[indiceExistente] = {
            ...copia[indiceExistente],
            cantidad: copia[indiceExistente].cantidad + item.cantidad,
          }
          return copia
        }
      }
      return [...actual, { ...item, clientId: siguienteId.current++ }]
    })
  }

  // Reemplazo completo del carrito (ej. al reabrir un trabajo a domicilio existente): asigna
  // clientId nuevos, no reutiliza los que tenía la venta guardada.
  function reemplazar(items: T[]) {
    setCarrito(items.map((item) => ({ ...item, clientId: siguienteId.current++ })))
  }

  function quitar(index: number) {
    setCarrito((actual) => actual.filter((_, i) => i !== index))
  }

  function vaciar() {
    setCarrito([])
  }

  const total = carrito.reduce((acc, item) => acc + item.cantidad * item.precioUnitario, 0)

  return { carrito, agregarOFusionar, reemplazar, quitar, vaciar, total }
}
