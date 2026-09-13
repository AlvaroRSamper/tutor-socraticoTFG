package es.uma.tfg.tutor_socratico.configuracion.seguridad;

import es.uma.tfg.tutor_socratico.configuracion.PropiedadesUsuarios;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class RegistroUsuarios {

    private final String asignaturaPorDefecto;
    private final List<UserDetails> usuarios = new ArrayList<>();
    private final Map<String, String> asignaturaPorUsuario = new HashMap<>();

    public RegistroUsuarios(PropiedadesUsuarios propiedades, PasswordEncoder passwordEncoder) {
        this.asignaturaPorDefecto = propiedades.asignaturaPorDefecto();

        for (PropiedadesUsuarios.Usuario usuario : propiedades.usuarios()) {
            registrar(usuario.id(), usuario.passwordHash(), usuario.rol(), usuario.asignatura(), passwordEncoder);
        }

        cargarFicheros(propiedades.fichero(), passwordEncoder);

        log.info("Usuarios cargados para login manual: {}", usuarios.size());
    }

    public List<UserDetails> usuarios() {
        return List.copyOf(usuarios);
    }

    public String asignaturaDe(String id) {
        return asignaturaPorUsuario.getOrDefault(id, asignaturaPorDefecto);
    }

    private void cargarFicheros(String rutas, PasswordEncoder passwordEncoder) {
        if (rutas == null || rutas.isBlank()) {
            return;
        }
        for (String ruta : rutas.split(",")) {
            cargarFichero(ruta.trim(), passwordEncoder);
        }
    }

    private void cargarFichero(String ruta, PasswordEncoder passwordEncoder) {
        if (ruta == null || ruta.isBlank()) {
            return;
        }
        Path fichero = Path.of(ruta);
        if (!Files.isRegularFile(fichero)) {
            log.error("Fichero de usuarios no encontrado en '{}'. El alumnado no podrá iniciar sesión.", ruta);
            return;
        }
        try {
            List<String> lineas = Files.readAllLines(fichero, StandardCharsets.UTF_8);
            for (String linea : lineas) {
                procesarLinea(linea, passwordEncoder);
            }
        } catch (IOException e) {
            log.error("No se pudo leer el fichero de usuarios '{}'", ruta, e);
        }
    }

    private void procesarLinea(String linea, PasswordEncoder passwordEncoder) {
        if (linea == null || linea.isBlank()) {
            return;
        }
        String[] campos = linea.split(",", -1);
        String id = campos[0].replace("﻿", "").trim();
        if (id.isEmpty() || id.equalsIgnoreCase("id")) {
            return;
        }
        String password = campos.length > 1 ? campos[1].trim() : "";
        String rol = campos.length > 2 ? campos[2].trim() : "";
        String asignatura = campos.length > 3 ? campos[3].trim() : "";
        registrar(id, password, rol.isEmpty() ? null : rol,
                asignatura.isEmpty() ? null : asignatura, passwordEncoder);
    }

    private void registrar(String id, String password, String rol, String asignatura, PasswordEncoder passwordEncoder) {
        if (id == null || id.isBlank()) {
            return;
        }
        if (password == null || password.isBlank()) {
            log.warn("Usuario '{}' ignorado: sin contraseña.", id);
            return;
        }
        usuarios.add(User.withUsername(id)
                .password(normalizar(password, passwordEncoder))
                .roles(rol == null || rol.isBlank() ? "ALUMNO" : rol)
                .build());
        if (asignatura != null && !asignatura.isBlank()) {
            asignaturaPorUsuario.put(id, asignatura);
        }
    }

    private String normalizar(String password, PasswordEncoder passwordEncoder) {
        if (password.startsWith("{")) {
            return password;
        }
        if (password.startsWith("$2a$") || password.startsWith("$2b$") || password.startsWith("$2y$")) {
            return "{bcrypt}" + password;
        }
        return passwordEncoder.encode(password);
    }
}
