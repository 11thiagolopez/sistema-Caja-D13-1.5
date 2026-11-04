const PESOS_CUIT = [5, 4, 3, 2, 7, 6, 5, 4, 3, 2]

export function esCuitValido(cuit: string): boolean {
  if (!/^\d{11}$/.test(cuit)) return false
  const suma = PESOS_CUIT.reduce((acc, peso, i) => acc + Number(cuit[i]) * peso, 0)
  const resto = 11 - (suma % 11)
  return (resto === 11 ? 0 : resto) === Number(cuit[10])
}

// Códigos de CondicionIVAReceptorId de ARCA (RG 5616).
export const CONSUMIDOR_FINAL = 5

export const CONDICIONES_IVA: { id: number; nombre: string }[] = [
  { id: 5, nombre: 'Consumidor Final' },
  { id: 6, nombre: 'Responsable Monotributo' },
  { id: 1, nombre: 'IVA Responsable Inscripto' },
  { id: 4, nombre: 'IVA Sujeto Exento' },
  { id: 13, nombre: 'Monotributista Social' },
  { id: 16, nombre: 'Monotributo Trabajador Independiente Promovido' },
  { id: 7, nombre: 'Sujeto No Categorizado' },
  { id: 8, nombre: 'Proveedor del Exterior' },
  { id: 9, nombre: 'Cliente del Exterior' },
  { id: 10, nombre: 'IVA Liberado – Ley 19.640' },
  { id: 15, nombre: 'IVA No Alcanzado' },
]

// Las cuatro que ofrece el formulario. Un cliente o el padrón pueden traer otra de CONDICIONES_IVA:
// se conserva y se muestra, pero no se elige a mano.
export const CONDICIONES_FORMULARIO = [
  { id: CONSUMIDOR_FINAL, nombre: 'Consumidor Final' },
  { id: 1, nombre: 'Responsable Inscripto' },
  { id: 6, nombre: 'Monotributista' },
  { id: 4, nombre: 'Exento' },
]

export function nombreCondicionIva(id: number | null): string {
  if (id == null) return 'Sin indicar'
  const propia = CONDICIONES_FORMULARIO.find((c) => c.id === id) ?? CONDICIONES_IVA.find((c) => c.id === id)
  return propia?.nombre ?? `Código ${id}`
}

export function etiquetaDocumento(docTipo: number, docNro: string | null): string {
  if (docTipo === 99 || !docNro) return 'Sin documento'
  return `${docTipo === 80 ? 'CUIT' : 'DNI'} ${docNro}`
}
