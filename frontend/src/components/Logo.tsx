// Logo de oNce: el ícono (cuadrado azul con "o" y "N") y la palabra "oNce", donde la N es el mismo
// trazo del ícono. Medidas y colores del diseño de referencia (ver DESIGN.md).

const TRAZO_N = 'M6 0h12v72h-12z M46 0h12v72h-12z M22 7.2v21.6l20 36v-21.6z'

export function LogoIcono({ tamano = 40 }: { tamano?: number }) {
  const escala = tamano / 40
  return (
    <span
      className="logo-icono"
      aria-hidden="true"
      style={{ width: tamano, height: tamano, borderRadius: 10 * escala, gap: 3 * escala }}
    >
      <span
        className="logo-icono-o"
        style={{ width: 13 * escala, height: 13 * escala, borderWidth: 3.5 * escala }}
      />
      <svg viewBox="0 0 64 72" style={{ width: 15 * escala, height: 17 * escala }} fill="#fff">
        <path d={TRAZO_N} />
      </svg>
    </span>
  )
}

/** La palabra "oNce". `claro` para fondo oscuro (menú, login). */
export function LogoTexto({ tamano = 26, claro = false }: { tamano?: number; claro?: boolean }) {
  return (
    // Todas letras de verdad (la N en azul, como los colores de Google): así quedan siempre sobre
    // la misma línea. Antes la N era un dibujo aparte y no alineaba con "o" y "ce".
    <span className={`logo-texto ${claro ? 'logo-texto-claro' : ''}`} style={{ fontSize: tamano }}>
      o<span className="logo-texto-n">N</span>ce
    </span>
  )
}

/**
 * Marca del menú lateral: "SISTEMA D13" y debajo, alineado al final, "by" + el logo de oNce.
 */
export function MarcaSistema() {
  return (
    <div className="marca-sistema" role="img" aria-label="Sistema D13 by oNce">
      <span className="marca-sistema-nombre" aria-hidden="true">
        SISTEMA D13
      </span>
      <span className="marca-sistema-by" aria-hidden="true">
        by
        <LogoIcono tamano={18} />
        <LogoTexto tamano={15} claro />
      </span>
    </div>
  )
}

export function Logo({ tamano = 40, claro = false }: { tamano?: number; claro?: boolean }) {
  return (
    <span className="logo" role="img" aria-label="oNce">
      <LogoIcono tamano={tamano} />
      <LogoTexto tamano={tamano * 0.65} claro={claro} />
    </span>
  )
}
