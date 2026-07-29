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

**El rol `VENDEDOR` no debe poder ver el historial de ventas bajo ninguna forma, ni siquiera el
de su propio turno.** Esto ya está bloqueado en el backend (`SecurityConfig`, ver
`plan-migracion.md` sección 7 punto 5), pero el frontend tiene que respetarlo también a nivel de
UI: el rol `VENDEDOR` no debe tener en su interfaz ningún botón/pantalla que intente pegarle a
`GET /api/ventas` ni a nada bajo `/api/caja/**` (resumen del día, resumen por turno, abrir/cerrar
caja, retiros) ni a `/api/reportes/**`. No es solo "ocultar el botón": ni siquiera debería
llamarse a esos endpoints desde el código de VENDEDOR, para no depender de que el 403 del backend
tape un error de UI.

---

## Contrato de API completo

Todas las rutas (excepto `/api/auth/**`) requieren header `Authorization: Bearer <token>`.
Sin token → 401. Con token pero sin el rol requerido → 403. Formato de error en ambos casos:
`{"message": "..."}`.

### Auth (`/api/auth`) — público

| Método | Ruta | Body | Devuelve |
|---|---|---|---|
| POST | `/api/auth/login` | `{usuario, password}` | `{idEmpleado, nombre, usuario, rol, token}` |

`rol` es `"ADMIN"` o `"VENDEDOR"`. Guardar `token` y `rol` (ej. en memoria + `localStorage` para
persistir la sesión) para el resto de los llamados y para decidir qué mostrar en la UI.

### Productos (`/api/productos`) — ADMIN y VENDEDOR

| Método | Ruta | Devuelve |
|---|---|---|
| GET | `/api/productos` | `Producto[]` (entidad completa: incluye `stockActual`, `precioVenta`, `precioCompra`) |
| GET | `/api/productos/{id}` | `Producto` |

### Ventas (`/api/ventas`)

| Método | Ruta | Rol | Body | Devuelve |
|---|---|---|---|---|
| POST | `/api/ventas` | ADMIN, VENDEDOR | `VentaRequest` (ver abajo) | `VentaResponse` |
| POST | `/api/ventas/descuento/confirmar` | **solo ADMIN** | `{idVenta, codigo}` | `VentaResponse` |
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
venta ya descontó stock pero necesita que un ADMIN confirme el código OTP que le llega por email
antes de poder considerarse una venta real para el arqueo de caja).

**UX a resolver**: no hay un endpoint "ventas pendientes de autorización". Para que un ADMIN vea
qué ventas están esperando confirmación, hay que pedir `GET /api/ventas?desde=hoy&hasta=hoy` y
filtrar en el cliente por `estado === "PENDIENTE_AUTORIZACION"`.

### Caja (`/api/caja`) — **todo exclusivo ADMIN**

| Método | Ruta | Body | Devuelve |
|---|---|---|---|
| POST | `/api/caja/abrir` | `{idEmpleado, montoInicial}` | `SesionCajaResponse` |
| POST | `/api/caja/cerrar` | — | `SesionCajaResponse` |
| POST | `/api/caja/retiro/solicitar` | `{idEmpleado, monto, motivo, medioPago}` | `SolicitudRetiroResponse` |
| POST | `/api/caja/retiro/confirmar` | `{idSolicitud, codigo}` | `MovimientoCajaResponse` |
| GET | `/api/caja/resumen-dia` | — | `ResumenDiaResponse` |
| GET | `/api/caja/resumen?desde=&hasta=` | — | `ResumenRangoResponse` |

`SesionCajaResponse`: `{idSesion, fecha, montoInicial, estado}` (`estado`: `"ABIERTA"`/`"CERRADA"`).

`ResumenDiaResponse`: `{ventas: VentaResponse[], retiros: MovimientoCajaResponse[], montoInicial,
ventasEfectivo, ventasTransferencia, ventasTarjeta, retirosEfectivo, retirosTransferencia,
efectivoFinal, totalDigital, cajaTotalDelDia}` (todos los montos `number`).

`ResumenRangoResponse`: `{desde, hasta, total: ResumenDiaResponse, sesiones: [{idSesion, fecha,
estado, empleadoApertura, resumen: ResumenDiaResponse}]}` — el desglose por turno.

**Flujo de retiro (dos pasos, ambos ADMIN)**: `solicitar` genera un código de 6 dígitos que se
manda por email a los ADMIN con email cargado; `confirmar` valida ese código (vence a los 10
minutos) y recién ahí crea el movimiento de caja real. El frontend necesita un form de dos pasos
(monto/motivo/medio → después el código).

### Reportes (`/api/reportes`) — **solo ADMIN**

| Método | Ruta | Devuelve |
|---|---|---|
| GET | `/api/reportes/productos-ganadores?desde=&hasta=&limit=` | `ProductoRankingDTO[]` |
| GET | `/api/reportes/balance?desde=&hasta=` | `BalanceFinancieroResponse` |

---

## Pantallas sugeridas (primera versión)

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
      Caja.tsx              # solo ADMIN
      Reportes.tsx          # solo ADMIN
    types/             # los DTOs de la sección "Contrato de API" de este documento
```

## Estado actual (Actualizado al 2026-07-29)

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
      (no solo ocultan botones) — `VENDEDOR` no puede llegar a `/ventas/historial`, `/caja` ni
      `/reportes` aunque escriba la URL a mano, cumpliendo la regla no negociable de arriba.
- [x] Las 6 pantallas: `Login`, `Productos`, `RegistrarVenta` (con aviso de
      `PENDIENTE_AUTORIZACION` cuando hay descuento), y las de ADMIN — `HistorialVentas` (con
      input de código para confirmar el OTP del descuento), `Caja` (abrir/cerrar sesión, retiro en
      dos pasos, resumen del día), `Reportes` (balance + productos ganadores).
- [x] `tsc -b` y `npm run build` sin errores, `oxlint` limpio. Verificado con `curl` contra el
      backend real corriendo (`mvn spring-boot:run`): login con credenciales inválidas devuelve
      `401 {"message": "..."}` (el shape que espera `ApiRequestError`), preflight CORS desde
      `http://localhost:5173` responde `200` con los headers esperados.
- [x] **Probado en el navegador con un usuario real** (2026-07-29, login de ADMIN y de VENDEDOR,
      Chrome real): encontró y arregló los dos bugs descritos en "Estado actual" arriba. Guard de
      roles verificado (VENDEDOR no ve acciones de ADMIN en Productos). Los flujos de OTP
      (descuento/retiro) no se volvieron a probar en esta sesión — quedan para la próxima.

## Pendientes / decisiones abiertas

- Probar en el navegador los flujos de OTP (confirmar descuento de venta, confirmar retiro de
  caja) con ambos roles — no se llegó a esto en la sesión del 2026-07-29, que se fue en encontrar
  y arreglar los dos bugs de la pantalla de Productos.
- Pulir estilos/UX: lo que hay es funcional pero básico (sin librería de componentes, formularios
  simples).
- Cargar `Producto.precioVenta` y `precioCompra` en Supabase para los productos que los tienen en
  `null` (hoy se muestran como "—" en Productos y bloquean la venta; `precioCompra` en `null`
  además hace que la pantalla de Reportes → Balance subestime el costo de mercadería).
- No se decidió todavía manejo de estado global (Context alcanza para el tamaño actual de la
  app; evaluar Zustand/Redux solo si la cantidad de estado compartido crece).
- ¿`.env` con la URL del backend va a variar entre dev/prod? Por ahora alcanza con
  `http://localhost:8080` fijo (`.env` y `.env.example`), ajustar cuando haya despliegue real.
