package es.uma.tfg.tutor_socratico.controlador;

import es.uma.tfg.tutor_socratico.configuracion.PropiedadesLti;
import es.uma.tfg.tutor_socratico.servicio.ServicioLti;
import es.uma.tfg.tutor_socratico.servicio.ServicioLti.DatosLanzamiento;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;


@Slf4j
@Controller
public class LtiLaunchController {

    private final ServicioLti servicioLti;
    private final PropiedadesLti propiedadesLti;
    private final HttpSessionSecurityContextRepository repositorioContexto = new HttpSessionSecurityContextRepository();

    public LtiLaunchController(ServicioLti servicioLti, PropiedadesLti propiedadesLti) {
        this.servicioLti = servicioLti;
        this.propiedadesLti = propiedadesLti;
    }

    @RequestMapping(value = "/lti/login", method = {RequestMethod.GET, RequestMethod.POST})
    public void iniciarLogin(@RequestParam("login_hint") String loginHint,
                             @RequestParam("target_link_uri") String targetLinkUri,
                             @RequestParam(value = "lti_message_hint", required = false) String messageHint,
                             HttpServletRequest request,
                             HttpServletResponse response) throws IOException {
        String state = UUID.randomUUID().toString();
        String nonce = UUID.randomUUID().toString();

        HttpSession session = request.getSession(true);
        session.setAttribute("lti_state", state);
        session.setAttribute("lti_nonce", nonce);

        StringBuilder url = new StringBuilder(propiedadesLti.authLoginUrl());
        url.append("?scope=openid");
        url.append("&response_type=id_token");
        url.append("&response_mode=form_post");
        url.append("&prompt=none");
        url.append("&client_id=").append(codificar(propiedadesLti.clientId()));
        url.append("&redirect_uri=").append(codificar(targetLinkUri));
        url.append("&login_hint=").append(codificar(loginHint));
        url.append("&state=").append(codificar(state));
        url.append("&nonce=").append(codificar(nonce));
        if (messageHint != null) {
            url.append("&lti_message_hint=").append(codificar(messageHint));
        }

        response.sendRedirect(url.toString());
    }

    @PostMapping("/lti/launch")
    public void lanzar(@RequestParam("id_token") String idToken,
                       @RequestParam(value = "state", required = false) String state,
                       HttpServletRequest request,
                       HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(true);
        String stateEsperado = (String) session.getAttribute("lti_state");
        String nonceEsperado = (String) session.getAttribute("lti_nonce");

        
        
        if (stateEsperado == null || state == null || !stateEsperado.equals(state)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Estado LTI ausente o no válido");
            return;
        }

        DatosLanzamiento datos;
        try {
            datos = servicioLti.procesarLanzamiento(idToken, nonceEsperado);
        } catch (Exception e) {
            log.warn("Lanzamiento LTI rechazado", e);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Lanzamiento LTI no válido");
            return;
        }

        autenticar(datos, request, response, session);

        response.sendRedirect(datos.rol().equals("PROFESOR") ? "/profesor.html" : "/index.html");
    }

    private void autenticar(DatosLanzamiento datos, HttpServletRequest request,
                            HttpServletResponse response, HttpSession session) {
        List<SimpleGrantedAuthority> autoridades = List.of(new SimpleGrantedAuthority("ROLE_" + datos.rol()));
        UsernamePasswordAuthenticationToken autenticacion =
                new UsernamePasswordAuthenticationToken(datos.usuario(), null, autoridades);

        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(autenticacion);
        SecurityContextHolder.setContext(contexto);
        repositorioContexto.saveContext(contexto, request, response);

        session.setAttribute("asignatura_id", datos.asignaturaId());
        session.removeAttribute("lti_state");
        session.removeAttribute("lti_nonce");
    }

    private String codificar(String valor) {
        return URLEncoder.encode(valor == null ? "" : valor, StandardCharsets.UTF_8);
    }
}
