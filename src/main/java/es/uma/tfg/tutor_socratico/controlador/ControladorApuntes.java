package es.uma.tfg.tutor_socratico.controlador;

import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import es.uma.tfg.tutor_socratico.dto.ApunteResumen;
import es.uma.tfg.tutor_socratico.dto.RespuestaMensaje;
import es.uma.tfg.tutor_socratico.dto.RespuestaOperacion;
import es.uma.tfg.tutor_socratico.dto.RespuestaValidacion;
import es.uma.tfg.tutor_socratico.servicio.ServicioValidacionApuntes;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/alumno/apuntes")
public class ControladorApuntes {

    private final ServicioValidacionApuntes servicioValidacion;

    public ControladorApuntes(ServicioValidacionApuntes servicioValidacion) {
        this.servicioValidacion = servicioValidacion;
    }

    private String asignaturaDe(HttpSession session) {
        String asig = (String) session.getAttribute("asignatura_id");
        return (asig == null || asig.isBlank()) ? "General" : asig;
    }

    @PostMapping("/validar")
    public ResponseEntity<?> validar(
            @RequestParam(value = "texto", required = false) String texto,
            @RequestParam(value = "tema", required = false) String tema,
            @RequestParam(value = "archivo", required = false) MultipartFile archivo,
            HttpSession session) {

        String contenido = extraerTexto(texto, archivo);
        if (contenido == null || contenido.isBlank()) {
            return ResponseEntity.badRequest().body(new RespuestaMensaje("Sube un archivo (PDF/TXT) o pega tus apuntes."));
        }
        String feedback = servicioValidacion.validar(contenido, tema, asignaturaDe(session));
        return ResponseEntity.ok(new RespuestaValidacion(feedback, contenido));
    }

    @PostMapping("/guardar")
    public ResponseEntity<?> guardar(
            @RequestParam(value = "texto", required = false) String texto,
            @RequestParam(value = "titulo") String titulo,
            @RequestParam(value = "archivo", required = false) MultipartFile archivo,
            Authentication auth,
            HttpSession session) {

        if (titulo == null || titulo.isBlank()) {
            return ResponseEntity.badRequest().body(new RespuestaMensaje("Indica un título para tus apuntes."));
        }
        String contenido = extraerTexto(texto, archivo);
        if (contenido == null || contenido.isBlank()) {
            return ResponseEntity.badRequest().body(new RespuestaMensaje("No hay contenido que guardar."));
        }
        return ResponseEntity.ok(servicioValidacion.guardar(auth.getName(), asignaturaDe(session), titulo, contenido));
    }

    @GetMapping("/listar")
    public List<ApunteResumen> listar(Authentication auth, HttpSession session) {
        return servicioValidacion.listar(auth.getName(), asignaturaDe(session));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<RespuestaOperacion> borrar(@PathVariable Long id, Authentication auth) {
        boolean ok = servicioValidacion.borrar(id, auth.getName());
        if (ok) {
            return ResponseEntity.ok(new RespuestaOperacion(true, "Apunte eliminado"));
        }
        return ResponseEntity.badRequest().body(new RespuestaOperacion(false, "No se pudo eliminar"));
    }

    
    private String extraerTexto(String textoPegado, MultipartFile archivo) {
        if (archivo != null && !archivo.isEmpty()) {
            String nombre = archivo.getOriginalFilename() != null ? archivo.getOriginalFilename().toLowerCase() : "";
            String tipo = archivo.getContentType() != null ? archivo.getContentType().toLowerCase() : "";
            try (InputStream in = archivo.getInputStream()) {
                if (nombre.endsWith(".pdf") || tipo.contains("pdf")) {
                    return new ApachePdfBoxDocumentParser().parse(in).text();
                }
                return new String(archivo.getBytes(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                log.error("No se pudo extraer texto del archivo {}: {}", nombre, e.getMessage());
                return null;
            }
        }
        return textoPegado;
    }
}
