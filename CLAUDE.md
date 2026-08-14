Contexto del Proyecto:
Estamos migrando un sistema de gestión a Spring Boot con JWT y flujos OTP.

Instrucciones Obligatorias:

Antes de escribir código nuevo del backend, lee SIEMPRE el archivo plan-migracion.md para respetar las reglas de negocio y seguridad.

Antes de escribir código del frontend, lee SIEMPRE el archivo plan-frontend.md (stack elegido, contrato de API completo, y la regla de negocio de qué puede ver cada rol).

Revisa la sección "Estado Actual" de este mismo archivo para saber exactamente en qué paso nos encontramos.

Estado Actual (Actualizado al 2026-08-14, dolarización de precios completa + gate diario de
cotización + Compras en ARS/USD):

**Dolarización de precios, implementada de punta a punta** (retoma el PENDIENTE del corte
anterior). Cada producto tiene ahora un ancla en USD (`precioVentaUsd`/`precioCompraUsd`,
recalculada sola cada vez que se guarda un precio en pesos vía alta/edición/compra) y, al abrir
caja, un recálculo masivo (`ProductoRepository.reajustarPreciosPorCotizacion`) vuelve a pesos
contra la cotización oficial VENTA del día, redondeando al múltiplo de $100 más cercano — dominio
nuevo `CotizacionDolar`/`CotizacionService`/`CotizacionApiClient` (dos APIs públicas gratuitas
encadenadas, dolarapi.com → dolar-bna.vercel.app). Migración inicial aplicada sobre los ~6900
productos reales el 2026-08-12 con la cotización real de ese día ($1515) — solo estableció el
ancla, no tocó los precios en pesos vigentes. Los comprobantes (tickets/remitos/PDFs) no se
tocaron: siguen mostrando únicamente pesos.

**Gate diario de cotización, nuevo** (pedido posterior del dueño, amplía el alcance original más
allá de solo "abrir caja"): nadie —ni ADMIN ni VENDEDOR— puede operar el sistema hasta que exista
una cotización cargada para hoy. Se intenta cargar sola al loguear (`GET/POST /api/cotizacion/*`,
nuevo `CotizacionController`); si las dos APIs fallan, solo ADMIN puede cargarla a mano y recién
ahí se desbloquea también VENDEDOR. `components/CotizacionGateModal.tsx` (nuevo) bloquea el
sistema entero desde `Layout.tsx`, con prioridad sobre el modal de "abrir caja" ya existente. Un
cartel puntual ("Cotización del día: USD $X") avisa **solo a ADMIN**, y solo cuando la carga la
disparó esa misma sesión — VENDEDOR nunca ve el valor del dólar.

**Compras en ARS/USD + % de ganancia** (`ComprasNueva.tsx`). Cada renglón tiene un `<select>`
ARS/USD independiente para precio de compra y precio de venta, más una columna nueva "%
Ganancia" entre ambos que autocompleta el precio de venta (recargo sobre el costo: 100% = vender
al doble). Todo el cálculo es de UI contra la cotización del día — el contrato del backend
(`CompraItemRequest`) no cambió, sigue recibiendo siempre pesos.

Backend: **134 tests** verdes (`mvn -q -o test`). `tsc -b` limpio. Probado por API (`curl`) contra
Supabase real en las dos sesiones — **no hubo navegador automatizado disponible en ninguna de las
dos**, así que ninguna de las pantallas nuevas (gate, cartel, Compras con ARS/USD) se vio todavía
renderizada en Chrome; queda pendiente confirmarlo visualmente la próxima vez que se abra el
sistema. Detalle técnico completo en `plan-migracion.md` sección 17 y `plan-frontend.md` "Estado
actual".

Estado Anterior (Actualizado al 2026-08-10, sesión larga con cuatro pedidos encadenados del dueño):

**1. Reportes ampliados.** Nuevos reportes "Ventas por marca" (`ReporteService.ventasPorMarca`,
`GET /api/reportes/ventas-por-marca`) y "Ventas por forma de pago" (`ventasPorFormaPago`,
`GET /api/reportes/ventas-por-forma-pago`). "Pagos a proveedores" ganó un drill-down por
proveedor puntual (filtro client-side sobre el `GET /api/compras` que ya existía, sin endpoint
nuevo). De paso se descubrió que "Ventas por vendedor" y "Comisiones" ya existían como pantallas
propias bajo la sección "Vendedores" del sidebar — se reorganizó todo bajo una sola sección
"Reportes" consolidada en `Layout.tsx` (solo se movieron los links, sin tocar lógica).

**2. Módulo Presupuestos** (`/presupuestos`, ambos roles). Cotización de productos de catálogo
que NO descuenta stock ni genera una `Venta` — modelo `Presupuesto`/`DetallePresupuesto`, espejo
de `Venta`/`DetalleVenta` pero sin OTP ni caja. Se puede enviar por email y descargar en PDF.

**3. PDF branded compartido, nuevo.** Infraestructura reusable para generar PDFs con el logo,
dirección y teléfono del local (`Arce 790, CABA — Tel: 1123752626`): `ComprobanteHtmlBuilder`
(arma el XHTML), `PdfService` (HTML→PDF con `openhtmltopdf`; el logo se cachea en base64 desde
`backend/src/main/resources/static/logo-d13.png`) y `EmailService.enviarConAdjuntoPdf` (adjunta
el PDF, el cuerpo del mail queda simple). Presupuestos y Ventas comparten esta misma
infraestructura — no hay dos generadores de PDF distintos.

**4. Ida y vuelta importante en Cobros — leer antes de tocar `RegistrarVenta.tsx`.** En un pedido
intermedio de esta sesión se agregó a Cobros un selector "Artículo / Copia de llave" y un ítem
manual de precio libre (columna nueva `DetalleVenta.tipo`). Un pedido posterior del dueño
(documento `prompt-claude-code-trabajos-domicilio.md`, guardado fuera del repo) pidió sacar
**ambas cosas** de Cobros — que volviera a ser solo catálogo — porque ese caso de uso en realidad
es un trabajo a domicilio, no una venta de mostrador. Se revirtió la UI de Cobros pero **se
conservó el modelo de datos**: `DetalleVenta.tipo` sigue existiendo, ahora con los valores
`ARTICULO` | `COPIA` (legado, ya no seleccionable desde ningún lado) | `SERVICIO` (mano de obra),
porque el módulo nuevo de Trabajo a domicilio lo reusa.

**5. Trabajo a domicilio** (`/ventas/domicilio`, **exclusivo ADMIN**). Pantalla nueva: cliente,
teléfono, dirección, descripción del trabajo, artículos de catálogo (mismo componente que Cobros,
extraído a `components/BuscadorProductoCarrito.tsx`, con descuento real de stock), mano de obra
de precio libre (`tipo="SERVICIO"`, sin producto) y técnico asignado. Botones "Guardar borrador"
(estado `EN_PROGRESO`, no genera comprobante) y "Cerrar y cobrar" (estado `CONFIRMADA`). Un
borrador se puede reabrir por número de trabajo y seguir editando — al reeditar,
`VentaService.guardarTrabajoDomicilio` primero devuelve el stock de las líneas viejas con
producto y recién después vuelve a descontar las nuevas, para no descuadrar el inventario.
`Venta` ganó columnas nuevas: `tipo_venta` (`MOSTRADOR`|`DOMICILIO`), `cliente_nombre`,
`cliente_telefono`, `direccion_trabajo`, `descripcion_trabajo`, `estado_trabajo`
(`AGENDADO`|`EN_CURSO`|`COMPLETADO`|`COBRADO`), `id_empleado_tecnico`.

**6. Comprobantes automáticos indexados por `id_venta`**, sin guardar PDFs de antemano — se
arman al vuelo con la infraestructura del punto 3: ticket para venta de mostrador, remito (con
cliente/dirección/técnico) para trabajo a domicilio. Desde Consulta de ventas
(`HistorialVentas.tsx`, ADMIN) se puede descargar o mandar por mail el comprobante de cualquier
venta/trabajo `CONFIRMADA`, y filtrar la lista por tipo de venta, técnico y estado del trabajo.

**7. Comisión reescrita para atribuir por línea, no por venta**
(`ReporteService.comisionesPorVendedor`). Una línea `SERVICIO` de un trabajo a domicilio atribuye
su monto BRUTO (sin restar costo) al **técnico asignado**, nunca a quien cobró la venta; cualquier
otra línea (artículo/copia, en mostrador o en domicilio) sigue atribuyendo el margen a quien
registró la venta, como siempre. Verificado en vivo: en un trabajo con 1 artículo + 1 línea de
mano de obra, el reporte de Comisiones mostró la ganancia del artículo del lado del vendedor y el
monto de mano de obra separado del lado del técnico.

**8. Rol TECNICO, nuevo.** Se agregó a `Empleado.rol` (antes solo ADMIN|VENDEDOR). Solo TECNICO
tiene comisión — en el alta de empleado (`Vendedores.tsx`) el campo "Comisión %" solo se muestra
si el rol elegido es TECNICO, y `EmpleadoService.crear`/`actualizar` fuerza `comision=null` para
cualquier otro rol aunque el request traiga un valor (VENDEDOR tiene sueldo fijo, ADMIN no cobra
comisión — regla de negocio explícita del dueño). El selector de "Técnico asignado" en Trabajo a
domicilio filtra `empleados` a solo `rol==='TECNICO'`. TECNICO **no tiene acceso al sistema** a
propósito: no se agregó ningún matcher de rol nuevo en `SecurityConfig`, así que si algún día
alguien loguea con ese rol, todo endpoint protegido por `hasRole`/`hasAnyRole` le devuelve 403
(decisión explícita del dueño: "no hace falta definir qué pantallas puede ver").

Todo compilado (backend **118 tests** / `tsc -b` limpio) y probado end-to-end en Chrome contra
Supabase real en cada punto (presupuesto con ítem manual + PDF + email, trabajo a domicilio
completo con reapertura y sin doble descuento de stock, comprobantes por mail, alta de técnico
con filtro del selector) — datos y stock de prueba restaurados al terminar cada prueba.
Migraciones aplicadas directamente sobre el proyecto real (`jyumiicapspsxgucirjd`) vía MCP de
Supabase; `get_advisors` corrido después para confirmar que no quedó ninguna tabla nueva sin RLS
(no se crearon tablas — todo fueron columnas nuevas sobre `ventas`).

**PENDIENTE — dolarización de precios (pedido del dueño, sin arrancar, retomar mañana).** La idea:
pasar el precio de venta de los productos a dólar oficial VENTA, que el sistema busque la
cotización del día al abrir caja (fija por sesión/turno, no se recalcula venta a venta), y que al
vender se calcule el precio en pesos automáticamente. Es técnicamente viable (agregar un precio en
USD por producto + guardar la cotización del día en la sesión de caja al abrirla, usando alguna
API pública tipo dolarapi.com), pero faltan decisiones de negocio del dueño antes de tocar código:
1. ¿El precio de COMPRA (costo) también pasa a dólares, o solo el de venta?
2. Migración de los ~6900 productos que ya están cargados en pesos: ¿con qué cotización se hace la
   conversión inicial a USD?
3. ¿Se redondea el precio final en pesos (a qué múltiplo — $100, $500), o queda el cálculo exacto?
4. Si la API de cotización falla el día que se abre caja: ¿usar la última cotización guardada,
   bloquear la apertura de caja, o permitir cargarla a mano ese día?

Estado Anterior (Actualizado al 2026-08-05, sesión larga — hay un bloqueante de máquina, leer el
final de esta sección antes de tocar nada):

**Continuación de la sesión de búsqueda por marca: ABM en línea de Productos, rediseño de
navegación, y un bug de datos real en la resolución de marcas (con fix de código escrito pero
SIN DESPLEGAR — ver "Bloqueante" al final).** Resumen por tema:

1. **Edición en línea en la tabla de Productos** (`Productos.tsx`, solo ADMIN): tocar una celda de
   descripción, marca, precio de venta o stock la vuelve editable ahí mismo (Enter guarda, Escape
   cancela, click afuera guarda). Rubro y código interno quedan de solo lectura a propósito —
   forman parte de la identidad del producto (van dentro de `codigoInterno` junto con el
   correlativo) y editarlos ahí generaría inconsistencias. Nuevo endpoint
   `PATCH /api/productos/{id}` (`ProductoUpdateRequest`, solo los campos no-nulos se actualizan,
   solo ADMIN en `SecurityConfig`), con tests. Se agregó también la columna "Código de fábrica" a
   la tabla (antes no se mostraba). Probado extremo a extremo contra Supabase real (subí stock de
   un producto real y lo volví a bajar para no dejar el inventario alterado).
2. **Rediseño de navegación**: login redirige a "Cobros" (`/ventas/nueva`) en vez de Productos.
   Sidebar colapsable con un botón ☰ en el header (`sidebarAbierta` en `Layout.tsx`), útil para
   liberar pantalla en Cobros. Sidebar y header quedan fijos (`position: fixed`/`height: 100svh`
   en `.layout`), sólo el contenido central scrollea. Header: franja azul (`--accent`, ahora
   `#005a9e`, más oscuro que el original `#007acc`) con "Sistema D13" centrado en la ventana
   (`position: fixed; left: calc(50% - 28px)`, corrido sutilmente a la izquierda a pedido), fuente
   Montserrat 900 (TT Neoris Pro es paga, no se pudo usar sin licencia — si el dueño consigue el
   archivo con licencia, reemplazar en `index.html`/`App.css`), con sombra sutil para dar
   profundidad a la letra. Padding del header reducido (era muy grueso). Tabla de productos con
   bordes más gruesos y oscuros (`--table-border`). Inputs con borde gris visible y fondo blanco
   (antes eran invisibles contra el fondo). Alta de producto: la flechita del stock ahora
   incrementa de a 1 (antes usaba el mismo paso 0.01 que el precio). El campo Marca del alta
   aclara que se escribe el nombre (no un código) y que no importan mayúsculas/minúsculas; al
   crear el producto se informa en un cartel el nombre y código de marca asignado, aclarando si la
   marca era nueva.
3. **Bug de datos real, encontrado por el dueño probando en producción**: al escribir una marca
   que ya existía en productos reales pero no estaba en el catálogo `marcas` (que quedó casi vacío
   — sólo tenía 1-2 filas de pruebas, totalmente desconectado de los nombres de marca reales que
   ya traían los 7004 productos migrados/backfileados a mano), `MarcaService.resolverOCrear`
   generaba un código nuevo (41+) en vez de reciclar el que esa marca ya usaba. Pasó dos veces:
   "KALOP" (sesión 2026-08-04, código nuevo 41, sin producto asociado — se limpió) y "kallay"
   (sesión de hoy, código nuevo 42, con un producto real: "llave tesorito" id 9214 — se corrigió a
   mano en Supabase para usar el código real de KALLAY, "21", el más usado de los 3 códigos
   históricos distintos que tiene esa marca en los datos migrados). **Fix de código escrito**
   (`ProductoRepository.buscarUsoHistoricoDeMarca` + `MarcaService.crearDesdeUsoHistorico`): antes
   de generar un código nuevo, busca si el nombre ya se usó en `productos` y, si es así, recicla
   el `(numeroMarca, marca)` que más se repite — código y mayúscula/minúscula histórica real, no
   lo que tipeó quien carga el producto nuevo. Tests nuevos en `MarcaServiceTest.java` (no existía
   antes). **Dato importante para el negocio, no arreglado ni por el código ni a mano**: los datos
   migrados tienen múltiples nombres de marca compartiendo el mismo `numero_marca` y viceversa
   (ej. "01" es CAMBRE para 564 productos pero también ACYTRA/PRIVE/KALLAY para unos pocos cada
   uno) — es ruido heredado de cómo funcionaba el código de marca en el sistema viejo (parece haber
   sido un código local por rubro, no un identificador de marca globalmente único). El fix nuevo
   elige el código más frecuente por nombre, lo cual es razonable pero no "corrige" esa mezcla
   histórica — si en algún momento se quiere prolijizar de verdad, hay que decidir junto con el
   dueño qué código es el "correcto" para cada marca y renumerar, una tarea aparte y más grande.

**BLOQUEANTE para retomar — LEER PRIMERO:** el compilador de Java de esta máquina se rompió a
mitad de la sesión (`ClassFormatError: Illegal UTF8 string in constant pool` en una clase interna
del JDK, `com/sun/tools/javac/code/Symtab$4`) — reproducible incluso compilando un "Hola mundo"
vacío sin relación con el proyecto, así que no es un problema de este código. Estado del archivo
`C:\Program Files\Java\jdk-24\lib\modules`: 142.450.906 bytes, fecha de escritura sin cambios
desde la instalación (25/8/2025) — no es que algo lo esté tocando ahora mismo; lo más probable es
que tenga corrupción real en disco que antes se enmascaraba con la página en caché de RAM del SO,
y dejó de poder leerse bien en algún momento de esta sesión larga. El dueño va a reiniciar la PC y,
si sigue fallando, reinstalar el JDK 24. **Antes de dar por hecho que el fix de marcas está
andando, hay que**:
1. Confirmar que `mvn -q -o test` (backend) corre limpio de nuevo (sin el `ClassFormatError`).
2. Reiniciar el backend (`mvn spring-boot:run` no tiene hot-reload de Java — un `kill` del proceso
   viejo por el puerto 8080 y volver a levantarlo). El backend que sigue corriendo ahora mismo
   todavía tiene el `MarcaService` VIEJO (sin el fix) compilado en memoria.
3. Recién ahí probar de nuevo el flujo de alta de producto con una marca existente tipeada con
   otra capitalización, para confirmar que recicla el código correcto en vez de crear uno nuevo.

**Pendiente de sesiones anteriores, sigue sin tocar**: RLS deshabilitado en las 12 tablas de
`public` (reportado por el advisor de Supabase, hay que decidir políticas antes de activarlo); no
se probó en el navegador el flujo de "Cargar precioVenta/precioCompra" pendiente para los
productos que los tienen en null.

Estado Anterior (Actualizado al 2026-08-05, primera parte de la sesión):

**Búsqueda de productos por marca (Productos, Ventas, Compras) + fix de un bug real en la
generación de código interno.** El dueño modificó a mano la tabla `productos` en Supabase:
renombró la columna vieja `marca` (que en realidad guardaba el código de 2 dígitos) a
`numero_marca`, y agregó una columna `marca` nueva con el nombre real de cada marca cargado a
mano para los 7004 productos históricos (el catálogo `marcas` sólo tenía 1 fila, así que la
resolución código→nombre para productos legacy fallaba silenciosamente y mostraba el código crudo
en vez del nombre). La entidad `Producto` sólo tenía un campo `marca` mapeado a esa columna y
usado como si fuera el código, así que tras el cambio manual en Supabase la generación de
`codigoInterno`/correlativo en altas nuevas (`ProductoService.crear`, `MarcaService`) quedaba rota
(confirmado corriendo los tests: 5 ya fallaban antes de tocar nada). Fix: `Producto` ahora tiene
`numeroMarca` (columna `numero_marca`, el código de 2 dígitos, para `codigoInterno`) y `marca`
(columna `marca`, el nombre para mostrar/buscar, sincronizado con `Marca.nombre` al crear). En el
frontend se sacó la indirección `nombrePorCodigoMarca[p.marca] ?? p.marca` (pensada para resolver
código→nombre vía el catálogo) en Productos.tsx, RegistrarVenta.tsx, ComprasConsulta.tsx y
ComprasNueva.tsx, porque ahora `producto.marca` ya es el nombre real listo para mostrar/filtrar —
el filtro de marca en Productos y el buscador en Registrar venta (que ya existían) ahora funcionan
de verdad para el catálogo legacy. Tests backend (`mvn test`, incluidos los 5 que ya estaban
rotos) y `tsc -b` verdes. **Pendiente: no se probó end-to-end en el navegador contra Supabase
real** (compiló y los tests automáticos pasan, pero no se abrió Chrome). También pendiente
—preexistente, no de esta sesión—: RLS deshabilitado en las 12 tablas de `public` (`productos`
incluida), reportado por el advisor de Supabase; no se tocó porque hay que decidir políticas antes
de activarlo.

Estado Anterior (Actualizado al 2026-08-04):

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