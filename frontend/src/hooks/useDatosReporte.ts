import { useEffect, useState } from 'react'
import { ApiRequestError } from '../api/client'

// Cada gráfico carga por su cuenta: si falla uno, los demás se siguen viendo. `cargar` tiene que
// venir de useCallback, cambia cuando cambia el filtro. Se conservan los datos anteriores mientras
// llega la respuesta nueva, para que la pantalla no salte, y se descarta cualquier respuesta de un
// filtro que ya no es el actual.
export function useDatosReporte<T>(cargar: () => Promise<T>) {
  const [datos, setDatos] = useState<T | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [cargando, setCargando] = useState(true)

  useEffect(() => {
    let vigente = true
    setCargando(true)
    setError(null)
    cargar()
      .then((resultado) => {
        if (vigente) setDatos(resultado)
      })
      .catch((err) => {
        if (vigente) setError(err instanceof ApiRequestError ? err.message : 'No se pudieron cargar los datos')
      })
      .finally(() => {
        if (vigente) setCargando(false)
      })
    return () => {
      vigente = false
    }
  }, [cargar])

  return { datos, error, cargando }
}
