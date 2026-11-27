package com.thiago.escenasFX.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.thiago.escenasFX.dto.CambiarEstadoTrabajoRequest;
import com.thiago.escenasFX.dto.FotoTrabajoResponse;
import com.thiago.escenasFX.dto.MiTrabajoResponse;
import com.thiago.escenasFX.dto.NotaTrabajoRequest;
import com.thiago.escenasFX.dto.NotaTrabajoResponse;
import com.thiago.escenasFX.dto.TrabajoTecnicoResponse;
import com.thiago.escenasFX.dto.UrlArchivoResponse;
import com.thiago.escenasFX.dto.ArchivoTrabajoResponse;
import com.thiago.escenasFX.model.Empleado;
import com.thiago.escenasFX.model.NotaTrabajo;
import com.thiago.escenasFX.model.Venta;
import com.thiago.escenasFX.repository.EmpleadoRepository;
import com.thiago.escenasFX.service.FotoTrabajoService;
import com.thiago.escenasFX.service.MisTrabajosService;
import com.thiago.escenasFX.service.ArchivoTrabajoService;

import jakarta.validation.Valid;

/**
 * "Mis trabajos" del técnico — espejo chico de VentaController/trabajo-domicilio, pero siempre
 * acotado al propio técnico (ver MisTrabajosService). Nunca recibe un idEmpleado del body: quién
 * opera sale siempre del JWT, igual que en RolAutenticado. Devuelve {@link TrabajoTecnicoResponse},
 * sin el teléfono ni el email del cliente: el contacto con el cliente es del jefe.
 */
@RestController
@RequestMapping("/api/mis-trabajos")
public class MisTrabajosController {

    private final MisTrabajosService misTrabajosService;
    private final FotoTrabajoService fotoTrabajoService;
    private final ArchivoTrabajoService archivoTrabajoService;
    private final EmpleadoRepository empleadoRepo;

    public MisTrabajosController(MisTrabajosService misTrabajosService, FotoTrabajoService fotoTrabajoService,
            ArchivoTrabajoService archivoTrabajoService, EmpleadoRepository empleadoRepo) {
        this.archivoTrabajoService = archivoTrabajoService;
        this.misTrabajosService = misTrabajosService;
        this.fotoTrabajoService = fotoTrabajoService;
        this.empleadoRepo = empleadoRepo;
    }

    @GetMapping
    public List<MiTrabajoResponse> listar(Authentication authentication) {
        Empleado tecnico = RolAutenticado.empleado(authentication, empleadoRepo);
        // La lista no necesita notas, fotos ni adjuntos: se piden al abrir el detalle.
        return misTrabajosService.listar(tecnico).stream()
            .map(v -> new MiTrabajoResponse(TrabajoTecnicoResponse.de(v), List.of(), List.of(), List.of()))
            .toList();
    }

    @GetMapping("/{id}")
    public MiTrabajoResponse obtener(@PathVariable Integer id, Authentication authentication) {
        Empleado tecnico = RolAutenticado.empleado(authentication, empleadoRepo);
        return toResponse(misTrabajosService.obtener(id, tecnico), tecnico);
    }

    @PostMapping("/{id}/estado")
    public MiTrabajoResponse cambiarEstado(@PathVariable Integer id,
            @Valid @RequestBody CambiarEstadoTrabajoRequest request, Authentication authentication) {
        Empleado tecnico = RolAutenticado.empleado(authentication, empleadoRepo);
        return toResponse(misTrabajosService.cambiarEstado(id, request.getEstado(), tecnico), tecnico);
    }

    @PostMapping("/{id}/notas")
    public ResponseEntity<NotaTrabajoResponse> agregarNota(@PathVariable Integer id,
            @Valid @RequestBody NotaTrabajoRequest request, Authentication authentication) {
        Empleado tecnico = RolAutenticado.empleado(authentication, empleadoRepo);
        NotaTrabajo nota = misTrabajosService.agregarNota(id, request.getTexto(), tecnico);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(nota));
    }

    @PostMapping("/{id}/fotos")
    public ResponseEntity<List<FotoTrabajoResponse>> subirFotos(@PathVariable Integer id,
            @RequestParam("fotos") List<MultipartFile> fotos, @RequestParam(required = false) String momento,
            Authentication authentication) {
        Empleado tecnico = RolAutenticado.empleado(authentication, empleadoRepo);
        Venta venta = misTrabajosService.obtenerAbierto(id, tecnico);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(fotoTrabajoService.subir(venta, tecnico, FotosHttp.leer(fotos), false, momento));
    }

    @GetMapping("/{id}/fotos/{idFoto}")
    public ResponseEntity<byte[]> verFoto(@PathVariable Integer id, @PathVariable Integer idFoto,
            Authentication authentication) {
        Empleado tecnico = RolAutenticado.empleado(authentication, empleadoRepo);
        misTrabajosService.obtener(id, tecnico);
        return FotosHttp.responder(fotoTrabajoService.obtener(id, idFoto));
    }

    @DeleteMapping("/{id}/fotos/{idFoto}")
    public ResponseEntity<Void> borrarFoto(@PathVariable Integer id, @PathVariable Integer idFoto,
            Authentication authentication) {
        Empleado tecnico = RolAutenticado.empleado(authentication, empleadoRepo);
        misTrabajosService.obtenerAbierto(id, tecnico);
        fotoTrabajoService.borrar(id, idFoto, tecnico, false);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/archivos")
    public ResponseEntity<List<ArchivoTrabajoResponse>> subirArchivo(@PathVariable Integer id,
            @RequestParam("archivo") MultipartFile archivo, @RequestParam(required = false) String momento,
            Authentication authentication) {
        Empleado tecnico = RolAutenticado.empleado(authentication, empleadoRepo);
        Venta venta = misTrabajosService.obtenerAbierto(id, tecnico);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(archivoTrabajoService.subir(venta, tecnico, archivo, archivo.getSize(), false, momento));
    }

    @GetMapping("/{id}/archivos/{idArchivo}/url")
    public UrlArchivoResponse urlArchivo(@PathVariable Integer id, @PathVariable Integer idArchivo,
            Authentication authentication) {
        Empleado tecnico = RolAutenticado.empleado(authentication, empleadoRepo);
        misTrabajosService.obtener(id, tecnico);
        return new UrlArchivoResponse(archivoTrabajoService.urlParaVer(id, idArchivo));
    }

    @DeleteMapping("/{id}/archivos/{idArchivo}")
    public ResponseEntity<Void> borrarArchivo(@PathVariable Integer id, @PathVariable Integer idArchivo,
            Authentication authentication) {
        Empleado tecnico = RolAutenticado.empleado(authentication, empleadoRepo);
        misTrabajosService.obtenerAbierto(id, tecnico);
        archivoTrabajoService.borrar(id, idArchivo, tecnico, false);
        return ResponseEntity.noContent().build();
    }

    private MiTrabajoResponse toResponse(Venta venta, Empleado tecnico) {
        List<NotaTrabajoResponse> notas = misTrabajosService.listarNotas(venta.getIdVenta(), tecnico)
            .stream().map(this::toResponse).toList();
        return new MiTrabajoResponse(TrabajoTecnicoResponse.de(venta), notas,
            fotoTrabajoService.listar(venta.getIdVenta()), archivoTrabajoService.listar(venta.getIdVenta()));
    }

    private NotaTrabajoResponse toResponse(NotaTrabajo nota) {
        return new NotaTrabajoResponse(nota.getIdNota(), nota.getTexto(), nota.getEmpleado().getNombre(),
            nota.getCreadoEn());
    }
}
