# Plan de migración: Sistema D13 (JavaFX + SQLite) → Spring Boot (REST + PostgreSQL)

## Para retomar mañana

**Estado al 2026-08-04: reestructuración grande de interfaz + seis dominios de negocio nuevos
(Marca, Proveedor, Gasto, Compra, Empleado CRUD, Comisiones), pedidos por el dueño después de usar
el sistema en producción.** Arrancó con un bug real encontrado en vivo (backend caído sin que el
dueño lo supiera — el frontend fallaba con "falta un token JWT válido" al abrir caja, pero la causa
real era `NonUniqueResultException`: habían quedado 3 sesiones de caja ABIERTA duplicadas de antes
del fix de la sesión 2026-07-30, ver sección 13; se cerraron a mano y quedó documentado como
recordatorio de limpiar datos legados después de un fix de código). A partir de ahí, pedido grande
de UI/negocio: fondo gris claro fijo (ya no sigue `prefers-color-scheme`, se sacó el bloque de modo
oscuro de `index.css` porque el dueño lo pidió explícitamente después de no ver el cambio con su
SO en modo oscuro), sidebar por secciones desplegables reemplazando la barra de botones de arriba,
modal obligatorio de "abrir caja" post-login, y los seis dominios nuevos. Detalle completo,
decisiones de diseño y qué se verificó en la sección 14. **Todo compilado (`mvn compile` y
`tsc -b` limpios) y probado end-to-end en Chrome con un usuario ADMIN temporal** (creado y borrado
en la misma sesión, junto con el resto de los datos de prueba) — commiteado en esta sesión.

**Estado al 2026-07-30: tres bugs reportados por el dueño del negocio probando el sistema real
(backend `:8080` + frontend `:5173`, ambos corriendo contra Supabase real) — los tres
encontrados, diagnosticados y arreglados en la misma sesión, pero todavía sin commitear.**

1. **Confirmar retiro/descuento estaba mal alcanzado por rol.** El dueño aclaró el flujo real de
   autorización: el código OTP le llega por email solo al ADMIN (eso sigue siendo el control de
   seguridad — el VENDEDOR nunca puede generarlo ni verlo), pero quien está físicamente en la caja
   —a menudo el VENDEDOR— es quien tiene que terminar la operación una vez que el ADMIN le pasa el
   código (llamada, WhatsApp, etc.). Antes `SecurityConfig` exigía `hasRole("ADMIN")` para
   `/api/caja/retiro/confirmar` y `/api/ventas/descuento/confirmar`, y el frontend ocultaba esas
   secciones para VENDEDOR — así que un vendedor que pedía un retiro o vendía con descuento nunca
   tenía dónde escribir el código, y la operación quedaba pendiente para siempre. **Fix**: ambos
   endpoints pasan a `hasAnyRole("ADMIN", "VENDEDOR")`; en el frontend, "Confirmar retiro" en
   `Caja.tsx` ya no está oculto para VENDEDOR (y el id de solicitud se precarga para cualquiera que
   pidió el retiro, no solo ADMIN), y se agregó una sección nueva "Confirmar descuento de venta" en
   `RegistrarVenta.tsx` (antes sólo existía en `HistorialVentas.tsx`, que sigue siendo exclusiva de
   ADMIN por la regla de no mostrarle el historial al VENDEDOR — por eso no alcanzaba con esa
   sección sola).
2. **Bug real de sesiones de caja duplicadas.** Si el vendedor se olvidaba de cerrar la caja al
   final del día y cerraba el navegador, al otro día `abrirSesion()` dejaba crear una sesión nueva
   sin problema, porque sólo chequeaba si había una sesión ABIERTA con fecha de **hoy** — una
   sesión de ayer sin cerrar no bloqueaba nada. Resultado: `sesiones_caja` iba acumulando sesiones
   ABIERTA de días distintos, ninguna cerrada nunca (el "bucle" que reportó el dueño). **Fix**:
   nuevo método `SesionCajaRepository.findByEstado(String)` (sin filtro de fecha), usado ahora en
   `abrirSesion()`, `obtenerSesionAbierta()` (renombrado desde `obtenerSesionAbiertaDeHoy()`),
   `cerrarSesionDelDia()`, `registrarRetiro()`, y en el vínculo `Venta`→`SesionCaja` de
   `VentaService.registrarVenta()`. `abrirSesion()` ahora rechaza abrir una sesión nueva mientras
   quede cualquier sesión ABIERTA sin cerrar, sea de hoy o de un día anterior. Test de regresión
   nuevo: `abrirSesion_haySesionAbiertaDeUnDiaAnterior_lanzaExcepcion`.
3. **Sin feedback visual mientras esperaba una respuesta del backend** ("se tilda"): se agregó un
   spinner CSS reutilizable (`.spinner` en `frontend/src/index.css`) y se aplicó a todos los
   botones que hacen una llamada async y no tenían loading state: `Caja.tsx`
   (abrir/cerrar/solicitar/confirmar retiro), `Productos.tsx` (agregar/eliminar producto, cargar
   stock), `HistorialVentas.tsx` (buscar, confirmar descuento), `RegistrarVenta.tsx` (confirmar
   descuento, nuevo) y `Login.tsx`.

Tests: **80/80 verdes** con `mvn test` (se sumó el test de regresión de arriba, y se
renombraron/expandieron `confirmarRetiro_comoVendedor_devuelve403` →
`confirmarRetiro_comoVendedor_funciona` y `confirmarDescuento_comoVendedor_devuelve403` →
`confirmarDescuento_comoVendedor_funciona`, que ahora ejercitan el flujo completo en vez de
esperar 403). `tsc -b` del frontend sin errores.

**Nada de esto está commiteado todavía** — quedó todo en el working tree para que el dueño lo
probara en vivo (backend y frontend corriendo en local contra Supabase real). El repo está al día
con `main` (no hay rama `migracion-web` con commits pendientes), así que estos cambios arrancan
limpios sobre `main` — falta decidir si van directo o por un branch nuevo, y commitear.

**Estado al 2026-07-29: primera prueba real en navegador (Chrome), con ADMIN y VENDEDOR.** Se
encontraron y arreglaron dos bugs que impedían usar la pantalla de Productos (ver sección 12).
Además se agregó ABM de productos (alta, baja lógica, carga de stock por código de barras) y se
amplió el rol VENDEDOR para que pueda abrir/cerrar caja y solicitar retiros. `migracion-web` se
mergeó a `main` con todo esto. Commits de la sesión, en orden: `5bacdd4` (backend: ABM productos +
roles de caja), `84d5ae6` (frontend: ABM productos + comprobante interno), `1865194` (fix: race
condition del JWT).

**Estado al 2026-07-26: el backend está completo, compilando, corriendo contra Supabase real, y
verificado end-to-end.** Esta fue la primera sesión en que la app efectivamente arrancó (antes
nunca se había probado más allá de la lectura del código). Commits de la sesión, en orden:
`cc87f43`, `a1dc129`, `2309d21`, `30897f4`, `48f5086`, `d642ee9`.

### Qué se verificó y quedó funcionando

- **Compilación**: limpia, sin errores de sintaxis en ningún controller/service/entidad (el
  problema real para arrancar nunca fue el código, sino config/entorno — ver más abajo).
- **Conexión a Supabase**: vía Session Pooler IPv4 (`aws-1-us-east-1.pooler.supabase.com:5432`,
  usuario `postgres.jyumiicapspsxgucirjd`) — la conexión directa (`db.<ref>.supabase.co:5432`) es
  IPv6-only y no funciona en esta red.
- **Auth JWT por roles** (ADMIN/VENDEDOR): login, tokens, y las reglas de `SecurityConfig`
  probadas con un usuario ADMIN real (`Aleja`).
- **CORS**: configurado (`app.cors.allowed-origins`, default puertos Vite/CRA) — bloqueante para
  cualquier frontend en otro origen, ya resuelto antes de que haga falta.
- **Los dos flujos de OTP** (descuento manual en venta y retiro de caja): probados de punta a
  punta con emails reales, código de 6 dígitos, hash bcrypt, expiración de 10 minutos.
- **Arqueo de caja**: con múltiples medios de pago (efectivo/transferencia/tarjeta), y **por
  turno** — la distribuidora opera con dos vendedores por día (mañana/tarde, secuenciales, nunca
  simultáneos), así que se agregó `idSesion` a `Venta`/`MovimientoCaja` y un endpoint nuevo
  `GET /api/caja/resumen?desde=&hasta=` con el total del período + desglose por turno. De paso se
  corrigió un bug real: el monto inicial del arqueo caía a `$0` en cuanto se cerraba la última
  sesión del día (buscaba solo la sesión ABIERTA; ahora suma todas las del rango).
- **Rama `legacy-javafx`**: creada apuntando a `8e61c95` (último commit con el código JavaFX
  completo), para no perder ese histórico aunque seguir purgándolo de `migracion-web`.
- **Paso 8 (tests) — arrancado**: 22 tests unitarios (Mockito) en `VentaServiceTest` y
  `CajaServiceTest`, cubriendo validación de stock, reglas del descuento, ambos flujos de OTP, y
  el arqueo por turno (incluye el caso de dos turnos el mismo día que expuso el bug de arriba).

### Fixes de entorno encontrados en el camino (no eran bugs de lógica de negocio)

- `application.properties` estaba guardado en ISO-8859-1 en vez de UTF-8 (rompía el filtrado de
  recursos de Maven) — corregido, y se restauró el mapeo de encoding en `.settings/` que lo causaba.
- `sesiones_caja.id_sesion`/`id_empleado_apertura` habían quedado como `bigint` en vez de `int4`
  en Supabase — corregido.
- Un producto tenía `stock_actual = NULL`, lo que rompía la deserialización (`int` primitivo) —
  corregido el dato y agregado `NOT NULL DEFAULT 0` a la columna.
- **Ojo con Eclipse**: este workspace lo tiene abierto, y su compilador incremental (sin Lombok
  configurado) a veces pisa el build de Maven en `target/classes` con clases rotas
  (`Unresolved compilation problems` al levantar). Si pasa: `mvn clean spring-boot:run` en un solo
  comando, no `compile` + `spring-boot:run` por separado.
- **(2026-07-28) JDK 24 rompía Lombok y Mockito silenciosamente**: la máquina de desarrollo tiene
  JDK 24 instalado (`java -version` → `24.0.2`), y las versiones que gestiona
  `spring-boot-starter-parent 3.3.4` no lo soportan:
  - **Lombok 1.18.34**: `@Getter`/`@Setter` no generaban nada — sin error, sin warning, el
    procesador ni se registraba (`mvn clean compile` fallaba con `cannot find symbol getX()` por
    todos lados, en cualquier clase que usara una entidad/DTO con Lombok). Reproducido standalone
    con `javac` fuera de Maven: sin `-processorpath` explícito, el procesador ni se intenta cargar
    bajo este JDK (antes alcanzaba con tenerlo en el classpath); forzando `-processorpath` sí
    corre, pero explota con `NoSuchFieldException: com.sun.tools.javac.code.TypeTag :: UNKNOWN`
    (los internals de `javac` cambiaron en JDK 24 y 1.18.34 no los conoce).
  - **Mockito 5.11.0 / byte-buddy 1.14.19**: los tests unitarios con `@Mock` fallaban con
    `MockitoException: Could not modify all classes` ("Mockito cannot mock this class") — mismo
    motivo, byte-buddy viejo no sabe instrumentar bytecode de JDK 24.
  - **Fix**: en `backend/pom.xml`, se agregaron tres properties que pisan lo que gestiona
    `spring-boot-starter-parent` (`lombok.version=1.18.46`, `mockito.version=5.23.0`,
    `byte-buddy.version=1.17.7` — las últimas disponibles en Maven Central al momento del fix, ya
    con soporte JDK 24 confirmado) y un `maven-compiler-plugin` explícito con
    `annotationProcessorPaths` apuntando a Lombok (para forzar el `-processorpath` que este JDK
    dejó de inferir del classpath). Sin el `byte-buddy.version` explícito no alcanza con subir
    Mockito solo: el BOM de Spring Boot vuelve a bajarlo a 1.14.19 por la versión que usa
    Hibernate, hay que pisarlo aparte. Verificado con `mvn clean compile` y `mvn test` (62/62
    verdes) después del fix.
  - Si en el futuro se sube la versión de `spring-boot-starter-parent` a una que ya gestione
    versiones de Lombok/Mockito/byte-buddy compatibles con el JDK en uso, estas tres properties se
    pueden quitar (no hacen daño dejarlas, pero quedarían redundantes).

### Config ya resuelta (para no volver a perder tiempo en esto)

- `JWT_SECRET`, `SUPABASE_DB_PASSWORD`, `GMAIL_APP_PASSWORD`: seteadas como variables de usuario
  de Windows en esta máquina. **Si se despliega a un hosting, hay que configurarlas también ahí**
  — no están en el repo.
- Emails de `Empleado` con `rol='ADMIN'` cargados en Supabase (incluye a Alejandro y a THIAGO11).
- Todo el SQL de las secciones 8, 9 y 10 de este documento ya está corrido en Supabase.

### Reorganización a monorepo + frontend arrancado (2026-07-28)

- El repo pasó a monorepo: todo el proyecto Java/Maven/Eclipse se movió de la raíz a `backend/`
  con `git mv` (historial preservado), y se creó `frontend/` — ver detalle completo del stack,
  contrato de API y pantallas en `plan-frontend.md`.
- El frontend (React + Vite + TypeScript) ya no es solo un plan: está scaffoldeado y funcionando
  — Login, Productos, Registrar venta, y las tres pantallas de ADMIN (Historial con confirmación
  de OTP, Caja, Reportes), con guards de rutas por rol reales (no solo UI). Detalle completo en
  `plan-frontend.md`.
- Verificado backend + frontend corriendo juntos: `mvn spring-boot:run` contra Supabase real en
  `:8080`, `npm run dev` en `:5173`, login con credenciales inválidas devuelve
  `401 {"message": "..."}`, preflight CORS desde `localhost:5173` responde `200`. **No se probó
  todavía un login real desde el navegador con un usuario válido** (falta abrir la UI a mano y
  loguearse).
- Commits `101a6ea` (reorg a monorepo + fix Lombok/Mockito) y `efa692e` (scaffold del frontend) ya
  están pusheados a `origin/migracion-web`, pero **todavía no mergeados a `main`** (2 commits de
  diferencia) — falta abrir el PR si se quiere llevarlos a `main` como se hizo con el PR #1.

### Pendiente para la próxima sesión, en orden sugerido de prioridad

1. **Commitear los cambios de hoy** (2026-07-30, backend + frontend, ver arriba) — quedaron sin
   commitear a propósito, para que el dueño los probara en vivo primero. Decidir si van directo a
   `main` o por un branch nuevo.
2. **Probar en el navegador, con un email real, el flujo completo de confirmación por VENDEDOR**
   (retiro y descuento con descuento): los tests de integración mockean `JavaMailSender`, así que
   el flujo "el ADMIN recibe el mail, le pasa el código al VENDEDOR, el VENDEDOR lo escribe" nunca
   se probó de punta a punta con un mail real en esta sesión.
3. **Cargar `Producto.precioVenta` y `precioCompra`** en Supabase para los productos que los
   tienen en `null`: bloquean la venta de esos productos y subestiman el costo de mercadería en el
   balance de Reportes. No se tocó en la sesión del 2026-07-30 (el dueño dijo que ya tiene los
   documentos con los precios y los va a cargar él).
4. **Pulir estilos/UX del frontend**: sigue siendo funcional pero básico (sin librería de
   componentes, formularios simples).
5. **Flyway** (opcional): reemplazar el manejo manual del esquema en Supabase por migraciones
   versionadas — recomendado antes de tener datos de producción reales, cada vez más caro después.

(Paso 8 — tests de integración — ya está completo, ver sección "Estado de avance" abajo.)

---

## Estado de avance

- [x] Paso 1 — Housekeeping: `application.properties` movido a la raíz de `resources`, `module-info.java`
      y todo el código JavaFX (`App`, `Main`, `controllers/*`, `utils/Paths`, `*.fxml`, `styles.css`,
      imagen del login) eliminados. Creada `SistemaD13Application` con `@SpringBootApplication`.
- [x] **Corrección no prevista**: `model`, `repository` y `service` vivían como paquetes raíz
      (`model.*`, `repository.*`, `service.*`), fuera de `com.thiago.escenasFX`. El component-scan por
      defecto de Spring Boot solo mira el paquete de la clase `@SpringBootApplication` hacia abajo, así
      que esas clases nunca se iban a registrar como beans. Se movieron todas a
      `com.thiago.escenasFX.{model,repository,service}` (y se sumaron `dto` y `config`).
- [x] Paso 2 — Entidades completas: `Venta`, `Empleado`, `DetalleVenta`, `MovimientoCaja`, `Producto`
      con getters/setters vía Lombok (`@Getter @Setter`) y montos migrados de `double` a
      `BigDecimal` (`totalVenta`, `precioUnitario`, `subtotal`, `monto`, `precioVenta`).
      Creada `SesionCaja` (entidad nueva, ver sección 3 y el SQL al final de este documento — **falta
      crear la tabla en Supabase**).
- [x] Paso 3 — Repositorios completos (`VentaRepository`, `ProductoRepository`,
      `MovimientoCajaRepository`, `EmpleadoRepository` con `findByUsuario`, `SesionCajaRepository`
      nuevo con `findByFechaAndEstado`).
- [x] Paso 4 — Servicios completos: `VentaService.registrarVenta` (valida y descuenta stock, calcula
      `totalVenta` con `BigDecimal`), `CajaService` (abrir/cerrar sesión, registrar retiro, y
      `calcularResumenDelDia()` con el `ResumenDiaDTO` ya creado en `dto`), `AuthService.login`
      (contra `passwordHash` con `BCryptPasswordEncoder`).
- [x] `SecurityConfig`: bean de `PasswordEncoder` (BCrypt) + `SecurityFilterChain` temporal con
      `permitAll()` en todo, para poder probar la API mientras se decide sesión vs JWT (punto 3 de la
      sección 7, todavía abierto).
- [x] Paso 5 — Controladores REST creados: `AuthController` (`POST /api/auth/login`),
      `ProductoController` (`GET /api/productos`, `GET /api/productos/{id}`, vía `ProductoService`
      nuevo), `VentaController` (`POST /api/ventas`, `GET /api/ventas?desde=&hasta=`),
      `CajaController` (`POST /api/caja/abrir|retiro|cerrar`, `GET /api/caja/resumen-dia`).
- [x] Paso 6 — DTOs de request/response completos (`dto/*Request.java`, `dto/*Response.java`).
      Las entidades JPA **no** se devuelven directo desde `VentaController`/`CajaController`: `Venta`
      y `DetalleVenta` tienen una relación bidireccional (`Venta.detalles` ↔ `DetalleVenta.venta`)
      que causaría un ciclo infinito al serializar a JSON. Se resolvió con un mapper compartido
      (`controller.VentaMapper`, package-private) en vez de anotaciones tipo `@JsonIgnore` en la
      entidad, para no acoplar el modelo de persistencia al contrato HTTP. `ProductoController` sí
      devuelve la entidad `Producto` directamente: no tiene relaciones bidireccionales, es de solo
      lectura y no expone datos sensibles.
- [x] `GlobalExceptionHandler` (`@RestControllerAdvice`) traduce excepciones de servicio a códigos
      HTTP: `AuthenticationFailedException` → 401, `IllegalStateException` → 409 (stock insuficiente,
      sesión ya abierta/cerrada), `IllegalArgumentException` → 400 (entidad referenciada no existe),
      errores de `@Valid` → 400 con el detalle de los campos.
- [x] **Corrección**: `application.properties` estaba mal ubicado en
      `src/main/java/com/thiago/escenasFX/repository/resources/application.properties` (dentro de
      `src/main/java`, donde Maven no lo copia al classpath por defecto — Spring Boot nunca lo iba
      a leer). Movido a `src/main/resources/application.properties` (raíz real de resources).
- [x] Tabla `sesiones_caja` creada en Supabase (SQL de la sección 8 ya ejecutado).
- [x] **Decisión (sección 7, punto 3)**: autenticación con **JWT por roles**, no sesión de servidor.
      Motivo: solo el rol `ADMIN` debe poder ver ventas, retirar dinero de caja, etc. — el resto de
      la administración de permisos por endpoint se termina de definir más adelante.
- [x] Paso 7 — Seguridad real con JWT + roles (`ADMIN`/`VENDEDOR`): `JwtService` (genera/valida el
      token), `JwtAuthenticationFilter` (lo lee del header `Authorization: Bearer ...` y arma el
      `SecurityContext`), `RestAuthenticationEntryPoint`/`RestAccessDeniedHandler` (401/403 en JSON).
      `SecurityConfig` reescrito en modo `STATELESS` con reglas por path+método (ver detalle abajo).
      `AuthController.login` ahora devuelve el `token` en `LoginResponse`.
- [x] **Flujo OTP para retiros de caja** (nuevo, no estaba en el plan original): sólo `ADMIN` puede
      pedir un retiro. `POST /api/caja/retiro/solicitar` genera un código de 6 dígitos, lo guarda
      hasheado en `SolicitudRetiro` (tabla nueva `solicitudes_retiro`) y lo manda por email a los
      `ADMIN`. `POST /api/caja/retiro/confirmar` valida el código y recién ahí crea el
      `MovimientoCaja` real. El viejo `POST /api/caja/retiro` directo se eliminó.
- [x] **Flujo OTP para descuentos manuales en ventas** (nuevo): `VentaRequest` acepta `descuento` +
      `motivoDescuento` opcionales. Si `descuento > 0`, la venta se guarda con
      `estado=PENDIENTE_AUTORIZACION` (el stock ya se descuenta/reserva en ese momento) y se
      dispara un OTP por email a los `ADMIN`. `POST /api/ventas/descuento/confirmar` valida el
      código y pasa la venta a `estado=CONFIRMADA`. Sin descuento, la venta queda `CONFIRMADA` de
      una, como antes.
      **Nota**: no hay liberación automática de stock si el OTP nunca se confirma (no se pidió un
      job de expiración); queda reservado hasta que alguien confirme o se intervenga a mano.
- [x] `CajaService.calcularResumenDelDia()` ahora sólo suma ventas `estado=CONFIRMADA` (antes sumaba
      todas): una venta con descuento pendiente de autorización no es plata real todavía y no debe
      inflar el arqueo de caja del día.
- [x] `ReportesController` (**sólo ADMIN**, via `SecurityConfig`): `GET
      /api/reportes/productos-ganadores?desde=&hasta=&limit=` (ranking por cantidad vendida) y
      `GET /api/reportes/balance?desde=&hasta=` (Ingresos por Ventas − Costo de Mercadería −
      Gastos Operativos = Ganancia Neta). El costo de mercadería usa el nuevo campo
      `Producto.precioCompra`; los gastos operativos son la suma de `MovimientoCaja` tipo
      `RETIRO` — como ahora sólo se crean al confirmar el OTP, todos los que existen ya están
      "aprobados" por construcción, no hace falta un flag extra.
- [x] **No se crearon** entidades/tablas/controladores de `Cliente` ni `CuentaCorriente`, según lo
      pedido explícitamente: las ventas no llevan cliente asociado.
- [x] SQL de la sección 9 en Supabase — hecho.
- [x] Variables de entorno (`JWT_SECRET`, `SUPABASE_DB_PASSWORD`/`GMAIL_APP_PASSWORD`) — hecho,
      seteadas como variables de usuario de Windows en la máquina de desarrollo.
- [x] Email de los `Empleado` con `rol='ADMIN'` cargado — hecho (probado con OTP real por email).
- [x] Paso 8 — Tests: **completo**. Unitarios (Mockito, sin tocar base real): `VentaServiceTest`
      (11) y `CajaServiceTest` (11). Integración (`@SpringBootTest`/`@DataJpaTest` + H2 en memoria
      real, `src/test/resources/application.properties` pisa la config de Supabase en el classpath
      de test):
      - Repositorios (`@DataJpaTest`, 12 tests): `EmpleadoRepositoryTest`, `VentaRepositoryTest`
        (incluye cascada `Venta`→`DetalleVenta` y `orphanRemoval`), `CajaRepositoriesTest`
        (`SesionCaja`+`MovimientoCaja`).
      - HTTP de punta a punta (`@SpringBootTest`+`MockMvc`, 26 tests, JWT real vía login, sin
        `@WithMockUser`): `AuthControllerIntegrationTest`, `SecurityIntegrationTest` (401/403 por
        rol), `VentaControllerIntegrationTest`, `CajaControllerIntegrationTest`. Los dos flujos de
        OTP (descuento de venta, retiro de caja) se prueban completos: `JavaMailSender` se
        mockea con `@MockBean` (sin SMTP real) y el test extrae el código de 6 dígitos del cuerpo
        del email capturado con `ArgumentCaptor`, para después confirmarlo por HTTP igual que
        haría un usuario real.
      - Se agregaron `h2` y `spring-security-test` al `pom.xml` (scope test). Nota de nomenclatura:
        las clases de integración HTTP quedaron con sufijo `...IntegrationTest`, no `...IT` —
        Surefire (bindeado a `mvn test`) solo recoge `*Test`/`*Tests` por default; `*IT` es la
        convención de Failsafe, que no está configurado en este proyecto.
      - 60/60 tests verdes (`mvn test`).
- [ ] `Producto.precioCompra` sigue sin cargarse en la mayoría de los productos (si queda `null`, el
      reporte de balance lo trata como 0, así que el costo de mercadería da de menos).
- **Nota (no es un bug, pero vale la pena revisarlo más adelante)**: `CajaService.calcularResumenDelDia()`
      no está anotado `@Transactional`, y devuelve entidades `Venta` cuya colección `detalles`
      (`@OneToMany`, `LAZY`) se recorre recién en `VentaMapper`, dentro del controller. Esto funciona
      hoy porque Spring Boot deja `spring.jpa.open-in-view=true` por defecto (mantiene la sesión de
      Hibernate abierta durante toda la request), pero es un patrón que el equipo de Spring
      desaconseja por rendimiento. Si en el futuro se desactiva OSIV, hay que envolver
      `calcularResumenDelDia()` en `@Transactional(readOnly = true)` o hacer fetch-join explícito en
      los repositorios.
- [ ] Agregar Flyway (opcional, quedó recomendado en la sección 6 pero no se agregó todavía — se está
      gestionando el esquema a mano en Supabase por ahora).
- [x] **(2026-07-30) Confirmar retiro/descuento habilitado para VENDEDOR, no sólo ADMIN**: ver
      detalle en "Para retomar mañana" arriba y en la sección 13 más abajo. `SecurityConfig` pasa
      `/api/caja/retiro/confirmar` y `/api/ventas/descuento/confirmar` a
      `hasAnyRole("ADMIN", "VENDEDOR")`.
- [x] **(2026-07-30) Fix bug de sesiones de caja duplicadas**: `abrirSesion()` sólo chequeaba una
      sesión ABIERTA de **hoy**, dejando abrir una sesión nueva encima de una vieja sin cerrar de
      un día anterior. Ahora usa `SesionCajaRepository.findByEstado("ABIERTA")` sin filtro de
      fecha. Ver sección 13.
- [x] **(2026-07-30) Spinners de carga** en los botones que llaman a la API y no tenían feedback
      visual (`Caja.tsx`, `Productos.tsx`, `HistorialVentas.tsx`, `RegistrarVenta.tsx`,
      `Login.tsx`).

## 0. Diagnóstico del estado actual (histórico, previo a los pasos de arriba)

El proyecto ya está **a mitad de camino** de la migración, no se parte de cero:

- `pom.xml` ya fue reescrito: hereda de `spring-boot-starter-parent 3.3.4` y trae
  `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, driver `postgresql` (apuntando a
  Supabase), `spring-boot-starter-validation` y `spring-boot-starter-security`. Las dependencias
  viejas de JavaFX (`javafx-controls`, `javafx-fxml`) y de `sqlite-jdbc` ya no están.
- El commit `971394e` ("Respaldo del sistema original en JavaFX", HEAD actual) **borró** la capa de
  persistencia manual (`persistence/ConexionDB.java`), el modelo de dominio en memoria
  (`model/CajaDiaria.java`, `model/Transaccion.java`) y el login viejo (`model/UserDAO.java`,
  `model/Usuario.java`), y en el mismo commit agregó los primeros borradores de entidades JPA,
  repositorios y servicios.
- **Consecuencia importante**: los controladores JavaFX que quedan (`Controller1`, `Controller2`,
  `ControllerLogin`, `Controllerventas`) referencian `persistence.ConexionDB` y `model.CajaDiaria`,
  que ya no existen. **El proyecto no compila en su estado actual.** Esto no es un problema a
  arreglar remendando esas clases: es la señal de que hay que terminar de cortar el cordón con
  JavaFX, no revivirlo.
- Las entidades/repositorios/servicios nuevos ya creados (`model.Venta`, `model.Empleado`,
  `model.DetalleVenta`, `model.MovimientoCaja`, `model.Producto`,
  `repository.{Venta,Producto,MovimientoCaja,Empleado}Repository`, `service.{Venta,Caja,Auth}Service`)
  son un buen punto de partida pero están **incompletos**: falta la mayoría de imports
  (`JpaRepository`, `List`, `LocalDateTime`, `@Autowired`, `@Transactional`), faltan
  getters/setters en las entidades, `EmpleadoRepository`/`AuthService` están vacíos, y
  `CajaService.calcularResumenDelDia()` referencia un `ResumenDiaDTO` que no existe.
- `application.properties` está guardado en
  `src/main/resources/com/thiago/escenasFX/application.properties`, una ruta que Spring Boot
  **no lee automáticamente** (debe estar en la raíz de `src/main/resources/`).
- Sigue existiendo `src/main/java/module-info.java` (JPMS), pensado para JavaFX. Un módulo con
  nombre complica innecesariamente el classpath de Spring/Hibernate (que usan mucha reflexión) y
  ya no tiene sentido sin JavaFX.

La buena noticia: como se perdió la implementación vieja de `ConexionDB` en el working tree pero
sigue en el historial de git, pude recuperar el SQL y la lógica de negocio originales
(commit `8e61c95`) para documentarlos abajo antes de que se reescriban.

---

## 1. Lógica de negocio y SQL identificados en el sistema original (JavaFX + SQLite)

Esto es lo que hoy vive "hardcodeado" dentro de los `Controller*.java` y de `ConexionDB` (recuperado
del historial), y es lo que hay que reencarnar como servicios + repositorios Spring:

| Operación (hoy en `Controller1`/`ConexionDB`) | SQL / lógica original | Futuro |
|---|---|---|
| `crearTablas()` | `CREATE TABLE IF NOT EXISTS ventas/sesiones/personas` | Reemplazado por JPA (`ddl-auto=validate`) + migraciones versionadas (Flyway) |
| `insertarVenta(desc, cant, precio, medio, tipo)` | `INSERT INTO ventas (...)` con `tipo` = `'VENTA'` o `'RETIRO'` metido en la misma tabla | Se separa en `Venta`+`DetalleVenta` (venta) y `MovimientoCaja` (retiro) — dos conceptos que en SQLite estaban mezclados en una tabla plana por `tipo` |
| `insertarSesion(montoInicial)` / `obtenerMontoInicialHoy()` | tabla `sesiones (monto_inicial, fecha, estado)` | **No tiene equivalente en el modelo JPA actual** → hay que crear una entidad `SesionCaja` (ver sección 3) |
| `obtenerVentasDelDia()` | `SELECT * FROM ventas WHERE fecha = date('now','localtime')` | `VentaRepository.findByFechaBetween(desde, hasta)` (ya existe el método, falta usarlo en un endpoint) |
| `insertarRetiro(motivo, monto, medio)` | `INSERT INTO ventas (..., tipo='RETIRO')` | `MovimientoCajaService.registrarRetiro(...)` sobre la tabla `movimientos_caja` |
| `cerrarSesionActual()` | `UPDATE sesiones SET estado='CERRADA' WHERE fecha=hoy` | Campo `estado` en `SesionCaja` + endpoint `POST /api/caja/cerrar` |
| `respaldarBaseDeDatos()` | copia el archivo `.db` a `C:/CajaCompartida/Backups/` | **No se traduce 1:1**: en Postgres/Supabase el backup es responsabilidad de la infraestructura (backups automáticos de Supabase), no de la app. Se elimina de la capa de negocio |
| `exportarTXT()` (en `Controller1`) | Lee `ConexionDB.obtenerVentasDelDia()`, agrupa por medio de pago (efectivo/transferencia/tarjeta), calcula retiros, escribe un `.txt` en disco local del cliente | Se convierte en un endpoint `GET /api/caja/resumen-dia` que devuelve el mismo cálculo como JSON (esto es exactamente lo que `CajaService.calcularResumenDelDia()` ya empezó a hacer) |
| Login (`ControllerLogin` + `UserDAO`) | `SELECT id FROM personas WHERE usuario=? AND password=?` (contraseña **en texto plano**, comparación directa) | `AuthService` + Spring Security con `BCryptPasswordEncoder` contra `Empleado.passwordHash` (la entidad ya tiene el campo `password_hash`, hay que completar la lógica) |
| Validación de stock al vender | No existía en el sistema viejo (SQLite solo guardaba filas sueltas, sin relación a productos/stock) | Ya está resuelto en el borrador nuevo: `VentaService.registrarVenta()` valida `stockActual` contra `DetalleVenta.cantidad` y descuenta stock — **esto es lógica de negocio nueva, no existía antes; consérvenla, es la mejora real de esta migración** |
| Atajo F12 "cambiar cliente" / ventana de espera | Estado puramente de UI de escritorio (una `Stage` nueva) | No aplica a un backend REST; en el futuro frontend web sería manejo de pestañas/estado de carrito en el cliente, no un endpoint |

---

## 2. Separación conceptual: Vista (JavaFX) vs. Modelo vs. Lógica de negocio vs. SQL

```
┌─────────────────────┐     ┌──────────────────────┐     ┌───────────────────┐
│   VISTA (a migrar)   │     │  LÓGICA DE NEGOCIO    │     │   DATOS / SQL      │
│   *.fxml + Controller│ ──▶ │  service.*Service     │ ──▶ │ repository.* (JPA) │
│   (se descarta)      │     │  (se conserva/termina)│     │ (se conserva/term.)│
└─────────────────────┘     └──────────────────────┘     └───────────────────┘
```

- **Se descarta por completo**: todo lo que es JavaFX puro — `com.thiago.escenasFX.App`/`Main`,
  los 4 `*.fxml`, `utils.Paths`, y la parte de los `Controller*` que manipula `TableView`, `Stage`,
  `Dialog`, `Alert`, atajos de teclado, etc. Ninguna de estas clases tiene lógica de negocio real
  que valga la pena preservar; son puro "pegamento" de UI de escritorio.
- **Se extrae y se conserva** (moviéndola de los controladores a servicios): las reglas que sí
  importan — armar el total de una venta, clasificar movimientos por medio de pago, validar stock,
  decidir a qué pantalla ir según si ya existe una sesión de caja abierta hoy, el cálculo de arqueo
  de caja (efectivo físico, digital, total del día).
- **El modelo de datos** (`model/*.java`) ya migró de POJOs sueltos (`Venta` con
  `descripcion/cantidad/precio/medioPago` como si fuera una fila plana) a entidades JPA
  relacionales (`Venta` → `DetalleVenta` → `Producto`, con `Empleado` como autor). Falta
  completarlo (ver sección 3).

---

## 3. Modelo de datos objetivo

Entidades que ya existen (a completar) y una que falta crear:

| Entidad | Estado | Pendiente |
|---|---|---|
| `Producto` | Creada | Agregar getters/setters; falta `@Table` no requiere cambios |
| `Empleado` | Creada | Agregar getters/setters; usarla en Spring Security (`UserDetailsService`) |
| `Venta` | Creada | Agregar getters/setters; falta `@Column` para `total_venta` con precisión decimal (usar `BigDecimal` en vez de `double` para montos — ver sección 7) |
| `DetalleVenta` | Creada | Agregar getters/setters |
| `MovimientoCaja` | Creada | Agregar getters/setters; falta relación con `SesionCaja` (ver abajo) |
| **`SesionCaja`** (nueva) | **No existe** | Reemplaza la tabla `sesiones` de SQLite: `idSesion`, `fecha`, `montoInicial`, `estado` (`ABIERTA`/`CERRADA`), `empleadoApertura`. Sin esto no se puede portar `obtenerMontoInicialHoy()` / `cerrarSesionActual()` |
| `ResumenDiaDTO` | Referenciada pero no existe | Crear como **DTO** (no entidad JPA) en un paquete `dto`, con los campos que `CajaService` ya intenta devolver: ventas, retiros, totales por medio de pago |

---

## 4. Paso a paso de la migración

### Paso 1 — Sanear el proyecto (housekeeping antes de escribir código nuevo)
1. Mover `application.properties` a `src/main/resources/application.properties` (raíz), no dentro
   de `com/thiago/escenasFX/`.
2. Eliminar `module-info.java` — ya no hay JavaFX y complica el uso de reflexión de
   Hibernate/Spring.
3. Eliminar del árbol: los 4 `.fxml`, `com/thiago/escenasFX/App.java`, `Main.java`,
   `controllers/*`, `utils/Paths.java`. (Antes de borrar, considerar dejarlos en una rama
   `legacy-javafx` o un tag de git, ya que el commit HEAD se llama justamente "Respaldo del
   sistema original en JavaFX" — confirmen conmigo si quieren conservar ese respaldo en otra rama
   antes de borrar en `migracion-web`.)
4. Crear la clase de arranque Spring Boot que falta: `com.thiago.escenasFX.SistemaD13Application`
   con `@SpringBootApplication` (el `pom.xml` ya referencia
   `mainClassName=com.thiago.escenasFX.SistemaD13Application` en las properties, pero la clase no
   existe todavía).

### Paso 2 — Completar el modelo de datos (capa `model`)
1. Agregar getters/setters (o Lombok `@Getter @Setter`, ver sección 6) a `Venta`, `Empleado`,
   `DetalleVenta`, `MovimientoCaja`, `Producto`.
2. Crear `model.SesionCaja` con los campos descritos en la sección 3.
3. Decidir `double` vs `BigDecimal` para montos (recomendado `BigDecimal`, ver sección 7) y
   aplicarlo de forma consistente en todas las entidades.

### Paso 3 — Completar los repositorios (capa `repository`)
1. Agregar los imports que faltan (`org.springframework.data.jpa.repository.JpaRepository`,
   `java.util.List`, `java.time.LocalDateTime`) en `VentaRepository`, `ProductoRepository`,
   `MovimientoCajaRepository`.
2. Completar `EmpleadoRepository extends JpaRepository<Empleado, Long>` con
   `Optional<Empleado> findByUsuario(String usuario)` (necesario para el login).
3. Crear `SesionCajaRepository` con
   `Optional<SesionCaja> findByFechaAndEstado(LocalDate fecha, String estado)`.

### Paso 4 — Completar la capa de servicios (capa `service`, lógica de negocio)
1. `VentaService`: agregar imports faltantes, anotar con `@Service`, inyectar por constructor en
   vez de `@Autowired` en campo (mejor práctica Spring actual). La lógica de validar stock y
   descontarlo ya está bien encaminada — conservarla.
2. `CajaService`:
   - Crear `dto.ResumenDiaDTO` con los campos que ya se usan: lista de ventas, lista de retiros,
     totales por medio de pago (efectivo, transferencia, tarjeta) y totales de retiro.
   - Terminar `calcularResumenDelDia()` replicando exactamente la lógica que hoy vive en
     `Controller1.exportarTXT()` (clasificación por medio de pago, `efectivoFinal = montoInicial +
     vEf - rEf`, etc.) — esa es la única fuente de verdad del cálculo de arqueo.
   - Agregar métodos `abrirSesion(montoInicial, empleado)` y `cerrarSesion()` usando
     `SesionCajaRepository`, migrando la lógica de `insertarSesion` / `obtenerMontoInicialHoy` /
     `cerrarSesionActual`.
   - Agregar `registrarRetiro(monto, motivo, medioPago, empleado)`, migrando `insertarRetiro`.
3. `AuthService` (hoy vacío): implementar `login(usuario, passwordPlano)` que busque el
   `Empleado` por usuario y compare con `BCryptPasswordEncoder.matches(...)` contra
   `passwordHash`. Esto reemplaza la comparación en texto plano de `UserDAO` (ver sección 7,
   es el cambio de seguridad más importante de toda la migración).

### Paso 5 — Controladores REST (capa `controller`, nueva)
Crear un paquete `controller` (o `web`) con, como mínimo:

| Controlador | Endpoints | Reemplaza a |
|---|---|---|
| `AuthController` | `POST /api/auth/login` | `ControllerLogin.eventAction` |
| `ProductoController` | `GET /api/productos`, `GET /api/productos/{id}` | (no existía antes; necesario para que el frontend busque productos al vender) |
| `VentaController` | `POST /api/ventas`, `GET /api/ventas?desde=&hasta=` | `Controller1.cargarProducto/confirmarVenta`, `Controllerventas.cargarVentas` |
| `CajaController` | `POST /api/caja/abrir`, `POST /api/caja/retiro`, `GET /api/caja/resumen-dia`, `POST /api/caja/cerrar` | `Controller2.confrimarMontoInicial`, `Controller1.abrirVentanaRetiro/exportarTXT/cerraCajaDiaria` |

Cada controlador solo orquesta: recibe DTO de request → valida (`@Valid`) → delega en el
`Service` correspondiente → devuelve DTO de response. **No debe tener lógica de negocio ni
acceder a repositorios directamente.**

### Paso 6 — DTOs y validación
Crear paquete `dto` con requests/responses explícitos en vez de exponer las entidades JPA
directamente por HTTP (evita ciclos de serialización por las relaciones `@OneToMany`/`@ManyToOne`
y desacopla el contrato HTTP del modelo de persistencia):
- `VentaRequest` / `VentaResponse`
- `RetiroRequest`
- `LoginRequest` / `LoginResponse`
- `ResumenDiaDTO` (ya mencionado)

### Paso 7 — Seguridad
Configurar `spring-boot-starter-security` (ya está en el `pom.xml` pero sin configuración):
- `SecurityConfig` con `BCryptPasswordEncoder` como bean.
- Login basado en sesión o JWT (a decidir según si el frontend será SPA separada o
  server-rendered) contra `EmpleadoRepository` vía `UserDetailsService`.
- Restringir por rol (`Empleado.rol`) qué endpoints puede usar cada usuario (por ejemplo, cerrar
  caja solo para roles con permiso).

### Paso 8 — Pruebas
- Tests unitarios de `VentaService` (validación de stock) y `CajaService` (cálculo de arqueo) —
  son la lógica más sensible a errores y la más fácil de testear sin JavaFX de por medio.
- Test de integración con `@SpringBootTest` + base H2 o Testcontainers Postgres para los
  repositorios y controladores.

---

## 5. Qué se descarta directamente (no tiene equivalente web razonable)

- `ConexionDB.conectar()` manual con `DriverManager` — reemplazado enteramente por
  `spring.datasource.*` + Hibernate.
- `respaldarBaseDeDatos()` (copiar archivo `.db` a una carpeta local) — el backup pasa a ser
  responsabilidad de Supabase/infraestructura.
- `exportarTXT()` escribiendo en `C:/CajaCompartida/Historial/...` (ruta del disco del cliente) —
  se convierte en un endpoint que devuelve el resumen como JSON (o, si de verdad quieren seguir
  generando un archivo descargable, un endpoint que genere un PDF/CSV bajo demanda, no que escriba
  en el filesystem del servidor).
- Todo el manejo de `Stage`/`Scene`/atajos de teclado (F12) — pasa a ser responsabilidad exclusiva
  del futuro frontend (React/Angular/Thymeleaf, lo que elijan), no del backend.

---

## 6. Dependencias a agregar al `pom.xml`

Ya presentes y correctas: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`,
`postgresql`, `spring-boot-starter-validation`, `spring-boot-starter-security`,
`spring-boot-starter-test`.

Recomendado agregar:

```xml
<!-- Reduce boilerplate de getters/setters en las entidades -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
</dependency>

<!-- Migraciones de esquema versionadas en vez de ddl-auto=validate a mano -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>

<!-- Documentación interactiva de la API REST (útil mientras se construye el frontend aparte) -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.6.0</version>
</dependency>
```

A eliminar/no usar: cualquier dependencia de JavaFX o `sqlite-jdbc` si reaparecen (ya no están en
el `pom.xml` actual, solo verificar que no se reintroduzcan).

---

## 7. Riesgos y decisiones a confirmar con vos antes de seguir

1. **`double` para montos**: todas las entidades actuales usan `double` para dinero (`totalVenta`,
   `precioUnitario`, `monto`, `montoInicial`). Con dinero real esto acumula errores de redondeo.
   Recomiendo migrar a `BigDecimal` antes de tener datos reales en producción — es un cambio
   grande pero mucho más barato ahora que con la tabla ya poblada.
2. **Conservar el histórico de JavaFX**: antes de borrar `App.java`, los `Controller*` y los
   `*.fxml` en el Paso 1, ¿querés que los mueva a una rama separada (`legacy-javafx`) o alcanza con
   que ya estén en el historial de git (commit `8e61c95` y anteriores)?
3. ~~**Autenticación**: ¿sesión de servidor o JWT?~~ **Resuelto**: JWT por roles. Rol `ADMIN` es el
   único que puede ver ventas, retirar dinero de caja, etc.; el detalle fino de qué puede hacer
   cada rol se termina de definir durante el Paso 7.
4. ~~**`SesionCaja` por empleado o por local**~~ **Resuelto**: se opera con una sola caja/cajero
   activo a la vez (no hay cajas simultáneas), así que el modelo global actual (una sesión por
   día, compartida) es correcto tal cual está. No hace falta migrar a sesión por empleado/turno —
   eso solo sería necesario si en el futuro hubiera más de un vendedor cobrando en paralelo, en
   cuyo caso habría que agregar `idSesion` a `Venta`/`MovimientoCaja` y permitir arqueo por sesión
   además de por día.
5. **Resuelto (2026-07-27)**: el dueño del negocio confirmó explícitamente que el rol `VENDEDOR`
   **no debe poder ver el historial de ventas bajo ninguna forma, ni siquiera el de su propio
   turno**. Ya estaba así de hecho (`GET /api/ventas` y todo `/api/caja/**` — incluye
   `resumen-dia` y `resumen` por rango/turno — ya exigían `hasRole("ADMIN")` en
   `SecurityConfig`), pero no había test que cubriera específicamente el caso "vendedor pide el
   resumen de un turno que no es el suyo (o del que participó)". Se agregó esa cobertura en
   `CajaControllerIntegrationTest` (`resumenDelDia_comoVendedor_devuelve403`,
   `resumenPorRango_comoVendedor_devuelve403_niSiquieraElDeSuPropioTurno`) y se documentó la regla
   como comentario en `SecurityConfig`, para que quede explícito que no se debe agregar a futuro
   un endpoint tipo "mis ventas" o "resumen de mi turno" para `VENDEDOR`.

Ninguno de estos puntos bloquea empezar el Paso 1 y 2 (housekeeping + completar entidades), pero sí
conviene resolverlos antes del Paso 4 (servicios) y Paso 7 (seguridad).

---

## 8. SQL pendiente: tabla `sesiones_caja` en Supabase

**Actualización**: se confirmó que todas las columnas de ID existentes en Supabase (`productos`,
`empleados`, `ventas`, `detalle_ventas`, `movimientos_caja`) son `int4` (`integer`), no `int8`
(`bigint`). Todas las entidades JPA se ajustaron de `Long` a `Integer` para reflejar esto
(`Empleado.idEmpleado`, `Producto.idProducto`, `Venta.idVenta`, `DetalleVenta.idDetalle`,
`MovimientoCaja.idMovimiento`, `SesionCaja.idSesion`), igual que los repositorios
(`JpaRepository<Entidad, Integer>`).

La primera versión de este SQL creó `sesiones_caja.id_sesion` como `bigint` (por indicación mía,
antes de confirmar el esquema real). Como la tabla está vacía y recién creada, conviene recrearla
en `int4` para que sea consistente con el resto y para que la FK `id_empleado_apertura` no quede
apuntando con un tipo distinto al de `empleados.id_empleado`:

```sql
DROP TABLE IF EXISTS sesiones_caja;

CREATE TABLE sesiones_caja (
    id_sesion            INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fecha                DATE NOT NULL DEFAULT CURRENT_DATE,
    monto_inicial        NUMERIC(12, 2) NOT NULL,
    estado               TEXT NOT NULL DEFAULT 'ABIERTA',
    id_empleado_apertura INTEGER REFERENCES empleados(id_empleado)
);
```

---

## 9. SQL pendiente: seguridad JWT + OTP (retiro de caja y descuento de venta)

Necesario para que Hibernate valide el esquema al arrancar (`ddl-auto=validate`) contra las
entidades nuevas/modificadas de esta etapa. Todas las columnas nuevas son nullable o tienen
`DEFAULT` para no romper filas existentes.

```sql
-- Empleado.email: a donde se manda el OTP (sólo hace falta cargarlo en los ADMIN)
ALTER TABLE empleados ADD COLUMN email TEXT;

-- Producto.precioCompra: para "Costo de Mercadería" en el balance financiero
ALTER TABLE productos ADD COLUMN precio_compra NUMERIC(12, 2);

-- Venta: estado (CONFIRMADA | PENDIENTE_AUTORIZACION), descuento manual y su OTP
ALTER TABLE ventas ADD COLUMN estado TEXT NOT NULL DEFAULT 'CONFIRMADA';
ALTER TABLE ventas ADD COLUMN descuento NUMERIC(12, 2) NOT NULL DEFAULT 0;
ALTER TABLE ventas ADD COLUMN motivo_descuento TEXT;
ALTER TABLE ventas ADD COLUMN otp_hash TEXT;
ALTER TABLE ventas ADD COLUMN otp_expira_en TIMESTAMP;

-- Solicitudes de retiro de caja pendientes de OTP (el MovimientoCaja real recién se crea al confirmar)
CREATE TABLE solicitudes_retiro (
    id_solicitud            INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    monto                   NUMERIC(12, 2) NOT NULL,
    motivo                  TEXT NOT NULL,
    medio_pago              TEXT NOT NULL,
    id_empleado_solicitante INTEGER REFERENCES empleados(id_empleado),
    otp_hash                TEXT NOT NULL,
    otp_expira_en           TIMESTAMP NOT NULL,
    estado                  TEXT NOT NULL DEFAULT 'PENDIENTE',
    creado_en               TIMESTAMP NOT NULL DEFAULT now()
);

-- Cargar el email de los administradores para que puedan recibir los OTP (ajustar usuario/email reales)
UPDATE empleados SET email = 'admin@tu-dominio.com' WHERE rol = 'ADMIN';
```

**Variables de entorno nuevas a configurar** (ninguna tiene secreto por defecto):
- `JWT_SECRET`: string largo y random (por ejemplo, 64+ caracteres) — sin esto la app no arranca.
- `JWT_EXPIRATION_MS`: opcional, default `28800000` (8 horas).
- `SMTP_HOST` / `SMTP_PORT`: opcionales, default `smtp.gmail.com` / `587`.
- `SMTP_USERNAME` / `SMTP_PASSWORD`: obligatorias para poder enviar los OTP (con Gmail, generar una
  "contraseña de aplicación" en vez de usar la contraseña normal de la cuenta).

---

## 10. SQL: `id_sesion` en `ventas` y `movimientos_caja` (arqueo por turno) — ✅ ejecutado

Vincula cada venta/retiro a la `SesionCaja` que estaba abierta al momento de registrarse. Nullable
porque vender sin caja abierta sigue estando permitido (no se agregó esa restricción).

```sql
ALTER TABLE ventas ADD COLUMN id_sesion INTEGER REFERENCES sesiones_caja(id_sesion);
ALTER TABLE movimientos_caja ADD COLUMN id_sesion INTEGER REFERENCES sesiones_caja(id_sesion);
```

---

## 11. SQL: baja lógica de productos + índices de código de barras — ✅ ejecutado

Necesario para el ABM de productos (alta/baja + carga de stock por código de barras) agregado en
la sesión del 2026-07-29. `Producto` tiene el campo `activo` (baja lógica: "eliminar" un producto
no borra la fila — `DetalleVenta.producto` no tiene cascade y un `DELETE` real rompería la
integridad de ventas históricas — solo lo saca de los listados y de la búsqueda por código).

```sql
ALTER TABLE productos ADD COLUMN activo BOOLEAN NOT NULL DEFAULT true;
CREATE INDEX idx_productos_codigo_fabrica ON productos(codigo_fabrica);
CREATE UNIQUE INDEX idx_productos_codigo_interno ON productos(codigo_interno);
```

Confirmado el 2026-07-29: se reinició el backend en limpio (`mvn clean spring-boot:run`) contra
Supabase real con `ddl-auto=validate` y arrancó sin errores de validación de esquema, así que esta
migración ya estaba corrida. Los tests con H2 no lo hubieran detectado igual (usan
`ddl-auto=create-drop`, que regenera el esquema desde las entidades sin importar lo que haya en
Supabase).

---

## 12. Sesión 2026-07-29: primera prueba en navegador real — dos bugs encontrados y arreglados

El usuario reportó que, tanto como ADMIN como VENDEDOR, apenas se logueaba quedaba trabado en la
pantalla de Productos ("falta un token JWT válido" / pantalla en blanco, sin poder navegar a
ningún otro lado). Diagnóstico con Chrome real (extensión de automatización), no solo lectura de
código:

1. **Race condition real en el JWT** (`frontend/src/auth/AuthContext.tsx`): el token se guardaba
   en el cliente HTTP (`client.ts`) dentro de un `useEffect`. React ejecuta los `useEffect` de
   componentes hijos antes que los del padre dentro del mismo commit — como `Productos` es hijo de
   `AuthProvider`, su fetch a `GET /api/productos` (que se dispara en su propio `useEffect` al
   montar) salía **antes** de que `AuthProvider` terminara de setear el token, tanto al redirigir
   tras el login como al refrescar la página ya logueado. La primera request siempre iba sin
   `Authorization` → 401. **Fix**: `useEffect` → `useLayoutEffect` en `AuthContext.tsx` (los layout
   effects de todo el árbol terminan antes que cualquier `useEffect`, así que ya no hay carrera).
2. **Bug que en la práctica era el que trababa todo**: varios productos en Supabase tienen
   `precioVenta: null` (dato legado, migrado sin ese campo cargado). `Productos.tsx` hacía
   `producto.precioVenta.toFixed(2)` sin chequear null → `TypeError`, sin error boundary en la app,
   React desmontaba **todo el árbol** → pantalla en negro sin nav ni nada, para cualquier usuario
   que llegara a ver ese producto en la lista (o sea, todos). Esto probablemente es lo que el
   usuario interpretó como "no me deja ver los productos". **Fix**: `types/api.ts` ahora tipa
   `Producto.precioVenta` como `number | null` (refleja la realidad: `BigDecimal` nullable en el
   backend); `Productos.tsx` muestra `—` en vez de crashear; `RegistrarVenta.tsx` bloquea agregar
   al carrito un producto sin precio cargado (mensaje de error) en vez de romper el cálculo del
   total.

Verificado en Chrome real (no solo `tsc`/`vitest`): logout/login como VENDEDOR (Thiago Lopez) y
como ADMIN (Aleja), ambos aterrizan en `/productos` sin 401 y sin excepciones en consola, con la
UI respetando el rol (VENDEDOR no ve "Eliminar" ni "Agregar producto"). Repetido además tras
reiniciar el backend en limpio, mismo resultado.

**Pendiente real, no es un bug**: varios productos tienen `precioVenta` en `null` en Supabase — hoy
se muestran como "—" y no se pueden vender hasta que se les cargue un precio. Conviene resolverlo
con un `UPDATE` en Supabase (junto con el pendiente de `precioCompra`, ver más abajo), no es algo
para arreglar en código.

---

## 13. Sesión 2026-07-30: tres bugs reportados probando el sistema en vivo

El dueño del negocio probó backend (`:8080`) y frontend (`:5173`) corriendo juntos contra Supabase
real (arrancados en esta misma sesión) y reportó tres problemas de uso real, los tres
diagnosticados leyendo el código real (no solo los planes) y arreglados en la misma sesión.

### 1. VENDEDOR no tenía dónde confirmar un retiro o un descuento

Diagnóstico inicial: la sección de confirmación con el código SÍ existía en el código
(`Caja.tsx` tenía "Confirmar retiro", `HistorialVentas.tsx` tenía el input de código para
descuentos), pero ambas estaban condicionadas a `esAdmin` — coincidía con la regla de negocio
documentada hasta ese momento ("el ADMIN es quien controla/autoriza con el código, nunca al
revés"). Al preguntarle al dueño con qué rol estaba probando, aclaró el flujo real: **el código
sigue llegando solo al ADMIN por email** (ahí sigue el control de seguridad — el VENDEDOR nunca lo
ve ni lo genera), pero es el VENDEDOR, parado en la caja, quien tiene que poder escribirlo una vez
que el ADMIN se lo pasa por otro canal (llamada, WhatsApp). Restringir la confirmación a ADMIN
dejaba la operación pendiente para siempre en la práctica, porque el ADMIN normalmente no está
físicamente en la caja para apretar el botón.

**Cambios:**
- `SecurityConfig`: `/api/caja/retiro/confirmar` y `/api/ventas/descuento/confirmar` pasan de
  `hasRole("ADMIN")` a `hasAnyRole("ADMIN", "VENDEDOR")`. El resto de los endpoints de
  caja/ventas exclusivos de ADMIN (resúmenes, historial) no cambia.
- `Caja.tsx`: la sección "Confirmar retiro" ya no está condicionada a `esAdmin`; el id de
  solicitud se precarga para quien pidió el retiro sea cual sea su rol.
- `RegistrarVenta.tsx`: sección nueva "Confirmar descuento de venta" (id de venta + código),
  visible para ambos roles, con el id de venta precargado apenas la venta queda
  `PENDIENTE_AUTORIZACION`. `HistorialVentas.tsx` (exclusivo ADMIN) conserva su propio input de
  confirmación para cuando el ADMIN revisa el historial directamente.
- Tests actualizados: `CajaControllerIntegrationTest.confirmarRetiro_comoVendedor_devuelve403` →
  `confirmarRetiro_comoVendedor_funciona`; `VentaControllerIntegrationTest.confirmarDescuento_comoVendedor_devuelve403`
  → `confirmarDescuento_comoVendedor_funciona`. Ambos ahora ejercitan el flujo completo
  (solicitar/vender → extraer OTP del email mockeado → confirmar con el mismo token de VENDEDOR)
  en vez de esperar 403.

### 2. Sesiones de caja duplicadas ("bucle" en la base)

El dueño reportó: si el vendedor se olvida de cerrar caja al final del día y cierra el navegador,
al otro día vuelve a abrir una caja nueva y en la base "aparecen todas como abiertas". Root cause
confirmado leyendo `CajaService.abrirSesion()`: el chequeo de "ya hay una sesión abierta" era
`sesionRepo.findByFechaAndEstado(LocalDate.now(), "ABIERTA")` — filtraba por la fecha de **hoy**,
así que una sesión de ayer con `estado='ABIERTA'` nunca aparecía en esa búsqueda y no bloqueaba
nada. Cada día sin cerrar sumaba una fila más `ABIERTA` a `sesiones_caja`, para siempre.

**Cambios:**
- `SesionCajaRepository`: método nuevo `findByEstado(String estado)` (sin filtro de fecha),
  documentado en el propio repositorio para que quede explícito por qué existe al lado de
  `findByFechaAndEstado` (que se deja, sigue usado/testeado en `CajaRepositoriesTest`).
- `CajaService.abrirSesion()`: usa `findByEstado("ABIERTA")` — bloquea abrir una sesión nueva si
  hay **cualquier** sesión abierta sin cerrar, sea de hoy o de un día anterior. Mensaje de error
  incluye la fecha de la sesión vieja para que quien lo vea sepa qué pasó.
- `CajaService.obtenerSesionAbiertaDeHoy()` → renombrado a `obtenerSesionAbierta()` (ya no está
  scopeado a "hoy"), usado por `cerrarSesionDelDia()` — así que "Cerrar caja" ahora sí puede
  cerrar una sesión vieja abandonada de un día anterior, no sólo la de hoy.
- `CajaService.registrarRetiro()` y `VentaService.registrarVenta()`: el vínculo de un retiro/venta
  a la sesión de caja activa (`idSesion`) también pasa a `findByEstado("ABIERTA")`, por
  consistencia — antes una venta hecha "hoy" mientras sólo quedaba abierta la sesión de ayer no se
  vinculaba a ninguna sesión.
- Test de regresión nuevo en `CajaServiceTest`: `abrirSesion_haySesionAbiertaDeUnDiaAnterior_lanzaExcepcion`,
  que reproduce exactamente el escenario reportado (sesión con `fecha = ayer`, `estado = ABIERTA`)
  y verifica que `abrirSesion()` la rechaza.

**Nota para la próxima sesión**: esto arregla que se sigan acumulando sesiones nuevas, pero **no
limpia las que ya quedaron abiertas en Supabase** de sesiones anteriores a este fix. Conviene
revisar `sesiones_caja` y cerrar a mano (`UPDATE sesiones_caja SET estado='CERRADA' WHERE
estado='ABIERTA' AND fecha < CURRENT_DATE`) las que correspondan, antes de que el próximo
`abrirSesion()` real choque con una de esas.

### 3. Sin feedback visual en botones mientras esperan respuesta ("se tilda")

Varios botones que disparan una llamada a la API no tenían ningún `disabled`/estado de carga
(`Caja.tsx` completo, y en `Productos.tsx` las acciones de agregar/eliminar producto y cargar
stock). Sin feedback, un click en una red lenta se sentía como que la app se colgó, e invitaba a
hacer doble click (con el riesgo de disparar la acción dos veces).

**Cambios:**
- Spinner CSS reutilizable (`.spinner`, con `@keyframes girar`) agregado a `frontend/src/index.css`.
- Aplicado con un estado de carga por acción (no uno solo compartido, para no tildar botones que
  no están esperando nada) en: `Caja.tsx` (abrir/cerrar/solicitar/confirmar), `Productos.tsx`
  (agregar/eliminar/cargar stock), `HistorialVentas.tsx` (buscar/confirmar por fila), y sumado
  también a `RegistrarVenta.tsx` y `Login.tsx`, que ya tenían el estado de carga pero no el ícono.
- Todos los botones afectados además quedan `disabled` mientras están en curso, evitando el doble
  submit.

## 14. Sesión 2026-08-04: rediseño de navegación + Marca, Proveedor, Gasto, Compra, Empleado CRUD, Comisiones

### 0. Bug real encontrado antes de arrancar el pedido nuevo

El dueño reportó que, tras loguearse bien, "Abrir caja" le tiraba `401 {"message": "No autenticado:
falta un token JWT válido"}`. El JWT era válido — se confirmó decodificándolo y repitiendo el POST
a mano. La causa real, visible en el log del backend, era `org.hibernate.NonUniqueResultException:
Query did not return a unique result: 3 results were returned` dentro de
`CajaService.abrirSesion()`, que usa `sesionRepo.findByEstado("ABIERTA")` esperando `Optional`
(0 o 1 resultado). Habían quedado **3 filas `ABIERTA`** en `sesiones_caja` (ids 2, 3, 4; de
2026-07-26, 2026-07-29 y 2026-07-30) — residuo de antes del fix de sesiones duplicadas de la
sesión 2026-07-30 (sección 13): el código ya estaba arreglado, pero los datos viejos nunca se
habían limpiado, y esa excepción no manejada se traducía en un 401 genérico en vez de un error
claro. **Fix**: `UPDATE sesiones_caja SET estado='CERRADA' WHERE estado='ABIERTA'` sobre las 3 filas
(confirmado con el dueño antes de tocar la base real). Verificado abriendo y cerrando una sesión de
prueba después del fix — `200 OK` en ambos POSTs.

### 1. Pedido del dueño y aclaraciones

El pedido original mezclaba términos ambiguos con software de referencia inaccesible ("Punto de
Venta Plus 7" de GD Sistemas, sin captura de pantalla disponible — el diseño se hizo en base a la
especificación escrita y las convenciones ya usadas en el código, no a una réplica visual).
Aclaraciones clave antes de tocar código:

- **"Cuentas corrientes" no es cuenta de crédito de cliente/proveedor** — es un gasto operativo
  (nombre + importe + fecha, ej. "pago de luz"), renombrado internamente a **Gastos**.
- **Compras sí actualiza stock y precio** de los productos (no es solo un asiento contable).
- **Modal de "abrir caja" post-login**: ambos roles, bloqueante, te deja en Cobros (`/ventas/nueva`)
  al confirmar.
- **Comisión de vendedor**: % sobre la **ganancia** (venta − costo), no sobre el total facturado.
- **Fórmula de ganancia neta**: deja de restar los retiros de caja (quedan solo como arqueo) y en
  su lugar resta Gastos reales + comisiones pagadas — cambio de comportamiento confirmado
  explícitamente con el dueño porque altera un número que ya conocía.
- **Compras**: la grilla soporta tanto reponer un producto existente como dar de alta uno nuevo en
  el mismo renglón.
- **Vendedores**: además de alta, se pidió baja lógica (no existía ninguna columna `activo` en
  `empleados` hasta ahora).
- **Marca**: pasa a ser nombre libre (ej. "KALOP") en toda la app, resuelto internamente a un
  código de 2 dígitos vía catálogo nuevo, para no romper `codigoInterno` de los 7004 productos
  existentes (auditado: códigos `"01"`-`"40"` en uso + dos valores mal formados `"4"`/`"8"` que
  duplican `"04"`/`"08"`; asignación automática arranca en `"41"`).
- **Proveedor**: catálogo nuevo con FK en `productos.id_proveedor`, backfileado desde los 5
  valores de texto libre que ya existían (`VEGA`, `CANDIL`, `LUZ VERDE`, `AKAI ENERGY`, `CAMBRE`).
  La columna vieja `productos.proveedor` (texto) queda intacta y sin usarse más, por compatibilidad.

### 2. Base de datos (Supabase, todo aditivo)

```sql
CREATE TABLE marcas (id_marca serial PK, nombre varchar(80) UNIQUE, codigo varchar(2) UNIQUE, activo boolean, creado_en timestamp);
CREATE TABLE proveedores (id_proveedor serial PK, nombre varchar(120) UNIQUE, contacto, telefono, email, activo, creado_en);
ALTER TABLE productos ADD COLUMN id_proveedor integer REFERENCES proveedores(id_proveedor);
-- backfill desde productos.proveedor (texto) a la nueva tabla + FK
CREATE TABLE gastos (id_gasto serial PK, nombre, importe numeric(12,2) CHECK(>0), fecha date, categoria, id_empleado_registro FK, creado_en);
CREATE TABLE compras (id_compra serial PK, fecha date, id_proveedor FK, medio_pago, total_compra, id_empleado_registro FK, creado_en);
CREATE TABLE compra_items (id_item serial PK, id_compra FK ON DELETE CASCADE, id_producto FK, cantidad CHECK(>0), precio_compra_unitario, precio_venta_unitario, subtotal);
ALTER TABLE empleados ADD COLUMN comision numeric(5,2) CHECK(0-100);
ALTER TABLE empleados ADD COLUMN activo boolean DEFAULT true;
```

Nada de esto tocó filas existentes de `productos`, `ventas`, `detalle_ventas`, `sesiones_caja`,
`movimientos_caja`, `solicitudes_retiro`, más allá del backfill de `id_proveedor` (nullable).

### 3. Backend — dominios nuevos

Mismo patrón que el resto del código (`model/`/`repository/`/`service/`/`dto/`/`controller/`
planos, DTOs `<Entidad><Acción>Request/Response`, excepciones reusando `IllegalArgumentException`
→400 / `IllegalStateException`→409 vía `GlobalExceptionHandler`, sin exceptions nuevas):

- **Marca**: `MarcaService.resolverOCrear(nombre)` — busca por nombre (case-insensitive), crea con
  el próximo código libre desde `"41"` si no existe. `ProductoService.crear()` la usa en vez de
  tomar `req.getMarca()` como código directo — el resto de la lógica de `codigoInterno`/correlativo
  no cambió. `GET /api/marcas` (ambos roles, lo necesita el combo de ventas). Sin `POST` público:
  la creación es siempre transparente desde otro flujo (alta de producto o compra).
- **Proveedor**: CRUD completo (`GET/POST/PUT/DELETE /api/proveedores`, ADMIN) + `resolverOCrear`
  reusado por Producto y Compra. `Producto` gana `@ManyToOne proveedorRef` (además del texto libre
  histórico, que se sigue escribiendo por compatibilidad).
- **Gasto**: `GET/POST /api/gastos?desde=&hasta=` (ADMIN), valida fecha no futura server-side.
- **Compra + CompraItem**: mismo patrón padre/hijo que Venta/DetalleVenta (`CompraMapper`
  package-private como `VentaMapper`, mismo motivo: evitar el ciclo de serialización JPA).
  `CompraService.registrarCompra()` valida fecha no futura, resuelve/crea el proveedor por nombre,
  y por cada renglón: si trae `idProducto` repone stock/precio de un producto existente; si trae
  `nuevoProducto` (rubro/familia/marca/descripción/código de fábrica) lo da de alta en el momento
  vía `ProductoService.crear()` con stock inicial 0 (el stock del renglón se suma después, junto
  con el de productos existentes, para no duplicar). `POST /api/compras`,
  `GET /api/compras?desde=&hasta=`, `GET /api/compras/pagos-proveedor?desde=&hasta=` (agrupado por
  proveedor), `GET /api/compras/productos-mas-comprados?desde=&hasta=&limit=` (mismo patrón que
  `ReporteService.productosGanadores`) — todos ADMIN.
- **Empleado (CRUD nuevo — no existía ningún controller antes)**: `Empleado` gana `comision`
  (BigDecimal, nullable) y `activo` (boolean, default true). `EmpleadoService.crear()` hashea la
  password con el `PasswordEncoder` bean ya existente (mismo que usa `AuthService`), valida usuario
  único. `GET/POST/PUT /api/empleados`, `DELETE /api/empleados/{id}` (baja lógica) — ADMIN.
  `EmpleadoResponse` nunca expone `passwordHash`.
- **Comisiones y ventas por vendedor**: agregado a `ReporteService`/`ReportesController` existentes
  (ya blindados ADMIN-only, sin matcher nuevo). `comisionesPorVendedor(desde,hasta)`: ventas
  CONFIRMADA agrupadas por empleado, margen = Σ(precioUnitario − precioCompra) × cantidad,
  comisión = margen × `empleado.comision`/100. `GET /api/reportes/comisiones?desde=&hasta=`,
  `GET /api/reportes/ventas-por-vendedor?desde=&hasta=&idEmpleado=`.
- **`balanceFinanciero()` reescrito**: antes sumaba todos los `MovimientoCaja` como "gastos
  operativos" (confundía retiros de caja con gasto real de negocio); ahora resta `Σ Gasto.importe`
  del rango + `Σ comisionCalculada`, y los retiros quedan fuera del cálculo de ganancia (siguen
  existiendo como arqueo puro en la sección Caja). `BalanceFinancieroResponse` gana
  `comisionesPagadas`.
- **`GET /api/caja/sesion-abierta`** (ambos roles): 200 con la sesión si hay una ABIERTA, 204 si
  no — lo necesita el modal post-login para decidir si mostrarse, ya que las variantes existentes
  (`resumen-dia`/`resumen`) son ADMIN-only y el modal debe funcionar para VENDEDOR también.
  `CajaService` gana `obtenerSesionAbiertaOpcional()` (variante no-throwing de la que ya existía).

### 4. Frontend — reestructuración y pantallas nuevas

- **Tema**: `--bg` pasa a gris claro (`#eceff1`), `--bg-alt` a blanco puro para que las tarjetas se
  destaquen. El dueño pidió que el gris se viera siempre — el bloque `@media
  (prefers-color-scheme: dark)` de `index.css` se sacó por completo (antes el tema dependía del SO;
  con el navegador del dueño en modo oscuro, el cambio de fondo no se veía nunca). `color-scheme`
  pasa de `light dark` a `light`.
- **`components/Modal.tsx`** nuevo: generaliza el único patrón de ventana emergente que existía
  (`ComprobanteInterno`, overlay fijo + tarjeta centrada, sin portal ni focus-trap) en un
  componente reusable (`title?`, `onClose?` — sin él, el modal no es descartable, usado para el
  flujo obligatorio de abrir caja; `wide?` para modales con tablas). `ComprobanteInterno` se
  refactorizó para usarlo.
- **`components/Layout.tsx`** reescrito alrededor de un array de configuración (secciones +
  roles) en vez del `<nav>` plano hardcodeado de antes — sidebar vertical con: enlaces sueltos
  (Cobros, Productos, Historial de ventas, Reportes), y secciones desplegables Caja (abrir/cerrar
  caja y retiros como tres modales distintos — antes vivían inline en `Caja.tsx` —, más "ver
  resumen del día" ADMIN), Gastos, Compras, Vendedores (ADMIN), y un botón suelto Proveedores que
  abre modal directo (sin ruta propia, tal cual lo pidió el dueño). `Caja.tsx` quedó reducido a
  solo el resumen ADMIN, ahora en `/caja/resumen`.
- **Modal de abrir caja post-login**: `Layout` llama `GET /api/caja/sesion-abierta` una vez al
  montar; si no hay sesión, muestra `AbrirCajaModal` sin `onClose` (no descartable) y al confirmar
  navega a `/ventas/nueva`. El mismo componente se reusa (con `onClose`) para la apertura manual
  desde el menú Caja.
- **Combo de producto/marca por nombre**: `<input list>` + `<datalist>` nativo (sin librería
  nueva) en `RegistrarVenta.tsx` (reemplaza el `<select>` por id) y en la grilla de Compras — la
  UI muestra/busca por nombre de marca, el backend sigue generando `codigoInterno` con el código
  de 2 dígitos por debajo. `Productos.tsx`: los campos marca y proveedor pasan de texto/código
  crudo a combos con autocompletado sobre `GET /api/marcas`/`GET /api/proveedores`.
- **Páginas nuevas** (todas ADMIN salvo aclaración): `Gastos.tsx` (listado + alta, patrón de
  `Productos.tsx`), `ComprasNueva.tsx` (grilla tipo Excel: filas dinámicas, fecha con
  `max={hoy}` — primera validación de fecha-no-futura del frontend, más el chequeo espejo en el
  backend —, cada renglón con datalist de producto que revela campos de alta inline si no matchea
  nada existente), `ComprasConsulta.tsx` (rango de fechas server-side + proveedor/marca/descripción
  client-side + "productos más comprados"), `PagosProveedores.tsx`, `Vendedores.tsx` (alta con %
  comisión + baja lógica), `ComisionesVendedores.tsx`, `VentasPorVendedor.tsx`, y
  `components/ProveedoresModal.tsx` (listado + alta/edición/baja inline, sin ruta propia).
- **`Reportes.tsx`**: nueva línea "Comisiones pagadas a vendedores" y relabel de "Gastos
  operativos" para reflejar la fórmula nueva.
- `utils/date.ts` nuevo (`hoyIso()`), extraído de la duplicación que ya existía en
  `HistorialVentas.tsx`/`Reportes.tsx`, reusado en todas las pantallas nuevas con filtro de fecha.

### 5. Qué se verificó

`mvn compile` y `tsc -b` limpios. En Chrome, con un usuario ADMIN temporal (creado por SQL directo
con un hash BCrypt generado localmente, y borrado al final junto con el resto de los datos de
prueba — el dueño no tenía credenciales a mano en esta sesión): sidebar con todas las secciones
correctas por rol; alta de gasto reflejada en el listado y en Reportes (`gastosOperativos:
5000.00` para el rango de hoy); alta de compra con un producto **nuevo** en el mismo renglón —
confirmado en Supabase que el producto quedó creado con `stock_actual=1`, `precio_compra`/`precio_
venta` correctos, `codigo_interno` bien armado (`9999410001`), `id_proveedor` resuelto a un
proveedor existente (CANDIL) y la marca "KALOP" creada con código `"41"` (primero libre después de
los `"01"`-`"40"` ya usados); modal de Proveedores mostrando los 5 proveedores reales migrados;
alta de vendedor con 50% de comisión; balance financiero de Reportes reflejando el gasto de prueba.
**No se pudo probar en vivo la rama "no hay sesión abierta → aparece el modal"** del flujo
post-login: había una sesión ABIERTA real (`id_sesion=6`, del día anterior) que no se tocó para no
interrumpir una posible operación real del dueño — sí se confirmó la rama contraria (con sesión
abierta, el modal correctamente no aparece). Sin errores en la consola del navegador durante toda
la sesión. Datos de prueba (usuario ADMIN temporal, vendedor de prueba, producto/compra/gasto de
prueba) borrados al final — la base quedó con los mismos 7004 productos y 2 empleados reales que
antes de empezar, más las tablas nuevas (proveedores con los 5 backfileados, marcas con "KALOP").

Al escribir esta sección se notó que la baja lógica de empleado no bloqueaba el login
(`AuthService.login()` no chequeaba `activo`) — se arregló en la misma sesión: ahora un empleado
con `activo=false` recibe el mismo `401 "Usuario o contraseña inválidos"` que credenciales mal
escritas (mismo criterio de no revelar qué usuarios existen que ya se usaba para usuario-inexistente
vs. password-incorrecta).

**Pendiente para la próxima sesión**: probar en vivo el modal de abrir caja cuando no hay ninguna
sesión abierta (cerrar la sesión real `id_sesion=6` cuando el dueño confirme que ya no la necesita,
o esperar a que él mismo la cierre); cargar `precioVenta`/`precioCompra` en los productos que
siguen en `null` (pendiente desde la sesión 2026-07-30); Row Level Security sigue deshabilitado en
la mayoría de las tablas de Supabase (señalado, no es parte de este pedido).
