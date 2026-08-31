package es.uma.tfg.tutor_socratico.configuracion;

import org.springframework.boot.context.properties.ConfigurationProperties;


@ConfigurationProperties(prefix = "lti")
public record PropiedadesLti(String issuer, String clientId, String jwksUri, String authLoginUrl) {
}
