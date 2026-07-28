Contexto del Proyecto:
Estamos migrando un sistema de gestión a Spring Boot con JWT y flujos OTP.

Instrucciones Obligatorias:

Antes de escribir código nuevo del backend, lee SIEMPRE el archivo plan-migracion.md para respetar las reglas de negocio y seguridad.

Antes de escribir código del frontend, lee SIEMPRE el archivo plan-frontend.md (stack elegido, contrato de API completo, y la regla de negocio de qué puede ver cada rol).

Revisa la sección "Estado Actual" de este mismo archivo para saber exactamente en qué paso nos encontramos.

Estado Actual (Actualizado al 2026-07-27):

**Reorganización a monorepo (2026-07-28)**: el repo pasó a tener `backend/` (todo el proyecto
Java/Maven/Eclipse, movido desde la raíz con `git mv` preservando el historial) y `frontend/`
(vacía, lista para el scaffold). Los comandos de Maven/Eclipse ahora corren desde `backend/`, no
desde la raíz del repo.

El backend Spring Boot está completo, compila limpio, y quedó verificado end-to-end contra
Supabase real (login JWT por roles, ventas con descuento + OTP, retiro de caja + OTP, arqueo de
caja por turno y por rango de fechas, CORS). Paso 8 (tests) ya está completo: 22 unitarios
(Mockito) + 40 de integración (`@DataJpaTest`/`@SpringBootTest` + H2 en memoria, JWT real, OTP con
`JavaMailSender` mockeado) — 62/62 verdes con `mvn test`. Detalle completo en la sección "Estado de
avance" de `plan-migracion.md`. La rama `migracion-web` ya se mergeó a `main` en GitHub (PR #1).

El frontend (React + Vite + TypeScript, en `frontend/`) todavía no está creado — el plan completo
(contrato de API, reglas de negocio por rol, estructura de carpetas, paso a paso) está en
`plan-frontend.md`, leerlo siempre antes de tocar código de frontend.

Pendiente (por prioridad):
1. Arrancar el frontend siguiendo `plan-frontend.md`.
2. Cargar `Producto.precioCompra` en Supabase (hoy la mayoría está en `null`, así que el balance
   financiero da el costo de mercadería de menos).
3. Flyway (opcional, reemplazaría el manejo manual del esquema en Supabase).