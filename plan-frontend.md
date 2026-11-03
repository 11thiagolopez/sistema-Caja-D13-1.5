# Plan de frontend: React + Vite (TypeScript) consumiendo el backend Spring Boot

## Contexto y decisión

El backend (Spring Boot, este mismo repo, carpeta `backend/`) está completo, migrado y con
62 tests verdes (ver `plan-migracion.md`). Corre en `http://localhost:8080` y ya tiene CORS
resuelto para un frontend en otro origen.

**Decisión (2026-07-27)**: el frontend se hace con **React + Vite + TypeScript**, editado en
**VS Code** (no Eclipse, que es específico para Java/JVM y no tiene soporte real para JS/TS).
Se eligió TypeScript por sobre JavaScript plano porque esta app tiene bastante contrato de API
tipado (roles, DTOs de request/response, estados de venta) que conviene modelar con tipos en vez
de descubrir a los golpes en runtime.

Se eligió React (en vez de Vue) porque hay más ejemplos/documentación disponibles para este tipo
de CRUD con JWT + roles, que es exactamente el caso de este proyecto.

**Ubicación**: monorepo — carpeta `frontend/` en la raíz de este mismo repo, al lado de
`backend/` (que contiene `pom.xml` y `src/`, el backend Java). Reorganizado el 2026-07-28: el
backend, que antes vivía en la raíz del repo, se movió a `backend/` con `git mv` para preservar
el historial; `frontend/` se creó vacía, lista para el scaffold de abajo.

---

## Regla de negocio crítica para el frontend (no negociable)

**El rol `VENDEDOR` no debe poder ver el historial de ventas ni el arqueo/resumen de caja bajo
ninguna forma, ni siquiera el de su propio turno.** Esto ya está bloqueado en el backend
(`SecurityConfig`, ver `plan-migracion.md` sección 7 punto 5), pero el frontend tiene que
respetarlo también a nivel de UI: el rol `VENDEDOR` no debe tener en su interfaz ningún
botón/pantalla que intente pegarle a `GET /api/ventas`, `GET /api/caja/resumen-dia`,
`GET /api/caja/resumen` ni a `/api/reportes/**`. No es solo "ocultar el botón": ni siquiera
debería llamarse a esos endpoints desde el código de VENDEDOR, para no depender de que el 403 del
backend tape un error de UI.

**Esto NO aplica a operar la caja del día a día**: `VENDEDOR` sí puede abrir/cerrar sesión de
caja, solicitar un retiro y confirmar un retiro o un descuento de venta con el código que le pase
el ADMIN (ver "Contrato de API" abajo) — el código de autorización sigue llegando solo al email
del ADMIN, eso es lo que mantiene el control, no el rol que aprieta "Confirmar".

---

## Contrato de API completo

Todas las rutas (excepto `/api/auth/**`) requieren header `Authorization: Bearer <token>`.
Sin token → 401. Con token pero sin el rol requerido → 403. Formato de error en ambos casos:
`{"message": "..."}`.

### Auth (`/api/auth`) — público

| Método | Ruta | Body | Devuelve |
|---|---|---|---|
| POST | `/api/auth/login` | `{usuario, password}` | `{idEmpleado, nombre, usuario, rol, token}` |

`rol` es `"ADMIN"`, `"VENDEDOR"` o `"TECNICO"` (desde 2026-08-10 — ver "Estado actual"). En la
práctica TECNICO nunca inicia sesión: es una categoría de empleado (para asignarlo a trabajos a
domicilio y calcular su comisión), no tiene ningún endpoint habilitado en `SecurityConfig`. Guardar
`token` y `rol` (ej. en memoria + `localStorage` para persistir la sesión) para el resto de los
llamados y para decidir qué mostrar en la UI.

### Productos (`/api/productos`) — ADMIN y VENDEDOR (alta/edición/baja: solo ADMIN)

| Método | Ruta | Rol | Body | Devuelve |
|---|---|---|---|---|
| GET | `/api/productos` | ADMIN, VENDEDOR | — | `ProductoResponse[]` |
| GET | `/api/productos/{id}` | ADMIN, VENDEDOR | — | `ProductoResponse` |
| GET | `/api/productos/buscar-por-codigo?codigo=` | ADMIN, VENDEDOR | — | `ProductoResponse` (busca por `codigoBarras` o `codigoInterno`, solo `activo=true`) |
| POST | `/api/productos` | **solo ADMIN** | `ProductoRequest` (ver abajo) | `201 ProductoResponse` |
| POST | `/api/productos/cargar-stock` | **solo ADMIN** | `{codigo, cantidad}` | `ProductoResponse` (suma `cantidad` al stock del producto resuelto por `codigo`) |
| PATCH | `/api/productos/{id}` | **solo ADMIN** | `ProductoUpdateRequest` (ver abajo) | `ProductoResponse` |
| DELETE | `/api/productos/{id}` | **solo ADMIN** | — | baja lógica (`activo=false`), `204` |

**Ya no devuelve la entidad JPA directo** (esto cambió respecto a versiones anteriores de este
documento) — `ProductoResponse`: `{idProducto, rubro, familia, marca, numeroMarca, correlativo,
codigoInterno, proveedor, codigoFabrica, codigoBarras, descripcion, precioVenta, precioCompra,
precioVentaUsd, precioCompraUsd, stockActual, activo}`.

`precioVentaUsd`/`precioCompraUsd` (dolarización, 2026-08-12): ancla en USD de los precios en
pesos, recalculada sola cada vez que se guarda un precio (alta, edición en línea, compra) contra
la última cotización conocida — nunca se editan directo, son de solo lectura en el frontend
(`Productos.tsx` los muestra como dos columnas extra). Pueden ser `null` si todavía no hay ninguna
cotización cargada en el sistema.

`ProductoRequest` (alta, `POST /api/productos`):
```ts
{
  rubro: string;          // 2 dígitos
  familia: string;        // 2 dígitos
  marca: string;          // nombre libre, no un código — ver sección Marcas abajo
  proveedor: string;
  codigoFabrica?: string; // tipeado a mano o escaneado
  descripcion: string;
  precioVenta: number;
  precioCompra?: number;
  stockActual: number;
}
```

`ProductoUpdateRequest` (edición en línea, `PATCH /api/productos/{id}`, 2026-08-05) — **todos los
campos son opcionales, semántica PATCH**: solo se actualiza el que venga no-nulo.
```ts
{
  descripcion?: string;
  marca?: string;
  precioVenta?: number;
  precioCompra?: number;
  stockActual?: number;
  codigoFabrica?: string;  // "" (string vacío) limpia el código de fábrica
}
```
No incluye `rubro`/código de marca/`proveedor`: esos forman parte de `codigoInterno` o de la
resolución de catálogos, cambiarlos ahí rompería esa identidad — para eso existe el alta de un
producto nuevo. `Productos.tsx` (solo ADMIN) usa esto para la edición en línea tocando una celda
de la tabla (Enter guarda, Escape cancela, click afuera guarda).

### Ventas (`/api/ventas`)

| Método | Ruta | Rol | Body | Devuelve |
|---|---|---|---|---|
| POST | `/api/ventas` | ADMIN, VENDEDOR | `VentaRequest` (ver abajo) | `VentaResponse` |
| GET | `/api/ventas?desde=&hasta=` | **solo ADMIN** | — | `VentaResponse[]` |
| GET | `/api/ventas/{id}` | **solo ADMIN** | — | `VentaResponse` |
| GET | `/api/ventas/{id}/pdf` | **solo ADMIN** | — | PDF del comprobante (ticket o remito según `tipoVenta`), armado al vuelo, sin guardarse de antemano |
| POST | `/api/ventas/{id}/enviar-comprobante` | ADMIN, VENDEDOR | `{email}` | `VentaResponse` (manda el mismo PDF por mail, marca `comprobanteEnviadoPorEmail=true`) |

`VentaRequest`:
```ts
{
  idEmpleado: number;
  medioPago: string;        // "EFECTIVO" | "TRANSFERENCIA" | "TARJETA"
  tipoComprobante?: string;
  detalles: {
    idProducto?: number;    // línea de catálogo, descuenta stock
    descripcion?: string;   // O ESTO: ítem manual/mano de obra, no toca stock — uno de los dos es obligatorio
    tipo?: string;          // "ARTICULO" (default) | "COPIA" (legado) | "SERVICIO"
    cantidad: number;
    precioUnitario: number;
  }[];
  descuento?: number;       // si > 0, requiere motivoDescuento
  motivoDescuento?: string;
  // Cuenta corriente, nuevo 2026-10-30 (fase 3 de Notas de Crédito — ver plan-migracion.md
  // sección 27.5): idCliente obligatorio si montoSaldoAplicado > 0. Si el saldo cubre el total
  // completo, el backend ignora "medioPago" y fuerza "SALDO_CUENTA_CORRIENTE" sin confiar en lo
  // que mande el frontend.
  idCliente?: number;
  montoSaldoAplicado?: number;
}
```

`VentaResponse`: `{idVenta, fecha, idEmpleado, medioPago, tipoComprobante, totalVenta, descuento,
estado, clienteEmail, comprobanteEnviadoPorEmail, tipoVenta, clienteNombre, clienteTelefono,
direccionTrabajo, descripcionTrabajo, estadoTrabajo, idEmpleadoTecnico, nombreTecnico, idCliente,
nombreCliente, montoSaldoAplicado, detalles: [{idDetalle, idProducto, descripcionProducto, tipo,
cantidad, precioUnitario, subtotal, proveedorProducto}]}`.

- `tipoVenta` es `"MOSTRADOR"` o `"DOMICILIO"` — los campos `clienteNombre`/`clienteTelefono`/
  `direccionTrabajo`/`descripcionTrabajo`/`estadoTrabajo`/`idEmpleadoTecnico`/`nombreTecnico` solo
  tienen valor en ventas `DOMICILIO` (ver subsección "Trabajo a domicilio" abajo).
- `idCliente`/`nombreCliente` (nuevo 2026-10-30) son el cliente del catálogo `/api/clientes` —
  **distintos** de `clienteNombre`/`clienteTelefono` arriba, que son texto libre de Trabajo a
  domicilio. `montoSaldoAplicado` es cuánto de esta venta se pagó con saldo de cuenta corriente
  (`null` si no se usó saldo). `medioPago` puede valer `"SALDO_CUENTA_CORRIENTE"` cuando el saldo
  cubrió el total completo.
- `proveedorProducto` (sesión 2026-09-17) es el `Producto.proveedor` al momento de la venta —
  `null` en ítems manuales sin producto de catálogo; `HistorialVentas.tsx` lo usa para identificar
  rápido a qué proveedor corresponde un producto vendido si la venta falla o hay un reclamo.
- `estado` es `"CONFIRMADA"` o `"PENDIENTE_AUTORIZACION"` (esto último solo si `descuento > 0`: la
  venta ya descontó stock pero necesita que se confirme el código OTP antes de poder considerarse
  una venta real para el arqueo de caja. El código sólo le llega por email al ADMIN — ahí está el
  control de seguridad —, pero desde el 2026-07-30 cualquiera de los dos roles puede escribirlo en
  `POST /api/ventas/descuento/confirmar`, porque en la práctica es el VENDEDOR quien está frente al
  cliente y termina la venta apenas el ADMIN le pasa el código).

**UX a resolver**: no hay un endpoint "ventas pendientes de autorización". Para que un ADMIN vea
qué ventas están esperando confirmación, hay que pedir `GET /api/ventas?desde=hoy&hasta=hoy` y
filtrar en el cliente por `estado === "PENDIENTE_AUTORIZACION"`.

### Trabajo a domicilio (`/api/ventas/trabajo-domicilio`) — **exclusivo ADMIN**, nuevo 2026-08-10

| Método | Ruta | Body | Devuelve |
|---|---|---|---|
| POST | `/api/ventas/trabajo-domicilio` | `TrabajoDomicilioRequest` (ver abajo) | `VentaResponse` |
| PUT | `/api/ventas/trabajo-domicilio/{id}` | igual que POST (sin `idVenta`, va en la URL) | `VentaResponse` |

`TrabajoDomicilioRequest`:
```ts
{
  idVenta?: number;           // ausente = crear uno nuevo; presente (solo en el PUT) = reabrir/editar
  idEmpleado: number;
  idEmpleadoTecnico?: number; // filtrar el selector a empleados con rol === "TECNICO"
  clienteNombre: string;
  clienteTelefono?: string;
  direccionTrabajo?: string;
  descripcionTrabajo?: string;
  estadoTrabajo?: string;     // "AGENDADO" | "EN_CURSO" | "COMPLETADO" — "COBRADO" lo fija el backend si cerrar=true
  detalles?: { idProducto?: number; descripcion?: string; tipo?: string; cantidad: number; precioUnitario: number }[];
  cerrar: boolean;            // true = "Cerrar y cobrar" (exige al menos un ítem); false = "Guardar borrador"
}
```
A diferencia de `VentaRequest`, `detalles` **no es obligatorio**: se puede guardar un trabajo
recién agendado sin artículos ni mano de obra todavía. Al reabrir un borrador (`PUT`), el backend
primero devuelve el stock de las líneas viejas con producto y recién después descuenta las nuevas,
para no descuadrar el inventario. Los artículos de catálogo sí descuentan stock real; una línea
`tipo="SERVICIO"` (mano de obra, sin `idProducto`) no. `TrabajoDomicilio.tsx` (`/ventas/domicilio`)
es la pantalla; `BuscadorProductoCarrito.tsx` (compartido con Cobros) es el buscador de artículos.

### Facturación fiscal (`/api/ventas/{idVenta}/factura`) — **solo ADMIN**, nuevo 2026-08-19

| Método | Ruta | Body | Devuelve |
|---|---|---|---|
| POST | `/api/ventas/{idVenta}/factura` | `{clienteDocTipo, clienteDocNro?, clienteNombre?}` | `FacturaFiscalResponse` |
| GET | `/api/ventas/{idVenta}/factura` | — | `200 FacturaFiscalResponse` si ya se facturó, `204` si no |
| GET | `/api/ventas/{idVenta}/factura/pdf` | — | PDF de la factura (requiere que ya exista, si no `400`) |
| POST | `/api/ventas/{idVenta}/factura/enviar-email` | `{email}` | `204`, manda el mismo PDF por mail |

`clienteDocTipo`: `80` (CUIT) \| `96` (DNI) \| `99` (Consumidor Final — en ese caso `clienteDocNro`
va vacío, cualquier otro valor lo exige). `clienteNombre` (nuevo, sesión 2026-09-12): obligatorio
igual que `clienteDocNro` salvo que `clienteDocTipo` sea Consumidor Final — WSFEv1 no lo pide, pero
toda factura real de ARCA lo muestra impreso. `FacturaFiscalResponse`: `{idFactura, idVenta,
puntoVenta, tipoComprobante, numero, clienteDocTipo, clienteDocNro, clienteNombre, cae,
caeVencimiento, importe, estado, errorDetalle}` (`estado`: `"PENDIENTE"` \| `"EMITIDA"` \|
`"ERROR"` — con `ERROR`, `errorDetalle` trae el motivo que devolvió ARCA, reintentable llamando
`POST` de nuevo).

**Acción aparte del comprobante interno** (no lo reemplaza, no se dispara sola): D13 es Monotributo,
así que siempre emite Factura C real contra ARCA de producción — **no hay ambiente de
homologación**, cada llamada exitosa genera un comprobante fiscal irreversible. Vive únicamente en
Consulta de ventas (`HistorialVentas.tsx`), pantalla ya exclusiva de ADMIN.

### Caja (`/api/caja`)

| Método | Ruta | Rol | Body | Devuelve |
|---|---|---|---|---|
| POST | `/api/caja/abrir` | ADMIN, VENDEDOR | `{idEmpleado, montoInicial, cotizacionManual?}` | `SesionCajaResponse` |
| POST | `/api/caja/cerrar` | ADMIN, VENDEDOR | — | `SesionCajaResponse` |
| POST | `/api/caja/retiro/solicitar` | ADMIN, VENDEDOR | `{idEmpleado, monto, motivo, medioPago}` | `SolicitudRetiroResponse` |
| GET | `/api/caja/resumen-dia` | **solo ADMIN** | — | `ResumenDiaResponse` |
| GET | `/api/caja/resumen?desde=&hasta=` | **solo ADMIN** | — | `ResumenRangoResponse` |

`SesionCajaResponse`: `{idSesion, fecha, montoInicial, estado, cotizacionUsdVenta, productosActualizados}`
(`estado`: `"ABIERTA"`/`"CERRADA"`; los dos últimos campos son de dolarización — la cotización
usada para repricear en esta apertura y cuántos productos se ajustaron; ver sección Cotización
abajo para de dónde sale `cotizacionManual`).

`ResumenDiaResponse`: `{ventas: VentaResponse[], retiros: MovimientoCajaResponse[], montoInicial,
ventasEfectivo, ventasTransferencia, ventasTarjeta, retirosEfectivo, retirosTransferencia,
efectivoFinal, totalDigital, cajaTotalDelDia, ventasPagadasConSaldo}` (todos los montos `number`).
`ventasPagadasConSaldo` (nuevo 2026-10-30) es informativo — no suma a `cajaTotalDelDia` — cuánto
de las ventas del período se pagó con saldo de cuenta corriente en vez de dinero real. Una venta
pagada parcialmente con saldo ya tiene su parte en efectivo/transferencia/tarjeta restada de los
campos correspondientes (no hay que restarla de nuevo en el frontend).

`ResumenRangoResponse`: `{desde, hasta, total: ResumenDiaResponse, sesiones: [{idSesion, fecha,
estado, empleadoApertura, resumen: ResumenDiaResponse}]}` — el desglose por turno.

**Peticiones (desde 2026-11-03, reemplaza al código OTP por mail)**: un retiro o un descuento pedido por
VENDEDOR queda pendiente hasta que un ADMIN lo resuelve; pedido por ADMIN (según el JWT, nunca según
`idEmpleado`) vuelve ya confirmado. El VENDEDOR sigue sus peticiones en el panel flotante "Mis
peticiones" (`components/AutorizacionesPendientes.tsx`, ids en localStorage por empleado, a propósito
sin endpoint de listado). Rechazar un descuento anula la venta.

| Método | Ruta | Roles | Respuesta |
|---|---|---|---|
| GET | `/api/autorizaciones` | ADMIN | `[{tipo, id, monto, motivo, solicitante, fecha, medioPago, totalVenta}]` |
| GET | `/api/autorizaciones/estado?retiros=1,2&descuentos=3` | ADMIN, VENDEDOR | `[{tipo, id, estado}]`, solo las propias |
| POST | `/api/autorizaciones/retiros/{id}/aprobar` o `/rechazar` | ADMIN | `{tipo, id, estado}` |
| POST | `/api/autorizaciones/descuentos/{id}/aprobar` o `/rechazar` | ADMIN | `{tipo, id, estado}` |
| GET | `/api/push/clave-publica` | ADMIN | `{clavePublica}` |
| POST | `/api/push/suscripcion` | ADMIN | 204; body `{endpoint}` |

`tipo`: `RETIRO` o `DESCUENTO`. `estado`: `PENDIENTE`, `APROBADA` o `RECHAZADA`.

`GET /api/ventas/{id}/pdf` admite VENDEDOR, solo para sus propias ventas `CONFIRMADA` (si no, 403).

### Cotización (`/api/cotizacion`) — dolarización, nuevo 2026-08-14

| Método | Ruta | Rol | Body | Devuelve |
|---|---|---|---|---|
| GET | `/api/cotizacion/actual` | ADMIN, VENDEDOR | — | `200 CotizacionResponse` si ya se cargó la de HOY, `204` si no |
| POST | `/api/cotizacion/cargar` | ADMIN, VENDEDOR | — | `CotizacionResponse`, o `502` si las dos APIs de cotización fallan |
| POST | `/api/cotizacion/manual` | **solo ADMIN** | `{valorVenta}` | `CotizacionResponse` |

`CotizacionResponse`: `{valorVenta, fecha, fuente, manual}` (`fuente`: `"dolarapi.com"` |
`"dolar-bna"` | `"MANUAL"`).

**Gate diario obligatorio, para los dos roles**: nadie puede operar el sistema sin que exista una
cotización cargada para hoy. `Layout.tsx` la chequea al loguear (`GET /actual`); si no hay, bloquea
todo con `CotizacionGateModal` (sin `onClose`, igual patrón que el modal de "abrir caja") que
dispara `POST /cargar` sola. Si eso falla (las dos APIs caídas), solo ADMIN ve el input para
cargarla a mano (`POST /manual`) — VENDEDOR solo puede reintentar `POST /cargar` (que primero
revisa si ya hay una fila de hoy, así que si mientras tanto un ADMIN la cargó desde otra sesión,
se destraba sin pegarle de nuevo a ninguna API). Una vez cargada, si fue **esta** sesión la que la
disparó y el rol es ADMIN, aparece un cartel puntual y dismisible ("Cotización del día: USD $X") —
nunca a VENDEDOR, y nunca en un segundo login del mismo día (la cotización ya existía).

Este endpoint no toca stock ni precios de productos — el recálculo masivo de precios (redondeado a
múltiplos de $100) sigue pasando únicamente al abrir caja (`POST /api/caja/abrir`), que reusa la
fila de hoy que este gate ya dejó creada.

### Reportes (`/api/reportes`) — **solo ADMIN**

| Método | Ruta | Devuelve |
|---|---|---|
| GET | `/api/reportes/productos-ganadores?desde=&hasta=&limit=` | `ProductoRankingDTO[]` |
| GET | `/api/reportes/balance?desde=&hasta=` | `BalanceFinancieroResponse` |
| GET | `/api/reportes/comisiones?desde=&hasta=` | `ComisionEmpleadoDTO[]` — `{idEmpleado, nombreEmpleado, comisionPorcentaje, gananciaGenerada, comisionCalculada, cantidadVentas}` |
| GET | `/api/reportes/ventas-por-vendedor?desde=&hasta=&idEmpleado=` | `VentaResponse[]` |
| GET | `/api/reportes/ventas-por-marca?desde=&hasta=` | `MarcaRankingDTO[]` — `{marca, cantidadVendida, totalFacturado}` |
| GET | `/api/reportes/ventas-por-forma-pago?desde=&hasta=` | `FormaPagoResumenDTO[]` — `{medioPago, cantidadVentas, totalFacturado}` |

`BalanceFinancieroResponse`: `{desde, hasta, ingresosPorVentas, costoMercaderia,
gastosOperativos, comisionesPagadas, gananciaNeta}` (desde la sesión 2026-08-04 gana
`comisionesPagadas`). La fórmula: `gananciaNeta = ingresosPorVentas - costoMercaderia -
gastosOperativos - comisionesPagadas`.
`gastosOperativos` ahora es la suma de la tabla `Gasto` (ver abajo), **no** los retiros de caja —
esos quedan fuera del cálculo de ganancia (siguen existiendo como arqueo puro en `/api/caja/resumen*`).

### Marcas (`/api/marcas`) — ADMIN y VENDEDOR, solo lectura

| Método | Ruta | Devuelve |
|---|---|---|
| GET | `/api/marcas` | `MarcaResponse[]` — `{idMarca, nombre, codigo}` |

Catálogo nombre↔código interno. No hay `POST` público: se crea de forma transparente al dar de
alta un producto o una compra con un nombre de marca que todavía no existe
(`MarcaService.resolverOCrear`). El código es de 2 dígitos para las marcas migradas/creadas antes
del 2026-09-23 y de 3 dígitos (desde `"100"`) para las nuevas — ver `plan-migracion.md` sección 26,
el rango de 2 dígitos se agotó. **Nota**: `Producto.marca` en la respuesta de `/api/productos` es
el NOMBRE de la marca (no el código) desde la sesión 2026-08-05 — el código vive aparte en
`Producto.numeroMarca`, usado solo para armar `codigoInterno`. El frontend no necesita ningún mapa
código→nombre: escribe y muestra el nombre directamente.

### Proveedores (`/api/proveedores`) — ADMIN

| Método | Ruta | Body | Devuelve |
|---|---|---|---|
| GET | `/api/proveedores` | — | `ProveedorResponse[]` |
| POST | `/api/proveedores` | `{nombre, contacto?, telefono?, email?}` | `ProveedorResponse` |
| PUT | `/api/proveedores/{id}` | igual que POST | `ProveedorResponse` |
| DELETE | `/api/proveedores/{id}` | — | baja lógica, `204` |
| GET | `/api/proveedores/{id}/cuenta-corriente` | — | `CuentaCorrienteProveedorResponse` — nuevo 2026-10-30; desde 2026-11-02 incluye `comprasPendientes: [{idCompra, fecha, totalCompra, saldoPendiente}]`, de la más vieja a la más nueva y sin el crédito de devoluciones |
| POST | `/api/proveedores/{id}/pagos` | `{idEmpleado, monto, medioPago, origen}` | `CuentaCorrienteProveedorResponse` — nuevo 2026-11-02: pago total o parcial, se aplica desde la compra más vieja; `medioPago`: `EFECTIVO`\|`TRANSFERENCIA`, `origen`: `CAJA`\|`EXTERNO`; `409` si `monto` supera el saldo de la cuenta o si es de `CAJA` sin caja abierta |
| POST | `/api/proveedores/devoluciones` | `DevolucionProveedorRequest` (ver abajo) | `201 DevolucionProveedorResponse` — nuevo 2026-10-30 |
| GET | `/api/proveedores/devoluciones?desde=&hasta=` | — | `DevolucionProveedorResponse[]` — nuevo 2026-10-30 |

**Cuenta corriente de proveedor, nuevo 2026-10-30** (fase 4 de Notas de Crédito — ver
`plan-migracion.md` sección 27.6): "cuánto le debemos". `CuentaCorrienteProveedorResponse`:
`{idProveedor, nombreProveedor, saldoPendiente, movimientos: [{idMovimiento, fecha, tipo, monto,
medioPago, descripcion, idCompra, idDevolucion}]}` (`tipo`: `"DEBE_COMPRA"` (suma deuda,
al registrar una compra) \| `"HABER_PAGO"` \| `"HABER_DEVOLUCION"` (ambos restan)).
`saldoPendiente` **negativo** significa que el proveedor nos debe a nosotros (devoluciones o
pagos de más) — nunca se guarda como un número suelto, se recalcula siempre sumando el
historial de movimientos.

`DevolucionProveedorRequest`:
```ts
{
  idProveedor: number;
  idEmpleado: number;
  idCompraOriginal?: number;  // puramente informativo — no earmarked contra el saldo de esa compra
  motivo?: string;
  detalles: { idProducto: number; cantidad: number; precioUnitario: number }[];  // siempre un producto real, nunca una línea manual
}
```
Reduce stock real (igual que una venta) y acredita la cuenta corriente **general** del
proveedor, sin AFIP — es B2B interno, espejo del lado compra de la Nota de Crédito del lado
venta. `DevolucionProveedorResponse`: `{idDevolucion, idProveedor, nombreProveedor,
idCompraOriginal, fecha, motivo, montoTotal, detalles: [{idDetalle, idProducto,
descripcionProducto, cantidad, precioUnitario, subtotal}]}`.

### Facturación con padrón — ADMIN (nuevo 2026-11-02)

| Método | Ruta | Body | Devuelve |
|---|---|---|---|
| GET | `/api/clientes/padron/{cuit}` | — | `{cuit, nombre, tipoPersona, domicilio, estadoClave, condicionIva, advertencia}`; `400` si el CUIT es inválido o no existe, `502` si ARCA falla |
| POST | `/api/ventas/{id}/factura` | `{clienteDocTipo, clienteDocNro?, clienteNombre?, condicionIvaReceptor?}` | `FacturaFiscalResponse` |

`condicionIvaReceptor` es obligatorio con CUIT; con DNI o Consumidor Final se asume 5. `ClienteRequest`
y `ClienteResponse` suman `condicionIva`. La emisión vive solo en `/facturar` (ver `plan-migracion.md`
sección 27.10).

### Gastos (`/api/gastos`) — ADMIN

| Método | Ruta | Body | Devuelve |
|---|---|---|---|
| GET | `/api/gastos?desde=&hasta=` | — | `GastoResponse[]` |
| POST | `/api/gastos` | `{idEmpleado, nombre, importe, fecha, categoria?, medioPago, origen}` | `GastoResponse` |

`fecha` no puede ser futura (validado en el backend con `IllegalArgumentException` → 400).
Desde 2026-11-02, `medioPago` (`EFECTIVO`\|`TRANSFERENCIA`) y `origen` (`CAJA`\|`EXTERNO`) son
obligatorios y vuelven en `GastoResponse` (null en gastos viejos); `409` si es de `CAJA` sin caja
abierta.

### Compras (`/api/compras`) — ADMIN

| Método | Ruta | Body | Devuelve |
|---|---|---|---|
| POST | `/api/compras` | `CompraRequest` (ver abajo) | `CompraResponse` |
| GET | `/api/compras?desde=&hasta=` | — | `CompraResponse[]` |
| GET | `/api/compras/pagos-proveedor?desde=&hasta=` | — | `PagoProveedorDTO[]` |
| GET | `/api/compras/productos-mas-comprados?desde=&hasta=&limit=` | — | `ProductoComprasRankingDTO[]` |
| POST | `/api/compras/{id}/pagos` | `{idEmpleado, monto, medioPago, origen}` | `CompraResponse` — nuevo 2026-10-30 |

`CompraRequest`:
```ts
{
  idEmpleado: number;
  fecha: string;              // no puede ser futura
  proveedorNombre: string;    // resuelve o crea el proveedor por nombre
  medioPago: string;          // EFECTIVO | TRANSFERENCIA | CUENTA_CORRIENTE (deja todo como deuda, ignora montoPagado)
  origen?: string;            // CAJA | EXTERNO: obligatorio si se paga algo; solo CAJA descuenta de la caja
  items: {
    idProducto?: number;               // repone stock/precio de un producto existente
    nuevoProducto?: {                  // O ESTO, si el producto todavía no existe
      rubro: string; familia: string; marca: string; descripcion: string; codigoFabrica?: string;
    };
    cantidad: number;
    precioCompraUnitario: number;
    precioVentaUnitario?: number;      // si viene, actualiza Producto.precioVenta también
  }[];
  // Cuenta corriente de proveedor, nuevo 2026-10-30: ausente = se paga todo de una
  // (comportamiento de siempre). Un valor menor al total deja el resto como deuda pendiente,
  // pagable después con POST /api/compras/{id}/pagos.
  montoPagado?: number;
}
```
Cargar una compra **actualiza stock y precio** del producto (suma `cantidad` al stock, refresca
`precioCompra` siempre y `precioVenta` si vino) — no es solo un asiento contable.

`ResumenDiaResponse` ganó el 2026-11-02 `movimientosProveedor` (todos los pagos y compras a crédito,
cada uno con `restaDelSaldoEsperado`), `pagosProveedorEfectivoCaja` y
`pagosProveedorTransferenciaCaja`. Solo efectivo + `CAJA` baja `efectivoFinal`; transferencia +
`CAJA` baja `totalDigital` (ver `plan-migracion.md` sección 27.8). Con la misma regla sumó `gastos`
(cada uno con `restaDelSaldoEsperado`), `gastosEfectivoCaja` y `gastosTransferenciaCaja` (sección
27.9).

`CompraResponse`: `{idCompra, fecha, idProveedor, nombreProveedor, medioPago, origen, totalCompra,
montoPagado, saldoPendiente, items: [{idItem, idProducto, descripcionProducto, marcaProducto,
cantidad, precioCompraUnitario, precioVentaUnitario, subtotal}]}`. `saldoPendiente` es calculado
(`totalCompra - montoPagado`), no se puede escribir directo.

`POST /api/compras/{id}/pagos` (nuevo 2026-10-30, la pantalla ya no lo usa desde 2026-11-02: usa
`POST /api/proveedores/{id}/pagos`): pago posterior contra **esa compra puntual**
— rechaza (`409`) si `monto` supera su `saldoPendiente` actual. No confundir con
`POST /api/proveedores/devoluciones` (abajo): un pago siempre apunta a una compra específica, una
devolución acredita la cuenta corriente **general** del proveedor.

**`precioCompraUnitario`/`precioVentaUnitario` siempre viajan en pesos** — el contrato no cambió.
`ComprasNueva.tsx` (2026-08-14) agregó un `<select>` ARS/USD por renglón para cada uno de los dos
precios más una columna "% Ganancia" (recargo sobre el costo: `precioVenta = precioCompra × (1 +
%/100)`) — todo eso es conversión puramente de UI contra la cotización del día
(`GET /api/cotizacion/actual`, garantizada por el gate de arriba); antes de armar el request,
`ComprasNueva.tsx` convierte cualquier valor tipeado en USD a pesos.

### Empleados (`/api/empleados`) — ADMIN (antes no existía este controller)

| Método | Ruta | Body | Devuelve |
|---|---|---|---|
| GET | `/api/empleados` | — | `EmpleadoResponse[]` (nunca expone `passwordHash`) |
| POST | `/api/empleados` | `{nombre, usuario, password, email?, rol, comision?}` | `EmpleadoResponse` |
| PUT | `/api/empleados/{id}` | igual que POST, `password` opcional | `EmpleadoResponse` |
| DELETE | `/api/empleados/{id}` | — | baja lógica (`activo=false`), `204` |

`comision` es el % sobre la **ganancia** (no el total facturado) que se lleva ese vendedor —
columna nueva en `empleados`. Un empleado con `activo=false` no puede loguearse (`AuthService.login()`
lo rechaza con el mismo mensaje genérico que credenciales inválidas).

### Presupuestos (`/api/presupuestos`) — ADMIN y VENDEDOR, nuevo 2026-08-10

Cotización de productos de catálogo que **no** descuenta stock ni genera una `Venta` — modelo
espejo de Ventas pero sin OTP ni caja.

| Método | Ruta | Body | Devuelve |
|---|---|---|---|
| POST | `/api/presupuestos` | `PresupuestoRequest` (ver abajo) | `PresupuestoResponse` |
| GET | `/api/presupuestos?desde=&hasta=` | — | `PresupuestoResponse[]` |
| GET | `/api/presupuestos/{id}` | — | `PresupuestoResponse` |
| POST | `/api/presupuestos/{id}/enviar-email` | — | `PresupuestoResponse` (manda el PDF al `clienteEmail` del presupuesto, marca `enviadoPorEmail=true`) |
| GET | `/api/presupuestos/{id}/pdf` | — | PDF del presupuesto |

`PresupuestoRequest`:
```ts
{
  idEmpleado: number;
  clienteNombre: string;
  clienteEmail?: string;
  clienteTelefono?: string;
  detalles: {
    idProducto?: number;   // línea de catálogo
    descripcion?: string;  // O ESTO: ítem manual (ej. "Apertura de cerradura") — uno de los dos es obligatorio
    cantidad: number;
    precioUnitario: number;
  }[];
}
```

`PresupuestoResponse`: `{idPresupuesto, fecha, idEmpleado, nombreEmpleado, clienteNombre,
clienteEmail, clienteTelefono, totalPresupuesto, enviadoPorEmail, detalles:
[{idProducto, descripcionProducto, cantidad, precioUnitario, subtotal}]}`.

### Clientes (`/api/clientes`) — ADMIN, nuevo 2026-10-30

Catálogo nuevo, identificado por CUIT/DNI — antes las ventas solo guardaban nombre/email/
teléfono como texto libre, sin forma de acumular saldo de forma confiable para la misma persona.

| Método | Ruta | Body | Devuelve |
|---|---|---|---|
| GET | `/api/clientes` | — | `ClienteResponse[]` |
| GET | `/api/clientes/buscar?q=` | — | `ClienteResponse[]` (nombre o documento, contains case-insensitive) |
| GET | `/api/clientes/{id}` | — | `ClienteResponse` |
| POST | `/api/clientes` | `ClienteRequest` | `201 ClienteResponse` |
| PUT | `/api/clientes/{id}` | `ClienteRequest` | `ClienteResponse` |
| DELETE | `/api/clientes/{id}` | — | baja lógica, `204` |
| GET | `/api/clientes/{id}/cuenta-corriente` | — | `CuentaCorrienteClienteResponse` |

`ClienteRequest`: `{nombre, docTipo, docNro?, telefono?, email?, direccion?}` (`docTipo`: `80`
CUIT \| `96` DNI \| `99` sin documento, misma convención que `ClienteDocTipo` de Factura).
`ClienteResponse` agrega `idCliente, activo`.

`CuentaCorrienteClienteResponse`: `{idCliente, nombreCliente, saldoActual, movimientos:
[{idMovimiento, fecha, tipo, monto, descripcion, idNotaCredito, idVentaAplicacion}]}`
(`tipo`: `"CREDITO"` \| `"DEBITO"`). **El saldo nunca se guarda como un número suelto** — se
recalcula siempre sumando el historial de movimientos (créditos menos débitos), para que la
cuenta corriente quede auditable. Hoy el único origen de un `CREDITO` es una Nota de Crédito;
todavía no hay ningún `DEBITO` real (eso es aplicar el saldo como medio de pago en Cobros,
pendiente — ver sección de Notas de Crédito abajo).

`Venta` ganó un campo `idCliente`/`cliente` nullable a nivel backend, pero **Cobros no lo pide
todavía** — ninguna venta de hoy tiene cliente vinculado en la práctica, así que el flujo de
Nota de Crédito siempre termina pidiendo elegir o crear un cliente a mano.

### Notas de Crédito (`/api/notas-credito`) — ADMIN, nuevo 2026-10-30

Documento nuevo vinculado a una `Venta` ya `CONFIRMADA` — **nunca la modifica**, es un apéndice
(mismo espíritu que `FacturaFiscal` respecto de `Venta`). No hay endpoint nuevo para "buscar la
venta original": se reusan `GET /api/ventas/{id}` (por número) y `GET /api/ventas?desde=&hasta=`
(por rango, filtrando por nombre de cliente en el frontend).

| Método | Ruta | Body | Devuelve |
|---|---|---|---|
| POST | `/api/notas-credito` | `NotaCreditoRequest` | `201 NotaCreditoResponse` |
| GET | `/api/notas-credito?desde=&hasta=` | — | `NotaCreditoResponse[]` |
| GET | `/api/notas-credito/{id}` | — | `NotaCreditoResponse` |
| GET | `/api/notas-credito/{id}/pdf` | — | PDF interno (siempre disponible, sin depender de ARCA) |
| POST | `/api/notas-credito/{id}/emitir-fiscal` | — | `NotaCreditoResponse` (solo si `esFiscal`) |
| GET | `/api/notas-credito/{id}/factura-pdf` | — | PDF fiscal con CAE+QR (solo si `estado="EMITIDA"` con CAE) |

`NotaCreditoRequest`:
```ts
{
  idVentaOriginal: number
  idEmpleado: number
  idCliente?: number       // obligatorio en la práctica hoy, ver nota de Clientes arriba
  motivo: 'DEVOLUCION_MERCADERIA' | 'ERROR_TIPEO' | 'DESCUENTO_POSTERIOR' | 'ANULACION_TOTAL'
  alcance: 'TOTAL' | 'PARCIAL'
  // Solo si alcance=PARCIAL — con TOTAL el backend expande solo todas las líneas por su
  // cantidad remanente (no reenviar detalles).
  detalles?: { idDetalleVentaOriginal: number; cantidad: number }[]
}
```
`DEVOLUCION_MERCADERIA`/`ANULACION_TOTAL` devuelven stock de las líneas con producto;
`ERROR_TIPEO`/`DESCUENTO_POSTERIOR` no tocan stock. El backend rechaza (409) si se pide acreditar
más que el remanente de una línea (protege contra doble acreditación entre notas parciales
sucesivas sobre la misma línea).

`NotaCreditoResponse`: `{idNotaCredito, idVentaOriginal, idCliente, nombreCliente, fecha, motivo,
alcance, montoTotal, esFiscal, estado, puntoVenta, tipoComprobante, numero, cae,
caeVencimiento, errorDetalle, detalles: [{idDetalle, idProducto, descripcionProducto, cantidad,
precioUnitario, subtotal}]}`.

- `esFiscal` es `true` si la venta original tenía `FacturaFiscal` con `estado="EMITIDA"` al
  momento de crear la nota — se calcula solo, no se elige.
- `estado`: `"EMITIDA"` (interna lista, o ya con CAE) \| `"PENDIENTE_FISCAL"` (`esFiscal=true`,
  todavía no se llamó a `/emitir-fiscal`) \| `"ERROR"` (ARCA rechazó o falló la conexión —
  reintentable llamando `/emitir-fiscal` de nuevo). Una nota interna (`esFiscal=false`) siempre
  nace y queda en `"EMITIDA"` — no hay nada más que hacer con ella.
- Nota de Crédito C = tipo de comprobante AFIP **13**, siempre asociada a la Factura C (11)
  original vía `CbtesAsoc` — verificado contra el manual oficial de WSFEv1 antes de implementar
  (detalle completo en `plan-migracion.md` sección 27.3).

**Pendiente (fase 3, no implementado)**: aplicar el saldo a favor de la cuenta corriente como
medio de pago en `POST /api/ventas` — hoy la cuenta corriente solo se puede consultar, no gastar
desde el sistema.

### Caja — endpoint nuevo

| Método | Ruta | Rol | Devuelve |
|---|---|---|---|
| GET | `/api/caja/sesion-abierta` | ADMIN, VENDEDOR | `200 SesionCajaResponse` si hay una ABIERTA, `204` si no |

Usado por el modal obligatorio de "abrir caja" que se muestra al loguearse (ver `Layout.tsx`) —
las variantes `resumen-dia`/`resumen` son ADMIN-only y no sirven para esto porque VENDEDOR también
tiene que ver el modal.

---

## Pantallas sugeridas (primera versión, histórico — ver "Estado actual" para la estructura real de hoy)

**Para ambos roles:**
- Login.
- Listado de productos (con stock).
- Registrar venta (elegir productos + cantidad, medio de pago, descuento opcional + motivo).

**Solo ADMIN:**
- Historial de ventas por rango de fechas, con acción "confirmar descuento" para las
  `PENDIENTE_AUTORIZACION`.
- Caja: abrir/cerrar turno, solicitar/confirmar retiro, ver resumen del día y por rango (con
  desglose por turno).
- Reportes: productos más vendidos, balance financiero.

## Estructura de carpetas propuesta (`frontend/`) — primera versión, histórico (ver "Estado actual" para la lista real de páginas de hoy)

```
frontend/
  .env.example        # VITE_API_BASE_URL=http://localhost:8080
  src/
    api/               # cliente fetch/axios con el token JWT ya inyectado
    auth/              # contexto de sesión (token, rol, nombre) + guard de rutas por rol
    pages/
      Login.tsx
      Productos.tsx
      RegistrarVenta.tsx
      HistorialVentas.tsx   # solo ADMIN
      Caja.tsx              # ADMIN y VENDEDOR (resumen del día queda solo para ADMIN dentro de la pantalla)
      Reportes.tsx          # solo ADMIN
    types/             # los DTOs de la sección "Contrato de API" de este documento
```

## Estado actual (Actualizado al 2026-10-30, Notas de Crédito + catálogo de Clientes — las 4 fases completas)

Pedido grande y nuevo del dueño (detalle técnico completo en `plan-migracion.md` sección 27):
Notas de Crédito (buscar la venta original, motivo + alcance, impacto en stock, saldo a favor
del cliente, validación fiscal ante ARCA) + cuenta corriente de clientes y proveedores. Se
dividió en 4 fases, **las 4 completas en esta misma sesión**: 1 (Clientes + nota de crédito
interna + cuenta corriente de cliente), 2 (emisión fiscal ante ARCA, nota de crédito C tipo 13
asociada a la factura C original), 3 (saldo a favor aplicable como medio de pago en Cobros) y 4
(cuenta corriente de Proveedores — pagos parciales de Compras + devoluciones de mercadería).

**Pantallas nuevas**: `Clientes.tsx` (`/clientes`, ADMIN — ABM con CUIT/DNI),
`CuentaCorrienteCliente.tsx` (`/clientes/:id/cuenta-corriente` — saldo + historial de
movimientos), `NotaCredito.tsx` (`/notas-credito/nueva`, ADMIN — flujo de 3 pasos: buscar la
venta por número o por fecha, motivo/alcance/líneas + elegir o crear cliente, confirmar; una vez
creada, si la venta tenía factura fiscal, un botón "Emitir ante ARCA" dispara la fase 2). Nuevo
componente `BuscadorClienteSelect.tsx` (buscador + alta rápida de cliente vía `<datalist>`
nativo, mismo patrón que `BuscadorProductoCarrito.tsx`). `HistorialVentas.tsx` ganó un link
"Nota de crédito" por cada fila `CONFIRMADA`.

**Cobros (`RegistrarVenta.tsx`) ganó una sección "Cliente (opcional)"** con
`BuscadorClienteSelect`: al elegir un cliente se consulta su saldo
(`GET /api/clientes/{id}/cuenta-corriente`) y aparece un input "Pagar con saldo" (tope
`min(saldoDisponible, total)`). Si el monto cubre el total completo, se avisa que el `<select>`
de medio de pago no aplica — el backend lo va a ignorar y forzar
`medioPago="SALDO_CUENTA_CORRIENTE"`. `Caja.tsx` muestra una línea nueva informativa
("Pagado con saldo de cuenta corriente") cuando corresponde, sin sumarla a la caja total.

**`CuentaCorrienteProveedores.tsx`** (`/proveedores/cuenta-corriente`, ADMIN, nuevo — fase 4):
elegir un proveedor muestra su saldo ("le debemos" o, si es negativo, "nos debe") + historial;
"Saldar deuda" (2026-11-02, reemplaza al buscador por fechas) lista sola toda la deuda y registra
un pago total o parcial, aplicado desde la compra más vieja; `ComprasNueva.tsx` ganó la opción
"Cuenta corriente" en medio de pago; "Registrar devolución" reusa `BuscadorProductoCarrito`/`useCarrito` (mismo patrón que Cobros) para armar las líneas.
Convive con `PagosProveedores.tsx` sin reemplazarla (esa sigue siendo un reporte de compras, no
de pagos reales).

Backend: **254 tests** verdes. Frontend: `tsc -b`, `npm run lint` y `npm run build` limpios.
**No probado en un navegador real** (sin Chrome disponible en esta sesión) ni contra Supabase
real (sin las variables de entorno de la máquina de desarrollo en este entorno) — pendiente de
confirmación visual la próxima vez que se abra el sistema.

## Estado anterior (Actualizado al 2026-09-17, código de colores de stock + fix de Compras + % ganancia en alta + proveedor en Productos e Historial)

Pedido de seis partes del dueño, todo en la misma sesión (detalle técnico completo en
`plan-migracion.md` sección 25):

1. **Código de colores de stock** (`utils/stockColor.ts`): verde (≥5), amarillo (3-4), naranja
   (1-2), rojo (0). El color pinta la **fila entera** (`claseFilaStock`, clase `fila-stock-*` en
   el `<tr>`) — corregido tras el primer intento, que coloreaba solo un badge en el número de
   stock y el dueño pidió que se note de un vistazo recorriendo la tabla, no enfocando esa columna.
   Aplicado en la tabla de `Productos.tsx` y en la fila del carrito de `Presupuestos.tsx` — los
   únicos dos lugares donde el stock se muestra en una fila renderizable con HTML/CSS. No se pudo
   aplicar en Cobros/Trabajo a domicilio/Compras porque ahí el producto se busca con `<datalist>`
   nativo, que no permite estilar sus opciones.
2. **Fix real en `ComprasNueva.tsx`**: el botón "Quitar" no limpiaba los campos de la última fila
   restante (el guard `length > 1` la dejaba intacta). Ahora reemplaza la última fila por una
   vacía.
3. **Alta de producto con % de ganancia** (`Productos.tsx`): el precio de venta se autocompleta a
   partir de precio de compra + "% Ganancia" (mismo patrón que ya tenía `ComprasNueva.tsx`), sigue
   siendo editable a mano. Solo UI — `ProductoRequest` no cambió.
4. **Columna "Proveedor"** en la tabla de `Productos.tsx` — el dato (`Producto.proveedor`) ya
   viajaba en `ProductoResponse`, solo faltaba mostrarlo.
5. **`proveedorProducto` en `DetalleVentaResponse`** (ver "Contrato de API" arriba) — nuevo. Fila
   expandible por venta en `HistorialVentas.tsx` (ADMIN) con producto/cantidad/proveedor por línea
   vendida, para identificar rápido el proveedor si una venta falla o hay un reclamo.
6. **Filtros de Productos reorganizados**: la búsqueda por descripción queda siempre visible;
   marca, proveedor (nuevo) y color de stock (nuevo) quedan dentro de un `<details>` colapsable
   ("Más filtros") para no amontonar cuatro filtros simultáneos. El filtro de proveedor deriva sus
   opciones de los productos ya cargados en memoria, no de `GET /api/proveedores` (exclusivo
   ADMIN), para que funcione también para VENDEDOR.

Backend: 199 tests verdes. Frontend: `tsc -b` y `npm run lint` limpios. **No probado en un
navegador real** esta sesión (sin Chrome disponible) — pendiente confirmación visual.

## Estado anterior (Actualizado al 2026-09-17, cambio solo de backend — sin impacto en el frontend)

El comprobante no fiscal (ticket/remito) perdió su título numerado y ganó una "X" grande en un
recuadro, igual que la letra de la Factura — cambio puntual en `TicketHtmlBuilder`/
`FacturaFiscalHtmlBuilder` (backend). No toca ningún componente, contrato de API ni regla de rol
del frontend — el PDF sigue viniendo del mismo `GET /api/ventas/{id}/pdf` de siempre, "Ver"/
"Descargar" (sección anterior) no cambian. Detalle técnico completo en `plan-migracion.md`,
sección 24.

## Estado anterior (Actualizado al 2026-09-16, negrita/negro puro + eliminación de `ComprobanteInterno.tsx`: "Ver" y "Descargar" son ahora el mismo PDF)

Dos pedidos del dueño tras probar el intento anterior (ver "Estado anterior" debajo) en la
impresora real: (1) el tamaño de letra ya estaba bien, pero salía gris/punteada, pidió negrita y
negro puro; (2) sospechó que el botón "Imprimir"/"Ver" de Comprobantes seguía sacando "otra cosa"
distinta de lo ya arreglado, pidió unificar todo. Tenía razón: `ComprobanteInterno.tsx` era un
segundo generador de ticket propio (HTML/CSS + `window.print()`), completamente separado del PDF
real que arma el backend — arreglar uno no arreglaba el otro. Detalle técnico completo (por qué
divergían, qué mostraba cada uno) en `plan-migracion.md`, sección 23. Resumen del lado frontend:

- **`ComprobanteInterno.tsx`, eliminado.** "Ver" (`HistorialVentas.tsx`) y "Ver comprobante"
  (`RegistrarVenta.tsx`, antes "Generar comprobante") ahora piden el mismo `GET
  /api/ventas/{id}/pdf` que ya usaba "Descargar", y lo abren en una pestaña nueva con el visor de
  PDF del navegador (`verBlob`, nuevo en `utils/descargarBlob.ts`) en vez de renderizar su propia
  vista — imprime respetando el tamaño de página embebido en el PDF, no un `@page` de CSS sujeto a
  cómo cada driver lo interprete.
- **`App.css`**: se sacaron las clases `.comprobante*` y `@page comprobante-58mm` (muertas sin
  `ComprobanteInterno`), salvo `.comprobante-acciones` (reusada en Trabajo a domicilio para una
  fila de botones, sin relación con impresión). `EtiquetaImprimible.tsx` queda como la única
  pantalla que sigue imprimiendo HTML/CSS en vivo (etiqueta de código de barras, no es un PDF del
  backend).

`tsc -b` **y** `npm run build` limpios (corrido el build de producción real, el mismo comando que
usa Vercel, a propósito). No probado contra la impresora física.

## Estado anterior (Actualizado al 2026-09-16, fix de impresión — medida real del papel: 58×210mm fijo, no auto)

Primer intento de fix (ver "Estado anterior" debajo) asumía sin poder confirmarlo que la Xprinter
era un rollo continuo de alto variable. El dueño probó en la impresora física real y corrigió el
dato: **58mm de ancho, 48mm imprimible, 210mm de largo FIJO** — el driver de Windows tiene un
tamaño de página fijo configurado, no un rollo de alto automático. Con `size: 58mm auto` (el
intento anterior) el driver ignoraba el alto pedido y ajustaba todo a su propio tamaño configurado
— de ahí el "cuadrado chico" que seguía saliendo mal. Detalle técnico completo en
`plan-migracion.md`, sección 22. Resumen del lado frontend:

- **`App.css`**: `@page comprobante-58mm` y `@page etiqueta-58mm` pasaron de `size: 58mm auto` a
  `size: 58mm 210mm` fijo, margen de 5mm (ancho de contenido real: 48mm, no 54mm).
- **Fuentes agrandadas** en todo `.comprobante` durante impresión (14-20px, antes 11-13px) — con
  el alto ahora fijo en 210mm sobra espacio de sobra para agrandar sin miedo a que no entre.

Sigue sin cambiar ningún contrato de API ni regla de rol. `tsc -b` limpio. **Sigue sin probarse
contra la impresora física** (no hay Xprinter conectada a esta máquina de desarrollo) — este fix
está basado directamente en la medida real que dio el dueño, pero falta la confirmación visual
final de que ahora imprime legible.

## Estado anterior (Actualizado al 2026-09-16, primer intento de fix de impresión en la Xprinter térmica de 58mm — alto estimado, corregido en la sesión siguiente)

Ni el ticket (`ComprobanteInterno.tsx`, impreso con `window.print()` al cerrar una venta) ni la
factura fiscal ni el remito (PDFs generados en el backend) tenían el ancho de página declarado —
en la Xprinter 58mm del mostrador salía todo en un cuadrado ilegible o en una impresión
larguísima. Detalle técnico completo (diagnóstico, el layout de dos columnas que no entraba en
58mm, la estimación de alto para el PDF) en `plan-migracion.md`, sección 21. Resumen del lado
frontend:

- **`ComprobanteInterno.tsx`**: cada ítem pasó de una fila de tabla (4 columnas lado a lado) a dos
  líneas (`2x Producto`, y debajo precio unitario / subtotal) — formato angosto de una sola
  columna, legible en los ~54mm imprimibles del rollo.
- **`App.css`**: `.comprobante` ganó su propio `@page comprobante-58mm { size: 58mm auto; margin:
  2mm; }` (con nombre, mismo patrón que `@page etiqueta-58mm` de la sección 19.2 — un `@page` sin
  nombre afectaría cualquier otra impresión de la app), más las clases nuevas
  `.comprobante-items`/`.comprobante-item`/`.comprobante-item-fila` para el layout de dos líneas.

**Corregido en la sesión siguiente (ver "Estado actual" arriba)**: este intento asumía rollo
continuo de alto variable — la impresora real tiene tamaño de página fijo, así que `size: 58mm
auto` no funcionaba como se esperaba.

## Estado anterior (Actualizado al 2026-09-16, sincronización de un equipo nuevo — sin cambios de código de esta sesión)

Sesión de solo `git pull` + verificación de entorno en un equipo de desarrollo que estaba 2
commits atrás — no se tocó código. Detalle completo (qué se trajo, puesta a punto del entorno,
resultados de test) en `plan-migracion.md`, secciones 19 y 20. Nada de esto cambia el contrato de
API ni las reglas de rol descritas más abajo en este documento — es documentación al día, no una
sesión de desarrollo. `tsc -b` limpio sobre el código ya existente.

## Estado anterior (Actualizado al 2026-09-12)

**Nombre del cliente en factura fiscal, códigos de barras mixtos, alertas de stock bajo, fix de
escaneo en alta de producto y edición de precio de compra** — detalle técnico completo (backend,
el bug de seguridad encontrado, el bug de escaneo) en `plan-migracion.md`, sección 19. Resumen del
lado frontend:

- **Productos.tsx**: botón nuevo "Imprimir etiqueta" por fila (`components/EtiquetaImprimible.tsx`,
  nuevo, usa la librería `jsbarcode` para generar el Code128 pensado para impresora térmica 58mm).
  `precioCompra` se suma a la edición en línea (mismo mecanismo click-to-edit que ya tenían
  descripción/marca/precioVenta/stockActual/codigoFabrica). El input de código de fábrica en
  "Agregar producto" dejó de usar `BarcodeInput` (pensado para escanear-y-accionar-ya como en
  Cobros/Cargar stock, que borraba el valor con el Enter automático del lector) — ahora es un
  input controlado que retiene el valor hasta que se envía todo el formulario.
- **Alertas de stock bajo**: no tienen pantalla propia — es un email automático al ADMIN
  (`NotificacionService` en el backend) disparado al cruzar 5 u 2 unidades en cualquier venta,
  sin ninguna acción del lado del frontend.

`tsc -b` y build de frontend limpios (198 tests de backend verdes en la sesión original). Probado
en Chrome contra Supabase real en esa sesión (dato tomado del mensaje de commit; sin detalle
adicional documentado en su momento).

## Estado anterior (Actualizado al 2026-08-19)

**Facturación fiscal ARCA/AFIP (Factura C, D13 Monotributo)** — detalle técnico completo
(backend, decisiones, qué se verificó, el bug del punto de venta) en `plan-migracion.md`, sección
18. Resumen del lado frontend:

- **`HistorialVentas.tsx`**: columna nueva "Factura fiscal" (solo filas `CONFIRMADA`) — selector de
  tipo de documento del cliente (CUIT/DNI/Consumidor Final) + input de número + botón "Facturar";
  una vez emitida muestra número de comprobante y CAE. Componente `FacturaFiscalCelda` local al
  archivo. Las facturas ya emitidas se traen en batch (`Promise.all`) junto con la búsqueda de
  ventas, no por fila.
- **`api/facturas.ts`, nuevo**: `facturarVenta`, `getFactura`.
- **`types/api.ts`**: `FacturarVentaRequest`, `FacturaFiscalResponse`, `ClienteDocTipo`.

`tsc -b` limpio. Probado en Chrome contra ARCA de producción real (no hay homologación) — una
venta real, CAE obtenido y verificado.

## Estado anterior (Actualizado al 2026-08-14)

**Dolarización de precios + gate diario de cotización + Compras en ARS/USD** — detalle técnico
completo (backend, decisiones de diseño, qué se verificó) en `plan-migracion.md`, sección 17.
Resumen del lado frontend:

- **`Productos.tsx`**: dos columnas nuevas de solo lectura, "USD venta"/"USD compra"
  (`precioVentaUsd`/`precioCompraUsd`), junto a las columnas de precio en pesos — se recalculan
  solas, no son editables.
- **`components/CotizacionGateModal.tsx`, nuevo**: bloqueo obligatorio post-login (ambos roles,
  sin `onClose`) hasta que exista la cotización del dólar de hoy. Intenta cargarla sola; si falla,
  solo ADMIN ve el input manual, VENDEDOR solo puede reintentar. Wireado en `Layout.tsx` **antes**
  que el gate de "abrir caja" ya existente (`cotizacionObligatoria` se resuelve primero).
- **`Layout.tsx`**: cartel dismisible "Cotización del día: USD $X" (`.aviso-cotizacion`), solo para
  ADMIN y solo cuando la carga la disparó la sesión actual — nunca a VENDEDOR.
- **`components/AbrirCajaModal.tsx`**: si `POST /api/caja/abrir` devuelve 502 (las dos APIs de
  cotización fallaron — caso borde ahora, ya que el gate de arriba casi siempre la deja cargada de
  antes), revela un input de cotización manual y reintenta. En la práctica esta rama casi no se
  dispara: el gate diario ya la cargó antes de llegar acá.
- **`ComprasNueva.tsx`**: `<select>` ARS/USD por renglón en precio compra y precio venta, columna
  nueva "% Ganancia" entre ambos que autocompleta el precio de venta. Ver contrato de API arriba
  (sección Cotización) para el detalle de conversión — el valor que viaja al backend sigue siendo
  siempre pesos.
- **`api/cotizacion.ts`, nuevo**: `getCotizacionActual`, `cargarCotizacion`,
  `cargarCotizacionManual`.

`tsc -b` limpio. Probado por API (`curl`) contra Supabase real — no había navegador automatizado
disponible en esta sesión para probarlo clickeando en Chrome (ver plan-migracion.md 17 para el
detalle exacto de qué se verificó así). **Pendiente: confirmar visualmente en el navegador** el
modal del gate, el cartel de ADMIN, y los selectores ARS/USD de Compras — la próxima vez que se
abra el sistema en Chrome, revisar que no haya sorpresas visuales (nunca se vio renderizado).

## Estado anterior (Actualizado al 2026-08-10)

**Sesión larga con cuatro pedidos encadenados del dueño** — detalle técnico completo (backend,
decisiones de diseño, qué se verificó) en `plan-migracion.md`, sección 16. Resumen del lado
frontend:

- **Pantallas nuevas**: `Presupuestos.tsx` (`/presupuestos`, ambos roles — cotización sin stock,
  con envío por mail y descarga en PDF), `VentasPorMarca.tsx` (`/reportes/marcas`, ADMIN),
  `VentasPorFormaPago.tsx` (`/reportes/forma-pago`, ADMIN), `TrabajoDomicilio.tsx`
  (`/ventas/domicilio`, **exclusivo ADMIN** — cliente, artículos de catálogo, mano de obra de
  precio libre, técnico asignado, borrador/reapertura, resumen con comisión en vivo).
- **`components/BuscadorProductoCarrito.tsx`, nuevo**: buscador de producto + cantidad + botón
  "Agregar" extraído de `RegistrarVenta.tsx`, compartido ahora entre Cobros y Trabajo a domicilio.
- **`RegistrarVenta.tsx` (Cobros) volvió a ser solo catálogo** — en un pedido intermedio de esta
  misma sesión había ganado un selector "Artículo/Copia" y un ítem manual; un pedido posterior
  pidió sacar ambas cosas de ahí (ver plan-migracion.md 16.4). Si ves `tipo`/`descripcion` en
  `DetalleVentaRequest` y te preguntás para qué sirven ya que Cobros no los usa: los sigue usando
  Trabajo a domicilio.
- **`HistorialVentas.tsx` (Consulta de ventas)**: columna "Tipo" (Mostrador/Domicilio), filtros de
  tipo/técnico/estado del trabajo, botones "Descargar" y "Enviar por mail" por fila `CONFIRMADA`,
  link "Abrir para editar" en trabajos `EN_PROGRESO`.
- **`Vendedores.tsx`**: rol `TECNICO` nuevo en el alta; el campo "Comisión %" solo aparece si el
  rol elegido es TECNICO (VENDEDOR tiene sueldo fijo, ADMIN no cobra comisión).
- **`Layout.tsx`**: sección "Reportes" consolidada (ver plan-migracion.md 16.1), nuevo ítem suelto
  "Trabajo a domicilio" (ADMIN).

`tsc -b` limpio. Probado end-to-end en Chrome contra Supabase real (ver plan-migracion.md 16.9
para el detalle de qué se probó). **Pendiente, sin arrancar**: dolarización de precios — ver
`CLAUDE.md`, sección "Estado Actual", para las cuatro preguntas abiertas antes de tocar código.

## Estado anterior (Actualizado al 2026-08-04)

**Rediseño grande de interfaz + seis dominios de negocio nuevos**, pedidos por el dueño después de
usar el sistema real (detalle completo, decisiones de diseño y qué se verificó en
`plan-migracion.md`, sección 14):

- **Sidebar por secciones** (`components/Layout.tsx`, reescrito alrededor de un array de
  configuración) reemplaza la barra de botones plana de arriba: enlaces sueltos (Cobros,
  Productos, Historial de ventas, Reportes) + secciones desplegables Caja (abrir/cerrar caja y
  retiros como tres modales distintos, más "ver resumen del día" en `/caja/resumen`, ADMIN),
  Gastos, Compras, Vendedores (ADMIN), y un botón suelto Proveedores que abre modal directo sin
  ruta propia.
- **Modal obligatorio de "abrir caja"** al loguearse (ambos roles) si no hay una sesión ABIERTA —
  al confirmar, navega a `/ventas/nueva` (Cobros).
- **Tema**: fondo gris claro fijo (`#eceff1`/`#ffffff`), ya no depende de `prefers-color-scheme`
  (se sacó el bloque de modo oscuro de `index.css` a pedido explícito del dueño).
- **Combo de producto/marca por nombre** (`<datalist>` nativo) en `RegistrarVenta.tsx` y en la
  grilla de Compras — reemplaza el `<select>` por id de antes. `Productos.tsx`: marca y proveedor
  pasan de texto/código crudo a combos con autocompletado.
- **Pantallas nuevas** (todas ADMIN salvo aclaración): `Gastos.tsx`, `ComprasNueva.tsx` (grilla
  tipo Excel, producto existente o alta inline, fecha no futura), `ComprasConsulta.tsx`,
  `PagosProveedores.tsx`, `Vendedores.tsx` (alta con % comisión + baja lógica),
  `ComisionesVendedores.tsx`, `VentasPorVendedor.tsx`, `components/ProveedoresModal.tsx`.
- **`Reportes.tsx`**: línea nueva de comisiones pagadas, fórmula de ganancia neta cambiada (ver
  contrato de API arriba).
- Nuevo `components/Modal.tsx` genérico (generaliza el overlay que antes solo tenía
  `ComprobanteInterno`), `utils/date.ts` (`hoyIso()` extraído de la duplicación que había en
  `HistorialVentas.tsx`/`Reportes.tsx`).

`tsc -b` sin errores. Probado en Chrome con un ADMIN de prueba (creado y borrado en la misma
sesión): sidebar, alta de gasto, alta de compra con producto nuevo (verificado en Supabase que
actualizó stock/precio y generó bien el código interno), modal de Proveedores, alta de vendedor
con comisión, balance financiero con la fórmula nueva — todo funcionando. La rama "no hay sesión
abierta → aparece el modal" del flujo post-login no se pudo probar en vivo porque había una sesión
real abierta que no se quiso interrumpir; sí se confirmó la rama contraria.

## Estado anterior (Actualizado al 2026-07-30)

**Tres arreglos a partir de pruebas reales del dueño del negocio** (detalle completo en
`plan-migracion.md`, sección 13):

- **Confirmar retiro/descuento habilitado para VENDEDOR**: `Caja.tsx` ya no oculta "Confirmar
  retiro" a VENDEDOR (antes estaba condicionado a `esAdmin`); `RegistrarVenta.tsx` suma una
  sección nueva "Confirmar descuento de venta" (antes esa confirmación sólo existía en
  `HistorialVentas.tsx`, inalcanzable para VENDEDOR por ser ruta exclusiva de ADMIN). El código
  sigue llegando solo al email del ADMIN — el cambio es sólo quién puede escribirlo en el form.
- **Spinners de carga**: clase `.spinner` reutilizable en `index.css`, aplicada con un estado de
  carga por acción (no compartido) en todos los botones que llaman a la API y no tenían feedback
  visual: `Caja.tsx` (las 4 acciones), `Productos.tsx` (agregar/eliminar/cargar stock),
  `HistorialVentas.tsx` (buscar/confirmar), y sumada también a `RegistrarVenta.tsx` y `Login.tsx`
  que ya tenían el estado pero no el ícono. Los botones quedan `disabled` mientras están en curso.
- (No es de frontend, pero afecta el flujo de Caja) Bug de sesiones de caja duplicadas arreglado
  en el backend — ver sección 13 de `plan-migracion.md`.

`tsc -b` sin errores después de estos cambios. **No probado todavía en el navegador con
credenciales reales** (no había a mano en esta sesión) — sí se verificó que el backend reinició
tomando los nuevos permisos y que el frontend hizo hot-reload sin romperse.

## Estado anterior (Actualizado al 2026-07-29)

**Probado en el navegador con usuarios reales (ADMIN y VENDEDOR) — lo que faltaba del punto
anterior.** Se encontraron y arreglaron dos bugs que impedían usar la pantalla de Productos con
cualquiera de los dos roles (detalle completo en `plan-migracion.md`, sección 12):

- Race condition real entre el `useEffect` que guardaba el token JWT en `AuthContext.tsx` y el
  `useEffect` de `Productos.tsx` que pedía `/api/productos` al montar — el fetch salía sin
  `Authorization` la primera vez. Fix: `useLayoutEffect` en vez de `useEffect` para setear el
  token.
- Productos con `precioVenta: null` en Supabase (dato legado) crasheaban toda la app (sin error
  boundary, un `TypeError` en un `.toFixed(2)` sin guard desmontaba todo el árbol de React).
  Fix: tipo `Producto.precioVenta` ahora es `number | null`, `Productos.tsx` muestra "—", y
  `RegistrarVenta.tsx` bloquea vender un producto sin precio cargado.

Además se agregó ABM de productos completo en la pantalla de Productos (alta con aviso del código
interno generado, baja lógica con confirmación, carga de stock escaneando código de barras — las
tres acciones solo para ADMIN, con `BarcodeInput` nuevo para la captura del scanner), y
`ComprobanteInterno` para imprimir el comprobante de una venta. `Caja.tsx` se ajustó porque ahora
VENDEDOR también puede abrir/cerrar caja y solicitar retiro (antes esa pantalla era enteramente
ADMIN).

## Estado anterior (Actualizado al 2026-07-28)

**El frontend ya está scaffoldeado y funcionando**, no es solo un plan. Se hizo el paso a paso de
abajo completo, incluidas las pantallas de ADMIN (no se dejaron para después como decía el plan
original):

- [x] `npm create vite@latest frontend -- --template react-ts`, `react-router-dom` instalado
      (pineado a `7.18.1`, la última publicada — tiene 2 advisories abiertos de la librería, pero
      son específicos de su modo RSC/SSR que esta app no usa al ser un SPA 100% client-side; no
      hay ninguna versión limpia publicada todavía).
- [x] Cliente API (`src/api/client.ts` + un módulo por recurso: `auth.ts`, `productos.ts`,
      `ventas.ts`, `caja.ts`, `reportes.ts`) con `Authorization: Bearer` inyectado desde el token
      guardado en el contexto de auth. Tipos completos del contrato en `src/types/api.ts`,
      chequeados contra los DTOs reales del backend (no solo contra este documento).
- [x] `src/auth/AuthContext.tsx`: sesión (`idEmpleado`, `nombre`, `usuario`, `rol`, `token`)
      persistida en `localStorage`.
- [x] `src/auth/ProtectedRoute.tsx`: `RequireAuth` y `RequireRole` bloquean **a nivel de router**
      (no solo ocultan botones) — `VENDEDOR` no puede llegar a `/ventas/historial` ni a
      `/reportes` aunque escriba la URL a mano, cumpliendo la regla no negociable de arriba.
      `/caja` sí es alcanzable por los dos roles (ver nota en `App.tsx`): la pantalla branchea
      internamente qué mostrarle a cada uno.
- [x] Las 6 pantallas: `Login`, `Productos`, `RegistrarVenta` (con aviso de
      `PENDIENTE_AUTORIZACION` cuando hay descuento, y desde el 2026-07-30 su propia sección para
      confirmarlo), `Caja` (abrir/cerrar sesión, retiro en dos pasos — ambos roles desde el
      2026-07-30, resumen del día solo ADMIN), y las de ADMIN — `HistorialVentas` (con input de
      código para confirmar el OTP del descuento), `Reportes` (balance + productos ganadores).
- [x] `tsc -b` y `npm run build` sin errores, `oxlint` limpio. Verificado con `curl` contra el
      backend real corriendo (`mvn spring-boot:run`): login con credenciales inválidas devuelve
      `401 {"message": "..."}` (el shape que espera `ApiRequestError`), preflight CORS desde
      `http://localhost:5173` responde `200` con los headers esperados.
- [x] **Probado en el navegador con un usuario real** (2026-07-29, login de ADMIN y de VENDEDOR,
      Chrome real): encontró y arregló los dos bugs descritos en "Estado actual" arriba. Guard de
      roles verificado (VENDEDOR no ve acciones de ADMIN en Productos). Los flujos de OTP
      (descuento/retiro) no se volvieron a probar en esta sesión — quedan para la próxima.

## Pendientes / decisiones abiertas

- Probar en vivo el modal de "abrir caja" en la rama sin sesión abierta (ver sección 14 de
  `plan-migracion.md` — no se pudo forzar sin interrumpir una sesión real).
- **Commitear los cambios de la sesión 2026-07-30** — quedaron sin commitear a propósito, para
  que el dueño los probara primero en vivo.
- Probar en el navegador, con un email real, el flujo completo de confirmación por VENDEDOR
  (retiro y descuento) — sigue sin probarse de punta a punta con credenciales reales; los tests
  automáticos mockean el envío de email.
- Pulir estilos/UX: lo que hay es funcional pero básico (sin librería de componentes, formularios
  simples).
- Cargar `Producto.precioVenta` y `precioCompra` en Supabase para los productos que los tienen en
  `null` (hoy se muestran como "—" en Productos y bloquean la venta; `precioCompra` en `null`
  además hace que la pantalla de Reportes → Balance subestime el costo de mercadería).
- No se decidió todavía manejo de estado global (Context alcanza para el tamaño actual de la
  app; evaluar Zustand/Redux solo si la cantidad de estado compartido crece).
- ¿`.env` con la URL del backend va a variar entre dev/prod? Por ahora alcanza con
  `http://localhost:8080` fijo (`.env` y `.env.example`), ajustar cuando haya despliegue real.
