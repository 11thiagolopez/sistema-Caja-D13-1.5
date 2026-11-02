import { useCallback, useMemo, useState } from 'react'
import {
  getBalance,
  getMarcasGrafico,
  getMediosPago,
  getProductosGrafico,
  getResumenVentas,
  getVentasPorDia,
  getVentasPorDiaSemana,
} from '../api/reportes'
import { GraficoBarras } from '../components/graficos/GraficoBarras'
import { GraficoDona } from '../components/graficos/GraficoDona'
import { GraficoLineas } from '../components/graficos/GraficoLineas'
import { TarjetaGrafico } from '../components/graficos/TarjetaGrafico'
import { COLOR_OTRO_MEDIO_PAGO, COLORES_MEDIO_PAGO, TEMA } from '../components/graficos/tema'
import { useDatosReporte } from '../hooks/useDatosReporte'
import { hoyIso, restarDiasIso } from '../utils/date'
import {
  formatoDiaCorto,
  formatoDiaLargo,
  formatoEntero,
  formatoPesos,
  formatoPesosEje,
  formatoPorcentaje,
} from '../utils/formato'
import type { OrdenRanking, RankingItemDTO } from '../types/api'

type Periodo = 'hoy' | 'semana' | 'mes' | 'personalizado'

interface Rango {
  desde: string
  hasta: string
}

const PERIODOS: { id: Periodo; nombre: string }[] = [
  { id: 'hoy', nombre: 'Hoy' },
  { id: 'semana', nombre: 'Últimos 7 días' },
  { id: 'mes', nombre: 'Este mes' },
  { id: 'personalizado', nombre: 'Personalizado' },
]

function rangoDe(periodo: Exclude<Periodo, 'personalizado'>): Rango {
  const hoy = hoyIso()
  if (periodo === 'hoy') return { desde: hoy, hasta: hoy }
  if (periodo === 'semana') return { desde: restarDiasIso(hoy, 6), hasta: hoy }
  return { desde: hoy.slice(0, 8) + '01', hasta: hoy }
}

function ToggleOrden({ orden, onCambiar }: { orden: OrdenRanking; onCambiar: (orden: OrdenRanking) => void }) {
  return (
    <div className="reportes-opciones" role="group" aria-label="Ver por">
      <button type="button" aria-pressed={orden === 'monto'} onClick={() => onCambiar('monto')}>
        $
      </button>
      <button type="button" aria-pressed={orden === 'unidades'} onClick={() => onCambiar('unidades')}>
        Unidades
      </button>
    </div>
  )
}

function textoRanking(item: RankingItemDTO): string {
  return `${formatoPesos(item.monto)} · ${formatoEntero(item.unidades)} u.`
}

function tablaRanking(columna: string, items: RankingItemDTO[]) {
  return {
    columnas: [columna, 'Monto', 'Unidades'],
    filas: items.map((item) => [item.etiqueta, formatoPesos(item.monto), formatoEntero(item.unidades)]),
  }
}

function BarrasRanking({ titulo, items, orden }: { titulo: string; items: RankingItemDTO[]; orden: OrdenRanking }) {
  const etiquetas = useMemo(() => items.map((item) => item.etiqueta), [items])
  const valores = useMemo(() => items.map((item) => item.valor), [items])
  const textoTooltip = useCallback((indice: number) => textoRanking(items[indice]), [items])

  return (
    <GraficoBarras
      horizontal
      titulo={titulo}
      etiquetas={etiquetas}
      valores={valores}
      formatoValor={orden === 'monto' ? formatoPesos : formatoEntero}
      textoTooltip={textoTooltip}
    />
  )
}

function MarcasMasVendidas({ rango }: { rango: Rango }) {
  const [orden, setOrden] = useState<OrdenRanking>('monto')
  const cargar = useCallback(() => getMarcasGrafico(rango.desde, rango.hasta, orden), [rango, orden])
  const { datos, error, cargando } = useDatosReporte(cargar)
  const mejores = datos?.mejores ?? []
  const peores = datos?.peores ?? []

  return (
    <TarjetaGrafico
      titulo="Marcas más vendidas"
      cargando={cargando}
      error={error}
      sinDatos={mejores.length === 0}
      acciones={<ToggleOrden orden={orden} onCambiar={setOrden} />}
      tabla={tablaRanking('Marca', mejores)}
    >
      <BarrasRanking titulo="Marcas más vendidas" items={mejores} orden={orden} />
      {peores.length > 0 && (
        <div className="reportes-peores">
          <h4>Menos vendidas (entre las que tuvieron ventas)</h4>
          <ol>
            {peores.map((item) => (
              <li key={item.etiqueta}>
                <span>{item.etiqueta}</span>
                <span>{textoRanking(item)}</span>
              </li>
            ))}
          </ol>
        </div>
      )}
    </TarjetaGrafico>
  )
}

function ProductosMasVendidos({ rango }: { rango: Rango }) {
  const [orden, setOrden] = useState<OrdenRanking>('monto')
  const cargar = useCallback(() => getProductosGrafico(rango.desde, rango.hasta, orden), [rango, orden])
  const { datos, error, cargando } = useDatosReporte(cargar)
  const items = datos ?? []

  return (
    <TarjetaGrafico
      titulo="Productos más vendidos"
      cargando={cargando}
      error={error}
      sinDatos={items.length === 0}
      acciones={<ToggleOrden orden={orden} onCambiar={setOrden} />}
      tabla={tablaRanking('Producto', items)}
    >
      <BarrasRanking titulo="Productos más vendidos" items={items} orden={orden} />
    </TarjetaGrafico>
  )
}

function VentasPorDia({ rango }: { rango: Rango }) {
  const cargar = useCallback(() => getVentasPorDia(rango.desde, rango.hasta), [rango])
  const { datos, error, cargando } = useDatosReporte(cargar)
  const puntos = useMemo(() => datos?.puntos ?? [], [datos])
  const etiquetas = useMemo(() => puntos.map((punto) => punto.etiqueta), [puntos])
  const valores = useMemo(() => puntos.map((punto) => punto.valor), [puntos])
  const mejor = datos?.mejorDia ?? null
  const peor = datos?.peorDia ?? null
  const hayVentas = mejor !== null
  const mismoDia = mejor?.etiqueta === peor?.etiqueta

  return (
    <TarjetaGrafico
      titulo="Ventas por día"
      cargando={cargando}
      error={error}
      sinDatos={!hayVentas}
      tabla={{
        columnas: ['Día', 'Total vendido'],
        filas: puntos.map((punto) => [formatoDiaLargo(punto.etiqueta), formatoPesos(punto.valor)]),
      }}
    >
      <GraficoLineas
        titulo="Ventas por día"
        etiquetas={etiquetas}
        valores={valores}
        indiceMejor={mejor ? etiquetas.indexOf(mejor.etiqueta) : -1}
        indicePeor={peor && !mismoDia ? etiquetas.indexOf(peor.etiqueta) : -1}
        formatoValor={formatoPesos}
        formatoEje={formatoPesosEje}
        formatoEtiquetaEje={formatoDiaCorto}
        formatoEtiquetaTooltip={formatoDiaLargo}
      />
      {mejor && (
        <p className="reportes-extremos">
          <span>
            <span aria-hidden="true" style={{ color: TEMA.mejor }}>▲</span> Mejor día:{' '}
            <strong>{formatoDiaLargo(mejor.etiqueta)}</strong> ({formatoPesos(mejor.valor)})
          </span>
          {peor && !mismoDia && (
            <span>
              <span aria-hidden="true" style={{ color: TEMA.peor }}>◆</span> Peor día:{' '}
              <strong>{formatoDiaLargo(peor.etiqueta)}</strong> ({formatoPesos(peor.valor)})
            </span>
          )}
        </p>
      )}
    </TarjetaGrafico>
  )
}

function VentasPorDiaSemana({ rango }: { rango: Rango }) {
  const cargar = useCallback(() => getVentasPorDiaSemana(rango.desde, rango.hasta), [rango])
  const { datos, error, cargando } = useDatosReporte(cargar)
  const puntos = useMemo(() => datos ?? [], [datos])
  const etiquetas = useMemo(() => puntos.map((punto) => punto.etiqueta), [puntos])
  const valores = useMemo(() => puntos.map((punto) => punto.valor), [puntos])

  return (
    <TarjetaGrafico
      titulo="Promedio por día de la semana"
      cargando={cargando}
      error={error}
      sinDatos={!valores.some((valor) => valor > 0)}
      tabla={{
        columnas: ['Día', 'Promedio vendido'],
        filas: puntos.map((punto) => [punto.etiqueta, formatoPesos(punto.valor)]),
      }}
    >
      <GraficoBarras
        titulo="Promedio por día de la semana"
        etiquetas={etiquetas}
        valores={valores}
        formatoValor={formatoPesos}
        formatoEje={formatoPesosEje}
      />
    </TarjetaGrafico>
  )
}

function MediosDePago({ rango }: { rango: Rango }) {
  const cargar = useCallback(() => getMediosPago(rango.desde, rango.hasta), [rango])
  const { datos, error, cargando } = useDatosReporte(cargar)
  const puntos = useMemo(() => datos ?? [], [datos])
  const etiquetas = useMemo(() => puntos.map((punto) => punto.etiqueta), [puntos])
  const valores = useMemo(() => puntos.map((punto) => punto.valor), [puntos])
  const colores = useMemo(
    () => etiquetas.map((etiqueta) => COLORES_MEDIO_PAGO[etiqueta] ?? COLOR_OTRO_MEDIO_PAGO),
    [etiquetas],
  )
  const total = valores.reduce((acc, valor) => acc + valor, 0)

  return (
    <TarjetaGrafico
      titulo="Medios de pago"
      cargando={cargando}
      error={error}
      sinDatos={puntos.length === 0}
      tabla={{
        columnas: ['Medio', 'Monto', '%'],
        filas: puntos.map((punto) => [
          punto.etiqueta,
          formatoPesos(punto.valor),
          formatoPorcentaje(punto.valor / total),
        ]),
      }}
    >
      <GraficoDona
        titulo="Medios de pago"
        etiquetas={etiquetas}
        valores={valores}
        colores={colores}
        formatoValor={formatoPesos}
        formatoPorcentaje={formatoPorcentaje}
      />
    </TarjetaGrafico>
  )
}

function TarjetasResumen({ rango }: { rango: Rango }) {
  const cargar = useCallback(() => getResumenVentas(rango.desde, rango.hasta), [rango])
  const { datos, error, cargando } = useDatosReporte(cargar)

  if (error) return <p className="error">{error}</p>

  const valor = (texto: string | null) => texto ?? (cargando ? '...' : '—')

  return (
    <div className={cargando ? 'reportes-tarjetas grafico-actualizando' : 'reportes-tarjetas'} aria-busy={cargando}>
      <div className="reportes-tarjeta">
        <span>Total vendido</span>
        <strong>{valor(datos && formatoPesos(datos.totalVendido))}</strong>
        <small>Sin descontar notas de crédito</small>
      </div>
      <div className="reportes-tarjeta">
        <span>Cantidad de ventas</span>
        <strong>{valor(datos && formatoEntero(datos.cantidadVentas))}</strong>
      </div>
      <div className="reportes-tarjeta">
        <span>Ticket promedio</span>
        <strong>{valor(datos && formatoPesos(datos.ticketPromedio))}</strong>
      </div>
      <div className="reportes-tarjeta">
        <span>Unidades vendidas</span>
        <strong>{valor(datos && formatoEntero(datos.unidadesVendidas))}</strong>
      </div>
    </div>
  )
}

function BalanceFinanciero({ rango }: { rango: Rango }) {
  const cargar = useCallback(() => getBalance(rango.desde, rango.hasta), [rango])
  const { datos: balance, error, cargando } = useDatosReporte(cargar)

  return (
    <TarjetaGrafico titulo="Balance financiero" cargando={cargando} error={error} sinDatos={balance === null}>
      {balance && (
        <>
          <ul>
            <li>Ingresos por ventas: {formatoPesos(balance.ingresosPorVentas)}</li>
            <li>Costo de mercadería: {formatoPesos(balance.costoMercaderia)}</li>
            <li>Gastos operativos (luz, alquiler, etc.): {formatoPesos(balance.gastosOperativos)}</li>
            <li>Comisiones pagadas a vendedores: {formatoPesos(balance.comisionesPagadas)}</li>
            <li>
              <strong>Ganancia neta: {formatoPesos(balance.gananciaNeta)}</strong>
            </li>
          </ul>
          <p>
            <em>
              Los retiros de caja no se descuentan acá (quedan solo como arqueo en la sección Caja) — esta ganancia
              neta resta costo de mercadería, gastos operativos reales y comisiones. A diferencia del total vendido de
              arriba, los ingresos ya descuentan las notas de crédito.
            </em>
          </p>
        </>
      )}
    </TarjetaGrafico>
  )
}

export function Reportes() {
  const [periodo, setPeriodo] = useState<Periodo>('mes')
  const [rango, setRango] = useState<Rango>(() => rangoDe('mes'))
  const [borrador, setBorrador] = useState<Rango>(rango)

  function elegirPeriodo(nuevo: Periodo) {
    setPeriodo(nuevo)
    if (nuevo === 'personalizado') {
      setBorrador(rango)
    } else {
      setRango(rangoDe(nuevo))
    }
  }

  // Un rango a medio cargar o invertido no se aplica: los gráficos quedan en el último válido.
  function cambiarBorrador(cambio: Partial<Rango>) {
    const nuevo = { ...borrador, ...cambio }
    setBorrador(nuevo)
    if (nuevo.desde && nuevo.hasta && nuevo.desde <= nuevo.hasta) {
      setRango(nuevo)
    }
  }

  const rangoInvalido = periodo === 'personalizado' && !!borrador.desde && !!borrador.hasta && borrador.desde > borrador.hasta

  return (
    <div className="reportes">
      <h2>Reportes</h2>

      <div className="reportes-filtro">
        <div className="reportes-opciones" role="group" aria-label="Período">
          {PERIODOS.map(({ id, nombre }) => (
            <button key={id} type="button" aria-pressed={periodo === id} onClick={() => elegirPeriodo(id)}>
              {nombre}
            </button>
          ))}
        </div>
        {periodo === 'personalizado' && (
          <div className="reportes-rango">
            <label>
              Desde
              <input type="date" max={hoyIso()} value={borrador.desde} onChange={(e) => cambiarBorrador({ desde: e.target.value })} />
            </label>
            <label>
              Hasta
              <input type="date" max={hoyIso()} value={borrador.hasta} onChange={(e) => cambiarBorrador({ hasta: e.target.value })} />
            </label>
          </div>
        )}
      </div>
      {rangoInvalido && <p className="error">La fecha desde no puede ser posterior a la fecha hasta.</p>}

      <TarjetasResumen rango={rango} />

      <div className="reportes-grilla">
        <MarcasMasVendidas rango={rango} />
        <ProductosMasVendidos rango={rango} />
        <VentasPorDia rango={rango} />
        <VentasPorDiaSemana rango={rango} />
        <MediosDePago rango={rango} />
        <BalanceFinanciero rango={rango} />
      </div>
    </div>
  )
}
