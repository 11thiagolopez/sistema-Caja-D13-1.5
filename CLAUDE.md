Contexto del Proyecto:
Estamos migrando un sistema de gestión a Spring Boot con JWT y flujos OTP.

Instrucciones Obligatorias:

Antes de escribir código nuevo del backend, lee SIEMPRE el archivo plan-migracion.md para respetar las reglas de negocio y seguridad.

Antes de escribir código del frontend, lee SIEMPRE el archivo plan-frontend.md (stack elegido, contrato de API completo, y la regla de negocio de qué puede ver cada rol).

Revisa la sección "Estado Actual" de este mismo archivo para saber exactamente en qué paso nos encontramos.

Estado Actual (Actualizado al 2026-08-04):

Rediseño grande de interfaz (sidebar por secciones desplegables, tema gris claro fijo, modal
obligatorio de abrir caja post-login) más seis dominios de negocio nuevos: Marca (catálogo
nombre↔código, resuelto automáticamente al dar de alta productos/compras), Proveedor (catálogo con
FK en productos, backfileado desde los 5 valores de texto libre que ya existían), Gasto (gastos
operativos, reflejados en Reportes), Compra (con actualización de stock/precio y alta de producto
nuevo en el mismo renglón), Empleado CRUD (antes no existía ningún endpoint — alta/baja con % de
comisión), y Comisiones (reporte por vendedor, % sobre la ganancia). La fórmula de ganancia neta en
Reportes cambió: ya no resta retiros de caja, resta Gastos reales + comisiones pagadas. Todo
compilado (`mvn compile`/`tsc -b` limpios) y probado end-to-end en Chrome. Detalle completo,
decisiones de diseño confirmadas con el dueño y qué quedó sin probar en vivo, en `plan-migracion.md`
sección 14 y `plan-frontend.md` "Estado actual". De paso se arregló un bug real encontrado antes de
empezar (3 sesiones de caja duplicadas en Supabase causaban un 401 engañoso al abrir caja) y uno
descubierto escribiendo esta misma actualización (la baja lógica de empleado no bloqueaba el login).

Estado Anterior (Actualizado al 2026-07-30):

**Monorepo**: el repo tiene `backend/` (todo el proyecto Java/Maven/Eclipse, movido desde la raíz
con `git mv` preservando el historial) y `frontend/` (React + Vite + TypeScript, scaffoldeado y
funcionando). Los comandos de Maven/Eclipse corren desde `backend/`, los de npm desde `frontend/`
— ya no desde la raíz del repo.

El backend Spring Boot está completo, compila limpio, y quedó verificado end-to-end contra
Supabase real (login JWT por roles, ventas con descuento + OTP, retiro de caja + OTP, arqueo de
caja por turno y por rango de fechas, CORS, ABM de productos). Tests: **80/80** verdes con
`mvn test`. Detalle completo en la sección "Estado de avance" de `plan-migracion.md`. El repo está
al día con `main` en GitHub, **pero los cambios de la sesión 2026-07-30 (ver abajo) todavía no
están commiteados** — quedaron en el working tree a propósito para que el dueño los probara en
vivo primero.

**Nota de entorno**: esta máquina tiene JDK 24, que rompe silenciosamente Lombok y Mockito con las
versiones que gestiona `spring-boot-starter-parent 3.3.4` (no generaban getters/setters ni podían
mockear clases, sin ningún error). Se fijó `lombok.version`, `mockito.version` y
`byte-buddy.version` en `backend/pom.xml` a las últimas versiones con soporte JDK 24. Detalle
completo en `plan-migracion.md`, sección "Fixes de entorno".

El frontend (React + Vite + TypeScript, en `frontend/`) tiene Login, Productos (con ABM completo:
alta, baja lógica, carga de stock por código de barras — solo ADMIN), Registrar venta (con
confirmación de descuento propia, nueva), y las pantallas de ADMIN (Historial de ventas con
confirmación de OTP, Reportes) más Caja (accesible a ambos roles, resumen del día solo ADMIN), con
guards de rutas por rol reales. VENDEDOR puede abrir/cerrar caja, solicitar retiros, y desde esta
sesión también confirmar un retiro o un descuento con el código que le pase el ADMIN. Contrato de
API completo y reglas de negocio por rol en `plan-frontend.md`.

**2026-07-30 — tres bugs reportados por el dueño probando el sistema real (backend `:8080` +
frontend `:5173` contra Supabase real) y arreglados en la sesión**: (1) confirmar un retiro o un
descuento estaba restringido a ADMIN tanto en el backend como en el frontend, pero el flujo real
es que el código le llega por email solo al ADMIN y éste se lo pasa (llamada, WhatsApp) al
VENDEDOR que está en la caja para que él lo escriba y cierre la operación — se habilitó
`VENDEDOR` para confirmar en ambos endpoints y se agregó la sección correspondiente en el
frontend; (2) bug real de sesiones de caja duplicadas: `abrirSesion()` sólo chequeaba una sesión
ABIERTA de **hoy**, así que una sesión de un día anterior sin cerrar no bloqueaba abrir una nueva
— la tabla `sesiones_caja` iba acumulando sesiones ABIERTA para siempre; (3) se agregaron spinners
de carga a los botones que llamaban a la API sin ningún feedback visual. Detalle completo,
incluido qué se testeó, en `plan-migracion.md` sección 13.

Pendiente (por prioridad):
1. Probar en vivo la rama del modal de "abrir caja" sin ninguna sesión abierta (no se pudo forzar
   en la sesión 2026-08-04 sin interrumpir una sesión real que había quedado abierta).
2. Probar en el navegador, con un email real, el flujo completo de confirmación por VENDEDOR
   (retiro y descuento) — los tests automáticos mockean el envío de email.
3. Cargar `precioVenta` y `precioCompra` en Supabase para los productos que los tienen en `null`
   (hoy se muestran como "—" y no se pueden vender; también afecta el balance financiero de
   Reportes, que subestima el costo de mercadería) — el dueño ya tiene los documentos con los
   precios, pendiente de que los cargue.
4. Pulir estilos/UX del frontend: sigue siendo funcional pero básico.
5. Row Level Security deshabilitado en la mayoría de las tablas de Supabase — señalado al dueño,
   pendiente de que decida si se atiende.
6. Flyway (opcional, reemplazaría el manejo manual del esquema en Supabase).