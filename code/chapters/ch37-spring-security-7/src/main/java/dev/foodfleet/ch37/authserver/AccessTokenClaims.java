package dev.foodfleet.ch37.authserver;

import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenExchangeAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

/**
 * Shapes access tokens for FoodFleet's resource servers (read from the Spring Security 7.1.1 sources: by default
 * the authorization server signs RS256, sets {@code aud} to the client id, sends {@code scope} as a JSON array
 * and sets no {@code typ} header):
 * <ul>
 * <li>{@code typ: at+jwt} and a {@code client_id} claim (RFC 9068), so an access token can't be mistaken for an ID
 * token;</li>
 * <li>{@code aud}: the APIs the scopes are for, or, for token exchange, the audience the caller asked for;</li>
 * <li>{@code roles}: the user's FoodFleet roles.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class AccessTokenClaims {

    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> accessTokenShape() {
        return context -> {
            if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                return;
            }
            context.getJwsHeader().type("at+jwt");
            context.getClaims().claim("client_id", context.getRegisteredClient().getClientId());
            List<String> audiences = new ArrayList<>();
            if (AuthorizationGrantType.TOKEN_EXCHANGE.equals(context.getAuthorizationGrantType())
                    && context.getAuthorizationGrant() instanceof OAuth2TokenExchangeAuthenticationToken exchange) {
                audiences.addAll(exchange.getAudiences());
            }
            if (audiences.isEmpty()) {
                audiences.addAll(ScopeAudiences.forScopes(context.getAuthorizedScopes()));
            }
            if (!audiences.isEmpty()) {
                context.getClaims().audience(audiences);
            }
            if (context.getPrincipal() != null) {
                List<String> roles = context.getPrincipal().getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .filter(a -> a.startsWith("ROLE_"))
                        .map(a -> a.substring(5))
                        .toList();
                if (!roles.isEmpty()) {
                    context.getClaims().claim("roles", roles);
                }
            }
        };
    }
}
