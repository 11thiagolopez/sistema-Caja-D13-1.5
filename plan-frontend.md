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

## Paso a paso para arrancar (próxima sesión)

1. `npm create vite@latest frontend -- --template react-ts` en la raíz del repo.
2. Instalar `react-router-dom` para el ruteo y los guards por rol.
3. Armar el cliente API (`fetch` con `Authorization: Bearer` desde el contexto de auth) y los
   tipos de la sección "Contrato de API".
4. Pantalla de Login primero (consume `/api/auth/login`, guarda token+rol).
5. Guard de rutas: componente que redirige o bloquea si el `rol` en contexto no tiene permiso
   (reflejar exactamente la tabla de arriba, sobre todo el bloqueo total de VENDEDOR a historial
   de ventas y caja).
6. Productos + Registrar venta (ambos roles).
7. Recién después, las pantallas exclusivas de ADMIN.

## Pendientes / decisiones abiertas

- ¿`.env` con la URL del backend va a variar entre dev/prod? Por ahora alcanza con
  `http://localhost:8080` fijo en `.env.example`, ajustar cuando haya despliegue real.
- Sigue pendiente (ver `plan-migracion.md`): cargar `Producto.precioCompra` en Supabase (afecta
  la pantalla de Reportes → Balance, que hoy subestima el costo de mercadería).
- No se decidió todavía manejo de estado global (Context alcanza para el tamaño actual de la
  app; evaluar Zustand/Redux solo si la cantidad de estado compartido crece).
