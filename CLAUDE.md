Contexto del Proyecto:
Estamos migrando un sistema de gestión a Spring Boot con JWT y flujos OTP.

Instrucciones Obligatorias:

Antes de escribir código nuevo del backend, lee SIEMPRE el archivo plan-migracion.md para respetar las reglas de negocio y seguridad.

Antes de escribir código del frontend, lee SIEMPRE el archivo plan-frontend.md (stack elegido, contrato de API completo, y la regla de negocio de qué puede ver cada rol).

Revisa la sección "Estado Actual" de este mismo archivo para saber exactamente en qué paso nos encontramos.

Estado Actual (Actualizado al 2026-07-29):

**Monorepo**: el repo tiene `backend/` (todo el proyecto Java/Maven/Eclipse, movido desde la raíz
con `git mv` preservando el historial) y `frontend/` (React + Vite + TypeScript, scaffoldeado y
funcionando). Los comandos de Maven/Eclipse corren desde `backend/`, los de npm desde `frontend/`
— ya no desde la raíz del repo.

El backend Spring Boot está completo, compila limpio, y quedó verificado end-to-end contra
Supabase real (login JWT por roles, ventas con descuento + OTP, retiro de caja + OTP, arqueo de
caja por turno y por rango de fechas, CORS, ABM de productos). Tests: 79/79 verdes con `mvn test`
(sumaron `ProductoServiceTest` y `ProductoControllerIntegrationTest` esta sesión). Detalle completo
en la sección "Estado de avance" de `plan-migracion.md`. `migracion-web` está mergeada a `main`
(local y en GitHub), incluyendo el ABM de productos y los dos fixes de esta sesión (ver abajo).

**Nota de entorno**: esta máquina tiene JDK 24, que rompe silenciosamente Lombok y Mockito con las
versiones que gestiona `spring-boot-starter-parent 3.3.4` (no generaban getters/setters ni podían
mockear clases, sin ningún error). Se fijó `lombok.version`, `mockito.version` y
`byte-buddy.version` en `backend/pom.xml` a las últimas versiones con soporte JDK 24. Detalle
completo en `plan-migracion.md`, sección "Fixes de entorno".

El frontend (React + Vite + TypeScript, en `frontend/`) tiene Login, Productos (con ABM completo:
alta, baja lógica, carga de stock por código de barras — solo ADMIN), Registrar venta, y las tres
pantallas de ADMIN (Historial de ventas con confirmación de OTP, Caja, Reportes), con guards de
rutas por rol reales. VENDEDOR ahora también puede abrir/cerrar caja y solicitar retiros (antes
era todo exclusivo de ADMIN). Contrato de API completo y reglas de negocio por rol en
`plan-frontend.md`.

**2026-07-29 — primera prueba real en Chrome (ADMIN y VENDEDOR) y dos bugs arreglados**: el
usuario reportó quedar trabado en la pantalla de Productos apenas logueaba, con ambos roles.
Diagnóstico con la extensión de automatización de Chrome (no solo lectura de código) encontró dos
bugs reales, no uno: (1) una race condition genuina entre el `useEffect` que guardaba el token JWT
y el `useEffect` de `Productos.tsx` que pedía los datos (fix: `useLayoutEffect` en
`AuthContext.tsx`), y (2) el bug que en la práctica tumbaba toda la app: productos con
`precioVenta: null` en Supabase (dato legado) hacían crashear un `.toFixed(2)` sin guard, y sin
error boundary React desmontaba toda la UI dejando una pantalla en negro. Detalle completo,
incluido cómo se verificó, en `plan-migracion.md` sección 12.

Pendiente (por prioridad):
1. Cargar `precioVenta` y `precioCompra` en Supabase para los productos que los tienen en `null`
   (hoy se muestran como "—" y no se pueden vender; también afecta el balance financiero de
   Reportes, que subestima el costo de mercadería).
2. Pulir estilos/UX del frontend: sigue siendo funcional pero básico.
3. Flyway (opcional, reemplazaría el manejo manual del esquema en Supabase).