package dev.foodfleet.lab08.security;

import java.util.Collection;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/** (From chapter 37.) Spring Security's hook for {@link TokenClaims}: used by the resource server's JwtAuthenticationConverter. */
public class TokenAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        return TokenClaims.authorities(jwt.getClaims()).stream()
                .map(a -> (GrantedAuthority) new SimpleGrantedAuthority(a)).toList();
    }
}
