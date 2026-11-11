import { useEffect, useMemo, useState } from 'react'
import { getResumenDia } from '../api/caja'
import { ApiRequestError } from '../api/client'
import { GraficoDona } from '../components/graficos/GraficoDona'
import { TarjetaGrafico } from '../components/graficos/TarjetaGrafico'
import { COLORES_MEDIO_PAGO } from '../components/graficos/tema'
import { formatoFechaHora, formatoPesos, formatoPorcentaje } from '../utils/formato'
import type { ResumenDiaResponse } from '../types/api'

// Una fila del arqueo: cuánto suma o resta en efectivo y en digital (transferencia + tarjeta).
interface FilaArqueo {
  concepto: string
  efectivo: number
  digital: number
}

function filasArqueo(r: ResumenDiaResponse): FilaArqueo[] {
  return [
    { concepto: 'Monto inicial', efectivo: r.montoInicial, digital: 0 },
    { concepto: 'Ventas de mostrador', efectivo: r.ventasEfectivo, digital: r.ventasTransferencia + r.ventasTarjeta },
    {
      concepto: 'Pagos de clientes (cuenta corriente)',
      efectivo: r.pagosClienteEfectivo,
      digital: r.pagosClienteTransferencia + r.pagosClienteTarjeta,
    },
    { concepto: 'Retiros', efectivo: -r.retirosEfectivo, digital: -r.retirosTransferencia },
    {
      concepto: 'Pagos a proveedores de caja',
      efectivo: -r.pagosProveedorEfectivoCaja,
      digital: -r.pagosProveedorTransferenciaCaja,
    },
    { concepto: 'Gastos de caja', efectivo: -r.gastosEfectivoCaja, digital: -r.gastosTransferenciaCaja },
  ]
}

function Monto({ valor }: { valor: number }) {
  if (valor === 0) return <span className="caja-monto-cero">—</span>
  const clase = valor < 0 ? 'caja-monto-negativo' : 'caja-monto-positivo'
  return <span className={clase}>{valor > 0 ? `+ ${formatoPesos(valor)}` : `− ${formatoPesos(-valor)}`}</span>
}

function MarcaResta({ resta }: { resta: boolean }) {
  return resta ? (
    <span className="caja-etiqueta caja-etiqueta-resta">Resta de la caja</span>
  ) : (
    <span className="caja-etiqueta">No toca la caja</span>
  )
}

export function Caja() {
  const [resumen, setResumen] = useState<ResumenDiaResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [cargando, setCargando] = useState(true)

  useEffect(() => {
    getResumenDia()
      .then(setResumen)
      .catch((err) => setError(err instanceof ApiRequestError ? err.message : 'No se pudo cargar el resumen del día'))
      .finally(() => setCargando(false))
  }, [])

  // Lo cobrado por cada medio: ventas de mostrador más pagos de clientes de cuenta corriente.
  const medios = useMemo(() => {
    if (!resumen) return { etiquetas: [], valores: [], colores: [] }
    const todos = [
      { etiqueta: 'Efectivo', valor: resumen.ventasEfectivo + resumen.pagosClienteEfectivo },
      { etiqueta: 'Transferencia', valor: resumen.ventasTransferencia + resumen.pagosClienteTransferencia },
      { etiqueta: 'Tarjeta', valor: resumen.ventasTarjeta + resumen.pagosClienteTarjeta },
    ].filter((m) => m.valor > 0)
    return {
      etiquetas: todos.map((m) => m.etiqueta),
      valores: todos.map((m) => m.valor),
      colores: todos.map((m) => COLORES_MEDIO_PAGO[m.etiqueta]),
    }
  }, [resumen])

  if (cargando) return <p>Cargando...</p>
  if (error) return <p className="error">{error}</p>
  if (!resumen) return null

  const filas = filasArqueo(resumen)
  const pagosCliente =
    resumen.pagosClienteEfectivo + resumen.pagosClienteTransferencia + resumen.pagosClienteTarjeta
  const ventasMostrador = resumen.ventasEfectivo + resumen.ventasTransferencia + resumen.ventasTarjeta
  const totalCobrado = medios.valores.reduce((acc, v) => acc + v, 0)

  return (
    <div className="caja-resumen">
      <h2>Resumen de caja del día</h2>

      <div className="reportes-tarjetas">
        <div className="reportes-tarjeta">
          <span>Vendido hoy</span>
          <strong>{formatoPesos(resumen.totalDelDia)}</strong>
          <small>Mostrador, cobros de clientes y trabajos a domicilio</small>
        </div>
        <div className="reportes-tarjeta">
          <span>Efectivo esperado en caja</span>
          <strong>{formatoPesos(resumen.efectivoFinal)}</strong>
          <small>Lo que tiene que haber en el cajón</small>
        </div>
        <div className="reportes-tarjeta">
          <span>Digital</span>
          <strong>{formatoPesos(resumen.totalDigital)}</strong>
          <small>Transferencias y tarjeta</small>
        </div>
        <div className="reportes-tarjeta caja-tarjeta-destacada">
          <span>Total en caja</span>
          <strong>{formatoPesos(resumen.cajaTotalDelDia)}</strong>
          <small>Efectivo + digital, incluye el monto inicial</small>
        </div>
      </div>

      <p className="caja-aclaracion">
        <strong>Vendido hoy</strong> y <strong>Total en caja</strong> no tienen que coincidir: el total en caja
        suma el monto inicial y resta retiros, pagos y gastos; lo vendido no incluye el monto inicial pero sí los
        trabajos a domicilio, que no pasan por la caja. El detalle está en el arqueo de abajo.
      </p>

      <div className="reportes-grilla">
        <section className="grafico-tarjeta caja-arqueo">
          <header className="grafico-encabezado">
            <h3>Arqueo: cómo se llega al total en caja</h3>
          </header>
          <div className="tabla-scroll">
            <table>
              <thead>
                <tr>
                  <th>Concepto</th>
                  <th>Efectivo</th>
                  <th>Digital</th>
                </tr>
              </thead>
              <tbody>
                {filas.map((f) => (
                  <tr key={f.concepto}>
                    <td>{f.concepto}</td>
                    <td>
                      <Monto valor={f.efectivo} />
                    </td>
                    <td>
                      <Monto valor={f.digital} />
                    </td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr>
                  <td>Saldo esperado</td>
                  <td>{formatoPesos(resumen.efectivoFinal)}</td>
                  <td>{formatoPesos(resumen.totalDigital)}</td>
                </tr>
                <tr className="caja-fila-total">
                  <td>Total en caja</td>
                  <td colSpan={2}>{formatoPesos(resumen.cajaTotalDelDia)}</td>
                </tr>
              </tfoot>
            </table>
          </div>
        </section>

        <TarjetaGrafico
          titulo="Cobrado por medio de pago"
          cargando={false}
          error={null}
          sinDatos={medios.valores.length === 0}
          tabla={{
            columnas: ['Medio de pago', 'Monto', '%'],
            filas: medios.etiquetas.map((e, i) => [
              e,
              formatoPesos(medios.valores[i]),
              formatoPorcentaje(medios.valores[i] / totalCobrado),
            ]),
          }}
        >
          <GraficoDona
            titulo="Cobrado por medio de pago"
            etiquetas={medios.etiquetas}
            valores={medios.valores}
            colores={medios.colores}
            formatoValor={formatoPesos}
            formatoPorcentaje={formatoPorcentaje}
          />
        </TarjetaGrafico>

        <section className="grafico-tarjeta">
          <header className="grafico-encabezado">
            <h3>Total vendido en el día</h3>
          </header>
          <dl className="caja-desglose">
            <div>
              <dt>Ventas de mostrador</dt>
              <dd>{formatoPesos(ventasMostrador)}</dd>
            </div>
            {pagosCliente > 0 && (
              <div>
                <dt>Pagos de clientes de cuenta corriente</dt>
                <dd>{formatoPesos(pagosCliente)}</dd>
              </div>
            )}
            <div>
              <dt>
                Trabajos a domicilio <small>(no pasan por la caja)</small>
              </dt>
              <dd>{formatoPesos(resumen.totalTrabajosDomicilio)}</dd>
            </div>
            <div className="caja-fila-total">
              <dt>Total del día</dt>
              <dd>{formatoPesos(resumen.totalDelDia)}</dd>
            </div>
          </dl>
          {(resumen.ventasEnCuentaCorriente > 0 || resumen.ventasPagadasConSaldo > 0) && (
            <dl className="caja-desglose caja-desglose-info">
              {resumen.ventasEnCuentaCorriente > 0 && (
                <div>
                  <dt>Vendido en cuenta corriente (todavía no cobrado)</dt>
                  <dd>{formatoPesos(resumen.ventasEnCuentaCorriente)}</dd>
                </div>
              )}
              {resumen.ventasPagadasConSaldo > 0 && (
                <div>
                  <dt>Pagado con saldo a favor (no es dinero cobrado)</dt>
                  <dd>{formatoPesos(resumen.ventasPagadasConSaldo)}</dd>
                </div>
              )}
            </dl>
          )}
        </section>
      </div>

      <section className="grafico-tarjeta caja-seccion">
        <header className="grafico-encabezado">
          <h3>Compras y pagos a proveedores</h3>
        </header>
        <div className="tabla-scroll">
          <table>
            <thead>
              <tr>
                <th>Fecha</th>
                <th>Proveedor</th>
                <th>Descripción</th>
                <th>Medio de pago</th>
                <th>Origen</th>
                <th>Monto</th>
                <th>Caja</th>
              </tr>
            </thead>
            <tbody>
              {resumen.movimientosProveedor.length === 0 && (
                <tr>
                  <td colSpan={7} className="caja-vacio">
                    Sin movimientos hoy.
                  </td>
                </tr>
              )}
              {resumen.movimientosProveedor.map((m) => (
                <tr key={m.idMovimiento}>
                  <td>{formatoFechaHora(m.fecha)}</td>
                  <td>{m.nombreProveedor}</td>
                  <td>{m.descripcion ?? '—'}</td>
                  <td>{m.medioPago}</td>
                  <td>{m.origen ?? '—'}</td>
                  <td>{formatoPesos(m.monto)}</td>
                  <td>
                    <MarcaResta resta={m.restaDelSaldoEsperado} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section className="grafico-tarjeta caja-seccion">
        <header className="grafico-encabezado">
          <h3>Gastos operativos</h3>
        </header>
        <div className="tabla-scroll">
          <table>
            <thead>
              <tr>
                <th>Registrado</th>
                <th>Nombre</th>
                <th>Categoría</th>
                <th>Medio de pago</th>
                <th>Origen</th>
                <th>Importe</th>
                <th>Caja</th>
              </tr>
            </thead>
            <tbody>
              {resumen.gastos.length === 0 && (
                <tr>
                  <td colSpan={7} className="caja-vacio">
                    Sin gastos hoy.
                  </td>
                </tr>
              )}
              {resumen.gastos.map((g) => (
                <tr key={g.idGasto}>
                  <td>{formatoFechaHora(g.fecha)}</td>
                  <td>{g.nombre}</td>
                  <td>{g.categoria ?? '—'}</td>
                  <td>{g.medioPago ?? '—'}</td>
                  <td>{g.origen ?? '—'}</td>
                  <td>{formatoPesos(g.importe)}</td>
                  <td>
                    <MarcaResta resta={g.restaDelSaldoEsperado} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  )
}
