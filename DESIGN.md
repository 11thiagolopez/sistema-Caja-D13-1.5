# DESIGN.md — oNce

Reglas visuales del frontend. Los valores viven en `frontend/src/index.css` (variables `--*` y
estilos base) y las piezas de layout en `frontend/src/App.css`. Antes de tocar una pantalla, revisala
con `/web-interface-guidelines <archivo>` (`.claude/commands/`).

**Para quién:** gente del local que no es de tecnología, en la PC del mostrador y en el celular
del técnico. Cada pantalla tiene que entenderse sin explicación: textos claros, botones grandes,
una sola acción principal.

## Marca

- Nombre: **oNce**. Logo en `components/Logo.tsx` (`Logo`, `LogoIcono`, `LogoTexto`): cuadrado azul
  con "o" y "N"; la N del texto es el mismo trazo. Sobre fondo oscuro, usar `claro`.
- Tipografía: **Inter** (400, 500, 600, 700).

## Colores ("Azul sistema")

| Uso | Variable | Valor |
|---|---|---|
| Menú lateral, títulos | `--navy` | `#0E1F3D` |
| Acción principal, enlaces, ítem activo | `--azul` | `#1F4E9C` |
| Hover del azul | `--azul-hover` | `#183E7D` |
| Fondo suave azul (avisos info, botón suave) | `--azul-suave` | `#EEF3FB` |
| Fondo de la app | `--bg` | `#F3F5F8` |
| Tarjetas | `--bg-alt` | `#FFFFFF` |
| Bordes | `--border` / `--input-border` | `#E2E5EA` / `#C9CED6` |
| Texto / secundario | `--text` / `--apagado` | `#1F2430` / `#6B7280` |
| Éxito | `--ok` sobre `--ok-bg` | `#1E7E34` / `#E3F1E6` |
| Atención | `--aviso` sobre `--aviso-bg` | `#8A5A00` / `#FFF4D6` |
| Error, peligro | `--error` sobre `--error-bg` | `#B42318` / `#FBE3E0` |

El color nunca va solo: siempre con texto ("Cobrada", "Anulada", "Pendiente").

## Componentes

- **Botones** (42px de alto, radio 8px, 14px semibold):
  - sin clase: principal azul. Uno solo por pantalla.
  - `btn-secundario`: borde azul.
  - `btn-suave`: fondo azul claro.
  - `btn-peligro`: rojo lleno, para anular.
  - `btn-peligro-borde`: borde rojo, para cerrar caja o rechazar.
  - `btn-exito`: verde, para aprobar o WhatsApp.
  - `btn-chico`: 32px. `btn-icono`: cuadrado de 42px, siempre con `aria-label`.
  - `boton-grande`: 56px, para el técnico en el celular.
  - Mientras trabaja: spinner + texto con "…" ("Guardando…").
- **Campos**: 42px, label arriba (13px medium). En foco: borde azul y anillo `--azul-anillo`. Con
  error: `aria-invalid="true"` (borde rojo de 2px) y el mensaje en rojo debajo.
- **Etiquetas de estado**: `badge` + `badge-ok` / `badge-aviso` / `badge-info` / `badge-error`.
- **Avisos**: `alerta` + `alerta-ok` / `alerta-aviso` / `alerta-error` (barra de color a la izquierda).
- **Tarjetas**: `tarjeta` (blanco, borde, radio 12px). Indicadores: `reportes-tarjeta`; la más
  importante con `caja-tarjeta-destacada` (fondo azul).
- **Tablas**: encabezado gris en mayúsculas de 12px, filas separadas por una línea fina, montos
  alineados a la derecha (`celda-numero`). Con muchas columnas, envolver en `tabla-scroll`.
- **Opciones excluyentes** (Todas / Efectivo / Tarjeta): `segmentado` con `aria-pressed`.

## Estructura de pantalla

- **Menú lateral** (navy): solo para *ir a un lugar*, agrupado en Caja, Ventas, Trabajos, Gestión,
  Reportes y Configuración. Las secciones se ven siempre abiertas.
- **Barra superior**: menú, buscador global (`BuscadorGlobal`, Ctrl+K), avisos, usuario y Salir.
- **Encabezado de pantalla** (`EncabezadoPagina`): título, etiqueta opcional al lado (ej. "Caja
  abierta") y acciones arriba a la derecha.
- **Regla**: si una acción tiene botón dentro de su pantalla, no va en el menú lateral. Ejemplos:
  - Abrir caja, cerrar caja y retiro de dinero están en Cobros y Resumen del día (`BotonesCaja`).
  - "+ Nueva compra" está en Compras.

## Accesibilidad y celular

- Los botones miden al menos 44px en el celular. Los campos tienen texto de 16px en el celular
  (si no, el iPhone hace zoom).
- Foco visible: borde de 3px `--azul-foco`. Se respeta `prefers-reduced-motion`.
- Antes de borrar o anular algo, siempre se pide confirmación.
- Cada pantalla resuelve sus estados vacío, cargando y error.
