package team.projectpulse.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.oauth2.jwt.Jwt;
import team.projectpulse.user.PeerEvaluationUser;
import team.projectpulse.user.UserRepository;

/**
 * Enforces FR-SEC-active-account on every request, not only at login: a token issued before an account was
 * deactivated stops working the moment she is, instead of at its two-hour expiry.
 *
 * <p>Loads the user named by the token's {@code userId} claim (a primary-key read) and refuses the token when that
 * user is deactivated or no longer exists. The refusal is an {@code AccountStatusException}, so the bearer-token
 * entry point answers {@code 401} and the SPA clears the token and returns to login. When the account is active the
 * token is handed to the delegate, the standard converter that reads the authorities claim.
 *
 * <p>Not a {@code @Component}: {@code SecurityConfiguration} builds it around its own delegate bean and passes it to
 * the resource server explicitly.
 */
public class ActiveAccountJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository userRepository;
    private final Converter<Jwt, ? extends AbstractAuthenticationToken> delegate;


    public ActiveAccountJwtAuthenticationConverter(UserRepository userRepository, Converter<Jwt, ? extends AbstractAuthenticationToken> delegate) {
        this.userRepository = userRepository;
        this.delegate = delegate;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Object claim = jwt.getClaim("userId");
        Integer userId = claim instanceof Number number ? Integer.valueOf(number.intValue()) : null;
        PeerEvaluationUser user = userId == null ? null : this.userRepository.findById(userId).orElse(null);
        if (user == null || !user.isEnabled()) {
            throw new DisabledException("The account is deactivated.");
        }
        return this.delegate.convert(jwt);
    }

}
