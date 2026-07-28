Contexto del Proyecto:
Estamos migrando un sistema de gestión a Spring Boot con JWT y flujos OTP.

Instrucciones Obligatorias:

Antes de escribir código nuevo del backend, lee SIEMPRE el archivo plan-migracion.md para respetar las reglas de negocio y seguridad.

Antes de escribir código del frontend, lee SIEMPRE el archivo plan-frontend.md (stack elegido, contrato de API completo, y la regla de negocio de qué puede ver cada rol).

Revisa la sección "Estado Actual" de este mismo archivo para saber exactamente en qué paso nos encontramos.

Estado Actual (Actualizado al 2026-07-28):

**Monorepo**: el repo tiene `backend/` (todo el proyecto Java/Maven/Eclipse, movido desde la raíz
con `git mv` preservando el historial) y `frontend/` (React + Vite + TypeScript, scaffoldeado y
funcionando). Los comandos de Maven/Eclipse corren desde `backend/`, los de npm desde `frontend/`
— ya no desde la raíz del repo.

El backend Spring Boot está completo, compila limpio, y quedó verificado end-to-end contra
Supabase real (login JWT por roles, ventas con descuento + OTP, retiro de caja + OTP, arqueo de
caja por turno y por rango de fechas, CORS). Paso 8 (tests) ya está completo: 22 unitarios
(Mockito) + 40 de integración (`@DataJpaTest`/`@SpringBootTest` + H2 en memoria, JWT real, OTP con
`JavaMailSender` mockeado) — 62/62 verdes con `mvn test`. Detalle completo en la sección "Estado de
avance" de `plan-migracion.md`. La rama `migracion-web` ya se mergeó a `main` en GitHub (PR #1),
pero los commits de la reorganización a monorepo y del scaffold de frontend (`101a6ea`, `efa692e`)
están pusheados a `origin/migracion-web` y **todavía no mergeados a `main`**.

**Nota de entorno**: esta máquina tiene JDK 24, que rompe silenciosamente Lombok y Mockito con las
versiones que gestiona `spring-boot-starter-parent 3.3.4` (no generaban getters/setters ni podían
mockear clases, sin ningún error). Se fijó `lombok.version`, `mockito.version` y
`byte-buddy.version` en `backend/pom.xml` a las últimas versiones con soporte JDK 24. Detalle
completo en `plan-migracion.md`, sección "Fixes de entorno".

El frontend (React + Vite + TypeScript, en `frontend/`) ya tiene Login, Productos, Registrar
venta, y las tres pantallas de ADMIN (Historial de ventas con confirmación de OTP, Caja, Reportes),
con guards de rutas por rol reales. Contrato de API completo y reglas de negocio por rol en
`plan-frontend.md`, leerlo siempre antes de tocar código de frontend — tiene el detalle de qué
falta pulir.

Pendiente (por prioridad):
1. Probar el frontend en el navegador con un usuario real (ADMIN y VENDEDOR) — no se pudo hacer
   en la última sesión por no tener acceso a un navegador.
2. Cargar `Producto.precioCompra` en Supabase (hoy la mayoría está en `null`, así que el balance
   financiero da el costo de mercadería de menos).
3. Abrir el PR de `migracion-web` → `main` para los commits pendientes de mergear.
4. Flyway (opcional, reemplazaría el manejo manual del esquema en Supabase).