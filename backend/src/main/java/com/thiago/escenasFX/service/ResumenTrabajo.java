package com.thiago.escenasFX.service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.thiago.escenasFX.model.Venta;

/**
 * Foto de cómo estaba un trabajo a domicilio, para avisarle a los ADMIN qué cambió con un texto que
 * se entienda en la notificación del navegador ("Cambios en #12 (Soldados 750): fecha viernes 28/11
 * → sábado 29/11; técnico Fabián → Juan"). Se toma antes de editar y después de guardar, y se
 * comparan. Los artículos se comparan como lista (descripción × cantidad a precio): si algo de eso
 * cambió, se dice "artículos" con el total de antes y el de ahora.
 */
record ResumenTrabajo(String cliente, String telefono, String direccion, String descripcion, String indicaciones,
        LocalDate fecha, LocalTime horaDesde, LocalTime horaHasta, String tecnico, String estadoTrabajo,
        BigDecimal total, List<String> articulos) {

    private static final Locale ES_AR = Locale.forLanguageTag("es-AR");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("EEEE dd/MM", ES_AR);
    private static final Map<String, String> ESTADOS = Map.of(
        "AGENDADO", "Agendado",
        "EN_CAMINO", "En camino",
        "EN_CURSO", "En curso",
        "TERMINADO_TECNICO", "Terminado por el técnico",
        "TERMINADO", "Terminado",
        "CERRADO_Y_COBRADO", "Cobrado",
        "CANCELADO", "Cancelado");

    static ResumenTrabajo de(Venta v) {
        List<String> articulos = v.getDetalles().stream()
            .map(d -> d.getDescripcion() + " x" + d.getCantidad() + " a " + d.getPrecioUnitario().stripTrailingZeros().toPlainString())
            .toList();
        return new ResumenTrabajo(v.getClienteNombre(), v.getClienteTelefono(), v.getDireccionTrabajo(),
            v.getDescripcionTrabajo(), v.getObservacionesTecnico(), v.getFechaProgramada(), v.getHoraDesde(),
            v.getHoraHasta(), v.getEmpleadoTecnico() != null ? v.getEmpleadoTecnico().getNombre() : null,
            v.getEstadoTrabajo(), v.getTotalVenta(), articulos);
    }

    /** "#12 (Soldados 750)": cómo se nombra el trabajo en los avisos. */
    static String nombre(Venta v) {
        String direccion = vacioANull(v.getDireccionTrabajo());
        String cliente = vacioANull(v.getClienteNombre());
        String detalle = direccion != null ? direccion : cliente;
        return "#" + v.getIdVenta() + (detalle != null ? " (" + detalle + ")" : "");
    }

    /** "Nuevo trabajo #12 (Soldados 750): viernes 28/11 de 10:00 a 12:00 — Técnico: Fabián". */
    String textoNuevo(Venta v) {
        StringBuilder sb = new StringBuilder("Nuevo trabajo ").append(nombre(v)).append(": ").append(cuando());
        sb.append(" — Técnico: ").append(tecnico != null ? tecnico : "sin asignar");
        if (vacioANull(descripcion) != null) {
            sb.append(" — ").append(descripcion.trim());
        }
        return sb.toString();
    }

    /** Lo que cambió entre {@code antes} y este, o lista vacía si nada que le importe a otro. */
    List<String> cambiosDesde(ResumenTrabajo antes) {
        List<String> cambios = new ArrayList<>();
        if (!Objects.equals(antes.fecha, fecha)) {
            cambios.add("fecha " + dia(antes.fecha) + " → " + dia(fecha));
        }
        if (!Objects.equals(antes.horaDesde, horaDesde) || !Objects.equals(antes.horaHasta, horaHasta)) {
            cambios.add("horario " + horario(antes.horaDesde, antes.horaHasta) + " → " + horario(horaDesde, horaHasta));
        }
        if (!Objects.equals(antes.tecnico, tecnico)) {
            cambios.add("técnico " + (antes.tecnico != null ? antes.tecnico : "sin asignar") + " → "
                + (tecnico != null ? tecnico : "sin asignar"));
        }
        if (!Objects.equals(antes.estadoTrabajo, estadoTrabajo)) {
            cambios.add("estado " + estado(antes.estadoTrabajo) + " → " + estado(estadoTrabajo));
        }
        if (!Objects.equals(antes.articulos, articulos)) {
            cambios.add("artículos (total " + pesos(antes.total) + " → " + pesos(total) + ")");
        }
        if (distinto(antes.direccion, direccion)) {
            cambios.add("dirección: " + textoONada(direccion));
        }
        if (distinto(antes.cliente, cliente)) {
            cambios.add("cliente: " + textoONada(cliente));
        }
        if (distinto(antes.telefono, telefono)) {
            cambios.add("teléfono: " + textoONada(telefono));
        }
        if (distinto(antes.descripcion, descripcion)) {
            cambios.add("descripción: " + textoONada(descripcion));
        }
        if (distinto(antes.indicaciones, indicaciones)) {
            cambios.add("indicaciones al técnico");
        }
        return cambios;
    }

    static String textoCambios(Venta v, List<String> cambios) {
        return "Cambios en " + nombre(v) + ": " + String.join("; ", cambios);
    }

    static String pesos(BigDecimal monto) {
        NumberFormat formato = NumberFormat.getCurrencyInstance(ES_AR);
        formato.setMaximumFractionDigits(monto != null && monto.stripTrailingZeros().scale() > 0 ? 2 : 0);
        return formato.format(monto != null ? monto : BigDecimal.ZERO).replace(" ", " ");
    }

    private String cuando() {
        String h = horario(horaDesde, horaHasta);
        return dia(fecha) + ("sin horario".equals(h) ? "" : " " + h);
    }

    private static String dia(LocalDate fecha) {
        return fecha != null ? fecha.format(DIA) : "sin fecha";
    }

    private static String horario(LocalTime desde, LocalTime hasta) {
        if (desde != null && hasta != null) {
            return "de " + desde + " a " + hasta;
        }
        if (desde != null) {
            return "desde las " + desde;
        }
        return "sin horario";
    }

    private static String estado(String codigo) {
        return codigo == null ? "—" : ESTADOS.getOrDefault(codigo, codigo);
    }

    private static boolean distinto(String a, String b) {
        return !Objects.equals(vacioANull(a), vacioANull(b));
    }

    private static String textoONada(String s) {
        String limpio = vacioANull(s);
        return limpio != null ? limpio : "(se borró)";
    }

    private static String vacioANull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
