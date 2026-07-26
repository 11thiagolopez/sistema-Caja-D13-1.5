# Plan de migración: Sistema D13 (JavaFX + SQLite) → Spring Boot (REST + PostgreSQL)

## Para retomar mañana

**Qué se hizo hoy** (sesión de verificación end-to-end, ver commits `cc87f43` y `a1dc129`): se
compiló y levantó la app por primera vez contra Supabase real, se probaron los dos flujos de OTP
(descuento en venta y retiro de caja) con emails reales, se probó el arqueo diario con múltiples
medios de pago (efectivo/transferencia/tarjeta), y se agregó configuración CORS (bloqueante para
cualquier frontend en otro origen — sin esto el browser rechaza las requests aunque el backend
responda bien). También se resolvió la decisión abierta de `SesionCaja` (ver sección 7, punto 4):
**se opera con una sola caja/cajero a la vez, así que el modelo global actual es correcto**, no
hace falta migrar a sesión por empleado/turno.

**Fixes de esta sesión** (no eran bugs de lógica de negocio, sino de entorno/config):
- `application.properties` estaba guardado en ISO-8859-1 en vez de UTF-8 (rompía el filtrado de
  recursos de Maven) — corregido, y se restauró el mapeo de encoding en `.settings/` que lo causó.
- `sesiones_caja.id_sesion`/`id_empleado_apertura` habían quedado como `bigint` en Supabase en vez
  de `int4` (la sección 8 ya lo documentaba) — corregido en Supabase.
- Un producto tenía `stock_actual = NULL` en Supabase, lo que rompía la deserialización porque el
  campo es un `int` primitivo — se corrigió el dato y se agregó `NOT NULL DEFAULT 0` a la columna.
- Ojo con Eclipse: este workspace tiene Eclipse abierto, y su compilador incremental (sin Lombok
  configurado) a veces pisa el build de Maven en `target/classes` con clases rotas
  (`Unresolved compilation problems` al levantar). Si pasa, alcanza con `mvn clean spring-boot:run`
  en vez de `compile` + `spring-boot:run` por separado.

**Para que la app arranque y se pueda probar, en este orden:**

1. Correr el SQL de la **sección 9** (columnas nuevas en `empleados`/`productos`/`ventas` + tabla
   `solicitudes_retiro`) en Supabase — ✅ hecho.
2. Configurar variables de entorno: `JWT_SECRET`, `SUPABASE_DB_PASSWORD`, `GMAIL_APP_PASSWORD` —
   ✅ hecho (seteadas como variables de usuario de Windows en esta máquina; si se despliega a un
   hosting, hay que configurarlas también ahí).
3. Cargar el `email` real de los empleados con `rol='ADMIN'` en Supabase — ✅ hecho.
4. Compilar el proyecto (`mvn clean compile`) — ✅ compila limpio, 52 archivos fuente.
5. Levantar la app y probar el flujo completo — ✅ probado: login con roles, venta con descuento +
   OTP, retiro de caja + OTP, y arqueo diario con efectivo/transferencia/tarjeta, todo contra datos
   reales de Supabase.

**Lo que sigue pendiente** (sin bloquear nada de lo anterior): Paso 8 (tests, arrancando ahora) y
Flyway (opcional).

**Segunda vuelta de la misma sesión**: se creó la rama `legacy-javafx` (apunta a `8e61c95`, el
último commit con el código JavaFX completo) para conservar el histórico sin ensuciar
`migracion-web`. Y surgió un caso real que la sección 7 punto 4 no contemplaba del todo: la
distribuidora opera con **dos vendedores por día, uno de mañana y otro de tarde, nunca
simultáneos** (no contradice "una sola caja a la vez" — son secuenciales, no concurrentes). Esto
reveló:

1. Un bug real en `calcularResumenDelDia()`: el monto inicial se buscaba solo en la sesión
   **ABIERTA** de hoy; en cuanto se cerraba la última sesión del día, caía a `$0` silenciosamente.
2. No había forma de separar cuánto vendió/retiró cada turno — `Venta` y `MovimientoCaja` no
   estaban vinculados a ninguna `SesionCaja`.
3. El arqueo (`/api/caja/resumen-dia`) tampoco soportaba rango de fechas (solo "hoy"), a diferencia
   del ranking de productos y el balance financiero, que sí — un problema para pedir un arqueo de
   un mes completo.

**Se implementó**: `Venta` y `MovimientoCaja` ahora tienen `idSesion` (nullable, requiere la
migración SQL de más abajo). Cada venta/retiro se vincula automáticamente a la sesión ABIERTA en
ese momento (o queda sin vincular si no hay ninguna — vender sin caja abierta sigue permitido, no
se agregó esa restricción). Nuevo endpoint `GET /api/caja/resumen?desde=&hasta=`: devuelve el total
combinado del rango (mismo formato que `/resumen-dia`, y de paso resuelve el bug del punto 1 porque
el monto inicial del total ahora suma el de **cada** sesión del rango, esté abierta o cerrada) más
un desglose por cada turno individual dentro de ese rango (`sesiones: [...]`, cada uno con su propio
arqueo). Probado con dos turnos reales el mismo día (mañana $24.000 cerrada, tarde $5.000 abierta)
— el total y cada desglose dieron matemáticamente correctos.

**Nota**: las ventas/retiros registrados *antes* de este cambio no tienen `idSesion` (la columna no
existía), así que no aparecen retroactivamente en el desglose por turno de sesiones viejas — pero
sí siguen sumando correctamente en el total por fecha, no se perdió ningún dato.

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
- [x] Paso 8 — Tests: **hecho parcialmente**. `VentaServiceTest` (11) y `CajaServiceTest` (11)
      cubren la lógica de negocio más sensible (validación de stock, reglas del descuento manual,
      los dos flujos de OTP, y el arqueo por turno) con Mockito, sin tocar la base real. **Sigue
      pendiente**: tests de integración con `@SpringBootTest` + H2/Testcontainers para repositorios
      y controladores (la otra mitad del Paso 8 original).
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
