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

### Productos (`/api/productos`) — ADMIN y VENDEDOR

| Método | Ruta | Devuelve |
|---|---|---|
| GET | `/api/productos` | `Producto[]` (entidad completa: incluye `stockActual`, `precioVenta`, `precioCompra`, `precioVentaUsd`, `precioCompraUsd`) |
| GET | `/api/productos/{id}` | `Producto` |

`precioVentaUsd`/`precioCompraUsd` (dolarización, 2026-08-12): ancla en USD de los precios en
pesos, recalculada sola cada vez que se guarda un precio (alta, edición en línea, compra) contra
la última cotización conocida — nunca se editan directo, son de solo lectura en el frontend
(`Productos.tsx` los muestra como dos columnas extra). Pueden ser `null` si todavía no hay ninguna
cotización cargada en el sistema.

### Ventas (`/api/ventas`)

| Método | Ruta | Rol | Body | Devuelve |
|---|---|---|---|---|
| POST | `/api/ventas` | ADMIN, VENDEDOR | `VentaRequest` (ver abajo) | `VentaResponse` |
| POST | `/api/ventas/descuento/confirmar` | ADMIN, VENDEDOR | `{idVenta, codigo}` | `VentaResponse` |
| GET | `/api/ventas?desde=&hasta=` | **solo ADMIN** | — | `VentaResponse[]` |

`VentaRequest`:
```ts
{
  idEmpleado: number;
  medioPago: string;        // "EFECTIVO" | "TRANSFERENCIA" | "TARJETA"
  tipoComprobante?: string;
  detalles: { idProducto: number; cantidad: number; precioUnitario: number }[];
  descuento?: number;       // si > 0, requiere motivoDescuento
  motivoDescuento?: string;
}
```

`VentaResponse`: `{idVenta, fecha, idEmpleado, medioPago, tipoComprobante, totalVenta, descuento,
estado, detalles: [{idProducto, descripcionProducto, cantidad, precioUnitario, subtotal}]}`.
`estado` es `"CONFIRMADA"` o `"PENDIENTE_AUTORIZACION"` (esto último solo si `descuento > 0`: la
venta ya descontó stock pero necesita que se confirme el código OTP antes de poder considerarse
una venta real para el arqueo de caja. El código sólo le llega por email al ADMIN — ahí está el
control de seguridad —, pero desde el 2026-07-30 cualquiera de los dos roles puede escribirlo en
`POST /api/ventas/descuento/confirmar`, porque en la práctica es el VENDEDOR quien está frente al
cliente y termina la venta apenas el ADMIN le pasa el código).

**UX a resolver**: no hay un endpoint "ventas pendientes de autorización". Para que un ADMIN vea
qué ventas están esperando confirmación, hay que pedir `GET /api/ventas?desde=hoy&hasta=hoy` y
filtrar en el cliente por `estado === "PENDIENTE_AUTORIZACION"`.

### Caja (`/api/caja`)

| Método | Ruta | Rol | Body | Devuelve |
|---|---|---|---|---|
| POST | `/api/caja/abrir` | ADMIN, VENDEDOR | `{idEmpleado, montoInicial, cotizacionManual?}` | `SesionCajaResponse` |
| POST | `/api/caja/cerrar` | ADMIN, VENDEDOR | — | `SesionCajaResponse` |
| POST | `/api/caja/retiro/solicitar` | ADMIN, VENDEDOR | `{idEmpleado, monto, motivo, medioPago}` | `SolicitudRetiroResponse` |
| POST | `/api/caja/retiro/confirmar` | ADMIN, VENDEDOR | `{idSolicitud, codigo}` | `MovimientoCajaResponse` |
| GET | `/api/caja/resumen-dia` | **solo ADMIN** | — | `ResumenDiaResponse` |
| GET | `/api/caja/resumen?desde=&hasta=` | **solo ADMIN** | — | `ResumenRangoResponse` |

`SesionCajaResponse`: `{idSesion, fecha, montoInicial, estado, cotizacionUsdVenta, productosActualizados}`
(`estado`: `"ABIERTA"`/`"CERRADA"`; los dos últimos campos son de dolarización — la cotización
usada para repricear en esta apertura y cuántos productos se ajustaron; ver sección Cotización
abajo para de dónde sale `cotizacionManual`).

`ResumenDiaResponse`: `{ventas: VentaResponse[], retiros: MovimientoCajaResponse[], montoInicial,
ventasEfectivo, ventasTransferencia, ventasTarjeta, retirosEfectivo, retirosTransferencia,
efectivoFinal, totalDigital, cajaTotalDelDia}` (todos los montos `number`).

`ResumenRangoResponse`: `{desde, hasta, total: ResumenDiaResponse, sesiones: [{idSesion, fecha,
estado, empleadoApertura, resumen: ResumenDiaResponse}]}` — el desglose por turno.

**Flujo de retiro (dos pasos)**: `solicitar` genera un código de 6 dígitos que se manda por email
a los ADMIN con email cargado; `confirmar` valida ese código (vence a los 10 minutos) y recién ahí
crea el movimiento de caja real. Desde el 2026-07-30 cualquiera de los dos roles puede llamar a
`confirmar` — el código sigue llegando sólo al ADMIN por email, pero es habitual que sea el
VENDEDOR quien está en la caja y termina la operación apenas el ADMIN se lo pasa (llamada,
WhatsApp, etc.). El frontend necesita un form de dos pasos (monto/motivo/medio → después el
código).

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

`BalanceFinancieroResponse` (desde la sesión 2026-08-04) gana `comisionesPagadas`. La fórmula
cambió: `gananciaNeta = ingresosPorVentas - costoMercaderia - gastosOperativos - comisionesPagadas`.
`gastosOperativos` ahora es la suma de la tabla `Gasto` (ver abajo), **no** los retiros de caja —
esos quedan fuera del cálculo de ganancia (siguen existiendo como arqueo puro en `/api/caja/resumen*`).

### Marcas (`/api/marcas`) — ADMIN y VENDEDOR, solo lectura

| Método | Ruta | Devuelve |
|---|---|---|
| GET | `/api/marcas` | `MarcaResponse[]` — `{idMarca, nombre, codigo}` |

Catálogo nombre↔código de 2 dígitos. No hay `POST` público: se crea de forma transparente al dar de
alta un producto o una compra con un nombre de marca que todavía no existe (`MarcaService.resolverOCrear`).
El frontend arma un mapa `codigo → nombre` con esta lista para mostrar/buscar por nombre en
`Producto.marca` (que sigue siendo el código de 2 dígitos en la respuesta de `/api/productos`, sin
cambios en ese contrato).

### Proveedores (`/api/proveedores`) — ADMIN

| Método | Ruta | Body | Devuelve |
|---|---|---|---|
| GET | `/api/proveedores` | — | `ProveedorResponse[]` |
| POST | `/api/proveedores` | `{nombre, contacto?, telefono?, email?}` | `ProveedorResponse` |
| PUT | `/api/proveedores/{id}` | igual que POST | `ProveedorResponse` |
| DELETE | `/api/proveedores/{id}` | — | baja lógica, `204` |

### Gastos (`/api/gastos`) — ADMIN

| Método | Ruta | Body | Devuelve |
|---|---|---|---|
| GET | `/api/gastos?desde=&hasta=` | — | `GastoResponse[]` |
| POST | `/api/gastos` | `{idEmpleado, nombre, importe, fecha, categoria?}` | `GastoResponse` |

`fecha` no puede ser futura (validado en el backend con `IllegalArgumentException` → 400).

### Compras (`/api/compras`) — ADMIN

| Método | Ruta | Body | Devuelve |
|---|---|---|---|
| POST | `/api/compras` | `CompraRequest` (ver abajo) | `CompraResponse` |
| GET | `/api/compras?desde=&hasta=` | — | `CompraResponse[]` |
| GET | `/api/compras/pagos-proveedor?desde=&hasta=` | — | `PagoProveedorDTO[]` |
| GET | `/api/compras/productos-mas-comprados?desde=&hasta=&limit=` | — | `ProductoComprasRankingDTO[]` |

`CompraRequest`:
```ts
{
  idEmpleado: number;
  fecha: string;              // no puede ser futura
  proveedorNombre: string;    // resuelve o crea el proveedor por nombre
  medioPago: string;
  items: {
    idProducto?: number;               // repone stock/precio de un producto existente
    nuevoProducto?: {                  // O ESTO, si el producto todavía no existe
      rubro: string; familia: string; marca: string; descripcion: string; codigoFabrica?: string;
    };
    cantidad: number;
    precioCompraUnitario: number;
    precioVentaUnitario?: number;      // si viene, actualiza Producto.precioVenta también
  }[];
}
```
Cargar una compra **actualiza stock y precio** del producto (suma `cantidad` al stock, refresca
`precioCompra` siempre y `precioVenta` si vino) — no es solo un asiento contable.

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

## Estructura de carpetas propuesta (`frontend/`)

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

## Estado actual (Actualizado al 2026-08-14)

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
