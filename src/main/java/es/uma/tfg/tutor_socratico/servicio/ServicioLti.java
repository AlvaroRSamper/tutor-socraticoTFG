package es.uma.tfg.tutor_socratico.servicio;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import es.uma.tfg.tutor_socratico.configuracion.PropiedadesLti;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
public class ServicioLti {

    private static final String CLAIM_ROLES = "https://purl.imsglobal.org/spec/lti/claim/roles";
    private static final String CLAIM_CONTEXTO = "https://purl.imsglobal.org/spec/lti/claim/context";

    private final PropiedadesLti propiedadesLti;
    private ConfigurableJWTProcessor<SecurityContext> procesador;

    public ServicioLti(PropiedadesLti propiedadesLti) {
        this.propiedadesLti = propiedadesLti;
    }

    public record DatosLanzamiento(String usuario, String rol, String asignaturaId) {
    }

    public DatosLanzamiento procesarLanzamiento(String idToken, String nonceEsperado) throws Exception {
        JWTClaimsSet claims = obtenerProcesador().process(idToken, null);

        if (!propiedadesLti.issuer().equals(claims.getIssuer())) {
            throw new BadCredentialsException("Emisor LTI no válido");
        }
        if (claims.getAudience() == null || !claims.getAudience().contains(propiedadesLti.clientId())) {
            throw new BadCredentialsException("Audiencia LTI no válida");
        }
        
        
        if (nonceEsperado == null || nonceEsperado.isBlank()
                || !nonceEsperado.equals(claims.getStringClaim("nonce"))) {
            throw new BadCredentialsException("Nonce LTI ausente o no válido");
        }
        
        Date expiracion = claims.getExpirationTime();
        if (expiracion == null || expiracion.before(new Date())) {
            throw new BadCredentialsException("Token LTI sin expiración válida o caducado");
        }

        return new DatosLanzamiento(claims.getSubject(), extraerRol(claims), extraerAsignaturaId(claims));
    }

    private String extraerRol(JWTClaimsSet claims) throws Exception {
        List<String> roles = claims.getStringListClaim(CLAIM_ROLES);
        boolean instructor = roles != null && roles.stream()
                .anyMatch(r -> r.contains("Instructor") || r.contains("Administrator"));
        return instructor ? "PROFESOR" : "ALUMNO";
    }

    private String extraerAsignaturaId(JWTClaimsSet claims) throws Exception {
        Map<String, Object> contexto = claims.getJSONObjectClaim(CLAIM_CONTEXTO);
        if (contexto == null || contexto.get("id") == null) {
            return null;
        }
        return contexto.get("id").toString();
    }

    private synchronized ConfigurableJWTProcessor<SecurityContext> obtenerProcesador() throws Exception {
        if (procesador == null) {
            JWKSource<SecurityContext> claves = JWKSourceBuilder
                    .create(new URL(propiedadesLti.jwksUri()))
                    .build();
            ConfigurableJWTProcessor<SecurityContext> nuevo = new DefaultJWTProcessor<>();
            nuevo.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, claves));
            procesador = nuevo;
        }
        return procesador;
    }
}
