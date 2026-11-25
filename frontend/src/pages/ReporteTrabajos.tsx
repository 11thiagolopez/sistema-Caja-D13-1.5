import { useCallback, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { getReporteTrabajos } from '../api/reportes'
import { FiltroPeriodo } from '../components/graficos/FiltroPeriodo'
import { GraficoBarras } from '../components/graficos/GraficoBarras'
import { GraficoDona } from '../components/graficos/GraficoDona'
import { GraficoLineas } from '../components/graficos/GraficoLineas'
import { TarjetaGrafico } from '../components/graficos/TarjetaGrafico'
import { COLOR_OTRO_MEDIO_PAGO, COLORES_MEDIO_PAGO, TEMA } from '../components/graficos/tema'
import { useDatosReporte } from '../hooks/useDatosReporte'
import { rangoDelMes, type Rango } from '../utils/date'
import {
  formatoDiaCorto,
  formatoDiaLargo,
  formatoDuracion,
  formatoEntero,
  formatoFechaHora,
  formatoMes,
  formatoPesos,
  formatoPesosEje,
  formatoPorcentaje,
} from '../utils/formato'
import type { PuntoGraficoDTO, ReporteTrabajosResponse } from '../types/api'

const COLORES_REPARTO = [COLOR_OTRO_MEDIO_PAGO, TEMA.serie]

function Tarjetas({ datos, cargando }: { datos: ReporteTrabajosResponse | null; cargando: boolean }) {
  const pesos = (valor: number | undefined) => (valor != null ? formatoPesos(valor) : cargando ? '...' : '—')

  return (
    <div className={cargando ? 'reportes-tarjetas grafico-actualizando' : 'reportes-tarjetas'} aria-busy={cargando}>
      <div className="reportes-tarjeta">
        <span>Total cobrado</span>
        <strong>{pesos(datos?.total)}</strong>
        <small>{datos ? `${formatoEntero(datos.cantidadTrabajos)} trabajos` : ' '}</small>
      </div>
      <div className="reportes-tarjeta">
        <span>Se llevaron los técnicos</span>
        <strong>{pesos(datos?.paraTecnicos)}</strong>
        <small>Solo sobre la mano de obra</small>
      </div>
      <div className="reportes-tarjeta">
        <span>Quedó para el negocio</span>
        <strong>{pesos(datos?.paraNegocio)}</strong>
        <small>Total menos técnicos</small>
      </div>
      <div className="reportes-tarjeta">
        <span>Ganancia del negocio</span>
        <strong>{pesos(datos?.gananciaNegocio)}</strong>
        <small>{datos ? `Restando ${formatoPesos(datos.costoArticulos)} de costo de artículos` : ' '}</small>
      </div>
      <div className="reportes-tarjeta">
        <span>Tiempo promedio por trabajo</span>
        <strong>
          {datos?.tiempoPromedioMinutos != null ? formatoDuracion(datos.tiempoPromedioMinutos) : cargando ? '...' : '—'}
        </strong>
        <small>
          {datos
            ? datos.trabajosConTiempo > 0
              ? `De "En camino" a "Terminé", sobre ${formatoEntero(datos.trabajosConTiempo)} trabajos`
              : 'Sin trabajos con "En camino" y "Terminé" registrados'
            : ' '}
        </small>
      </div>
      <div className="reportes-tarjeta">
        <span>Mano de obra</span>
        <strong>{pesos(datos?.manoDeObra)}</strong>
      </div>
      <div className="reportes-tarjeta">
        <span>Artículos</span>
        <strong>{pesos(datos?.articulos)}</strong>
      </div>
    </div>
  )
}

function CobradoPorDia({ puntos, cargando, error }: { puntos: PuntoGraficoDTO[]; cargando: boolean; error: string | null }) {
  const etiquetas = useMemo(() => puntos.map((punto) => punto.etiqueta), [puntos])
  const valores = useMemo(() => puntos.map((punto) => punto.valor), [puntos])

  return (
    <TarjetaGrafico
      titulo="Cobrado por día"
      cargando={cargando}
      error={error}
      sinDatos={!valores.some((valor) => valor > 0)}
      tabla={{
        columnas: ['Día', 'Total cobrado'],
        filas: puntos.map((punto) => [formatoDiaLargo(punto.etiqueta), formatoPesos(punto.valor)]),
      }}
    >
      <GraficoLineas
        titulo="Cobrado por día"
        etiquetas={etiquetas}
        valores={valores}
        indiceMejor={-1}
        indicePeor={-1}
        formatoValor={formatoPesos}
        formatoEje={formatoPesosEje}
        formatoEtiquetaEje={formatoDiaCorto}
        formatoEtiquetaTooltip={formatoDiaLargo}
      />
    </TarjetaGrafico>
  )
}

function Dona({
  titulo,
  puntos,
  colores,
  columna,
  cargando,
  error,
}: {
  titulo: string
  puntos: PuntoGraficoDTO[]
  colores: string[]
  columna: string
  cargando: boolean
  error: string | null
}) {
  const etiquetas = useMemo(() => puntos.map((punto) => punto.etiqueta), [puntos])
  const valores = useMemo(() => puntos.map((punto) => punto.valor), [puntos])
  const total = valores.reduce((acc, valor) => acc + valor, 0)

  return (
    <TarjetaGrafico
      titulo={titulo}
      cargando={cargando}
      error={error}
      sinDatos={total === 0}
      tabla={{
        columnas: [columna, 'Monto', '%'],
        filas: puntos.map((punto) => [punto.etiqueta, formatoPesos(punto.valor), formatoPorcentaje(punto.valor / total)]),
      }}
    >
      <GraficoDona
        titulo={titulo}
        etiquetas={etiquetas}
        valores={valores}
        colores={colores}
        formatoValor={formatoPesos}
        formatoPorcentaje={formatoPorcentaje}
      />
    </TarjetaGrafico>
  )
}

function PorTecnico({ datos, cargando, error }: { datos: ReporteTrabajosResponse | null; cargando: boolean; error: string | null }) {
  const filas = useMemo(() => datos?.porTecnico ?? [], [datos])
  const etiquetas = useMemo(() => filas.map((fila) => fila.nombre), [filas])
  const valores = useMemo(() => filas.map((fila) => fila.paraTecnico), [filas])

  return (
    <TarjetaGrafico
      titulo="Por técnico"
      cargando={cargando}
      error={error}
      sinDatos={filas.length === 0}
      tabla={{
        columnas: ['Técnico', 'Trabajos', 'Mano de obra', 'Se llevó', 'Para el negocio', 'Tiempo promedio'],
        filas: filas.map((fila) => [
          fila.nombre,
          formatoEntero(fila.cantidadTrabajos),
          formatoPesos(fila.manoDeObra),
          formatoPesos(fila.paraTecnico),
          formatoPesos(fila.paraNegocio),
          fila.tiempoPromedioMinutos != null ? formatoDuracion(fila.tiempoPromedioMinutos) : '—',
        ]),
      }}
    >
      <GraficoBarras
        horizontal
        titulo="Lo que se llevó cada técnico"
        etiquetas={etiquetas}
        valores={valores}
        formatoValor={formatoPesos}
      />
    </TarjetaGrafico>
  )
}

function PorMes({ datos, cargando, error }: { datos: ReporteTrabajosResponse | null; cargando: boolean; error: string | null }) {
  const meses = useMemo(() => datos?.porMes ?? [], [datos])
  const etiquetas = useMemo(() => meses.map((m) => formatoMes(m.mes)), [meses])
  const valores = useMemo(() => meses.map((m) => m.total), [meses])

  return (
    <TarjetaGrafico
      titulo="Trabajos por mes"
      cargando={cargando}
      error={error}
      sinDatos={!meses.some((m) => m.cantidadTrabajos > 0)}
      tabla={{
        columnas: ['Mes', 'Trabajos', 'Cobrado', 'Se llevaron los técnicos'],
        filas: meses.map((m) => [
          formatoMes(m.mes),
          formatoEntero(m.cantidadTrabajos),
          formatoPesos(m.total),
          formatoPesos(m.paraTecnicos),
        ]),
      }}
    >
      <GraficoBarras titulo="Cobrado por mes" etiquetas={etiquetas} valores={valores} formatoValor={formatoPesos} formatoEje={formatoPesosEje} />
    </TarjetaGrafico>
  )
}

/** Resumen para liquidar comisiones: un renglón por técnico y mes (por fecha de cobro). */
function ComisionesPorMes({ datos }: { datos: ReporteTrabajosResponse | null }) {
  if (!datos) return null
  return (
    <section>
      <h3>Comisiones por mes</h3>
      <p className="reportes-aclaracion">
        Lo que le corresponde a cada técnico, por mes de cobro. Se calcula solo al cerrar y cobrar cada trabajo, con el %
        que tenía el técnico en ese momento.
      </p>
      {datos.comisionesPorMes.length === 0 ? (
        <p>No hay comisiones en este período.</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th>Mes</th>
              <th>Técnico</th>
              <th>Trabajos</th>
              <th>Mano de obra</th>
              <th>Le corresponde</th>
            </tr>
          </thead>
          <tbody>
            {datos.comisionesPorMes.map((c) => (
              <tr key={`${c.mes}-${c.idEmpleado}`}>
                <td>{formatoMes(c.mes)}</td>
                <td>{c.nombre}</td>
                <td>{formatoEntero(c.cantidadTrabajos)}</td>
                <td>{formatoPesos(c.manoDeObra)}</td>
                <td>
                  <strong>{formatoPesos(c.paraTecnico)}</strong>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}

export function ReporteTrabajos() {
  const [rango, setRango] = useState<Rango>(rangoDelMes)
  const cargar = useCallback(() => getReporteTrabajos(rango.desde, rango.hasta), [rango])
  const { datos, error, cargando } = useDatosReporte(cargar)

  const reparto = useMemo<PuntoGraficoDTO[]>(
    () =>
      datos
        ? [
            { etiqueta: 'Técnicos', valor: datos.paraTecnicos },
            { etiqueta: 'Negocio', valor: datos.paraNegocio },
          ]
        : [],
    [datos],
  )
  const medios = useMemo(() => datos?.porMedioPago ?? [], [datos])
  const coloresMedios = useMemo(
    () => medios.map((punto) => COLORES_MEDIO_PAGO[punto.etiqueta] ?? COLOR_OTRO_MEDIO_PAGO),
    [medios],
  )
  const dias = useMemo(() => datos?.porDia ?? [], [datos])

  return (
    <div className="reportes">
      <h2>Reporte de trabajos a domicilio</h2>

      <FiltroPeriodo rango={rango} onCambiar={setRango} />

      <p className="reportes-aclaracion">Trabajos cobrados, por el día en que se cobraron. No forman parte del arqueo de caja.</p>

      {error && <p className="error">{error}</p>}

      <Tarjetas datos={datos} cargando={cargando} />

      <div className="reportes-grilla">
        <CobradoPorDia puntos={dias} cargando={cargando} error={error} />
        <Dona titulo="Técnicos y negocio" columna="Parte" puntos={reparto} colores={COLORES_REPARTO} cargando={cargando} error={error} />
        <PorTecnico datos={datos} cargando={cargando} error={error} />
        <Dona titulo="Medios de pago" columna="Medio" puntos={medios} colores={coloresMedios} cargando={cargando} error={error} />
        <PorMes datos={datos} cargando={cargando} error={error} />
      </div>

      <ComisionesPorMes datos={datos} />

      <section>
        <h3>Trabajos cobrados</h3>
        {datos?.trabajos.length === 0 && <p>No hay trabajos cobrados en este período.</p>}
        {datos && datos.trabajos.length > 0 && (
          <div className="tabla-scroll">
          <table>
            <thead>
              <tr>
                <th>#</th>
                <th>Cobrado</th>
                <th>Dirección</th>
                <th>Cliente</th>
                <th>Técnico</th>
                <th>Medio</th>
                <th>Total</th>
                <th>Mano de obra</th>
                <th>Artículos</th>
                <th>Técnico se llevó</th>
                <th>Para el negocio</th>
                <th>Duración</th>
              </tr>
            </thead>
            <tbody>
              {datos.trabajos.map((trabajo) => (
                <tr key={trabajo.idVenta}>
                  <td>
                    <Link to={`/ventas/domicilio?id=${trabajo.idVenta}`}>{trabajo.idVenta}</Link>
                  </td>
                  <td>{formatoFechaHora(trabajo.fecha)}</td>
                  <td>{trabajo.direccion ?? '—'}</td>
                  <td>{trabajo.cliente ?? '—'}</td>
                  <td>{trabajo.tecnico ?? '—'}</td>
                  <td>{trabajo.medioPago}</td>
                  <td>{formatoPesos(trabajo.total)}</td>
                  <td>{formatoPesos(trabajo.manoDeObra)}</td>
                  <td>{formatoPesos(trabajo.articulos)}</td>
                  <td>{formatoPesos(trabajo.paraTecnico)}</td>
                  <td>{formatoPesos(trabajo.paraNegocio)}</td>
                  <td>{trabajo.minutos != null ? formatoDuracion(trabajo.minutos) : '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
          </div>
        )}
      </section>
    </div>
  )
}
