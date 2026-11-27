import type { ReactNode } from 'react'

interface EncabezadoPaginaProps {
  titulo: string
  /** Al lado del título, por ejemplo el estado de la caja. */
  etiqueta?: ReactNode
  subtitulo?: ReactNode
  /** Botones arriba a la derecha. Una acción que está acá no se repite en el menú lateral. */
  acciones?: ReactNode
}

export function EncabezadoPagina({ titulo, etiqueta, subtitulo, acciones }: EncabezadoPaginaProps) {
  return (
    <div className="encabezado-pagina">
      <div>
        <div className="encabezado-pagina-titulo">
          <h2>{titulo}</h2>
          {etiqueta}
        </div>
        {subtitulo && <p className="encabezado-pagina-subtitulo">{subtitulo}</p>}
      </div>
      {acciones && <div className="encabezado-pagina-acciones">{acciones}</div>}
    </div>
  )
}
