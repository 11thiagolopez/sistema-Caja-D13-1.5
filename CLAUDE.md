Contexto del Proyecto:
Estamos migrando un sistema de gestión a Spring Boot con JWT y flujos OTP.

Instrucciones Obligatorias:

Antes de escribir código nuevo, lee SIEMPRE el archivo plan-migracion.md para respetar las reglas de negocio y seguridad.

Revisa la sección "Estado Actual" de este mismo archivo para saber exactamente en qué paso de la migración nos encontramos.

Estado Actual (Actualizado al 2026-07-27):

El backend Spring Boot está completo, compila limpio, y quedó verificado end-to-end contra
Supabase real (login JWT por roles, ventas con descuento + OTP, retiro de caja + OTP, arqueo de
caja por turno y por rango de fechas, CORS). Paso 8 (tests) ya está completo: 22 unitarios
(Mockito) + 38 de integración (`@DataJpaTest`/`@SpringBootTest` + H2 en memoria, JWT real, OTP con
`JavaMailSender` mockeado) — 60/60 verdes con `mvn test`. Detalle completo en la sección "Estado de
avance" de `plan-migracion.md` — leerla siempre antes de tocar código nuevo.

Pendiente (por prioridad):
1. Cargar `Producto.precioCompra` en Supabase (hoy la mayoría está en `null`, así que el balance
   financiero da el costo de mercadería de menos).
2. Arrancar el frontend (el backend ya tiene CORS resuelto para consumirlo desde otro origen).
3. Flyway (opcional, reemplazaría el manejo manual del esquema en Supabase).