import type { ReactNode } from 'react'

export interface TablaGrafico {
  columnas: string[]
  filas: string[][]
}

interface Props {
  titulo: string
  cargando: boolean
  error: string | null
  sinDatos: boolean
  acciones?: ReactNode
  tabla?: TablaGrafico
  children: ReactNode
}

// Marco común de los gráficos: resuelve los estados de carga, error y sin datos, y ofrece los mismos
// valores en una tabla para leerlos sin depender del tooltip.
export function TarjetaGrafico({ titulo, cargando, error, sinDatos, acciones, tabla, children }: Props) {
  return (
    <section className="grafico-tarjeta" aria-busy={cargando}>
      <header className="grafico-encabezado">
        <h3>{titulo}</h3>
        {acciones}
      </header>

      {error && <p className="error">{error}</p>}
      {!error && cargando && sinDatos && (
        <p className="grafico-estado">
          <span className="spinner" />
          Cargando...
        </p>
      )}
      {!error && !cargando && sinDatos && <p className="grafico-estado">Sin datos para este período</p>}

      {!error && !sinDatos && (
        <div className={cargando ? 'grafico-contenido grafico-actualizando' : 'grafico-contenido'}>
          {children}
          {tabla && (
            <details className="grafico-tabla">
              <summary>Ver como tabla</summary>
              <table>
                <thead>
                  <tr>
                    {tabla.columnas.map((columna) => (
                      <th key={columna}>{columna}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {tabla.filas.map((fila, i) => (
                    <tr key={i}>
                      {fila.map((celda, j) => (
                        <td key={j}>{celda}</td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </details>
          )}
        </div>
      )}
    </section>
  )
}
