import { useState } from 'react'
import { descargarPdfFactura } from '../api/facturas'
import { numeroComprobante } from '../utils/formato'
import type { FacturaFiscalResponse } from '../types/api'

interface BotonDescargarFacturaProps {
  factura: Pick<FacturaFiscalResponse, 'idVenta' | 'puntoVenta' | 'numero'>
  onError: (mensaje: string) => void
}

export function BotonDescargarFactura({ factura, onError }: BotonDescargarFacturaProps) {
  const [descargando, setDescargando] = useState(false)

  async function onDescargar() {
    setDescargando(true)
    try {
      await descargarPdfFactura(factura.idVenta, numeroComprobante(factura.puntoVenta, factura.numero))
    } catch (err) {
      onError(err instanceof Error ? err.message : 'No se pudo descargar la factura')
    } finally {
      setDescargando(false)
    }
  }

  return (
    <button type="button" className="boton-secundario" onClick={onDescargar} disabled={descargando}>
      {descargando && <span className="spinner" />}
      {descargando ? 'Descargando...' : 'Descargar PDF'}
    </button>
  )
}
