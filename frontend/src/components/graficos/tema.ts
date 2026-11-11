export const TEMA = {
  superficie: '#ffffff',
  textoPrimario: '#0b0b0b',
  textoSecundario: '#52514e',
  grilla: '#e1e0d9',
  serie: '#2a78d6',
  serieLavado: 'rgba(42, 120, 214, 0.1)',
  mejor: '#0ca30c',
  peor: '#d03b3b',
  sinEspecificar: '#898781',
}

// El color sigue al medio de pago, no a su posición: cambiar el filtro nunca repinta los demás.
export const COLORES_MEDIO_PAGO: Record<string, string> = {
  Efectivo: '#2a78d6',
  Transferencia: '#eb6834',
  Tarjeta: '#1baf7a',
  'Tarjeta de crédito': '#0e8a5f',
  'Tarjeta de débito': '#5cc99b',
  'Saldo cuenta corriente': '#eda100',
  'Sin especificar': TEMA.sinEspecificar,
}

export const COLOR_OTRO_MEDIO_PAGO = '#6250d6'
