package com.twitter.lambda.posts;

import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;

import java.security.interfaces.RSAPublicKey;
import java.util.concurrent.TimeUnit;

/**
 * Validates Auth0 JWT tokens.
 * Reads AUTH0_DOMAIN and AUTH0_AUDIENCE from Lambda environment variables.
 */
public class JwtValidator {

    private final String domain;
    private final String audience;
    private final JwkProvider jwkProvider;

    public JwtValidator() {
        this.domain   = System.getenv("AUTH0_DOMAIN");
        this.audience = System.getenv("AUTH0_AUDIENCE");

        this.jwkProvider = new JwkProviderBuilder(domain)
                .cached(5, 10, TimeUnit.MINUTES)
                .build();
    }

    public DecodedJWT validate(String authorizationHeader) throws Exception {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new IllegalArgumentException("Missing Authorization header");
        }

        String token = authorizationHeader.startsWith("Bearer ")
                ? authorizationHeader.substring(7)
                : authorizationHeader;

        DecodedJWT unverified = JWT.decode(token);

        RSAPublicKey publicKey = (RSAPublicKey) jwkProvider
                .get(unverified.getKeyId())
                .getPublicKey();

        Algorithm algorithm = Algorithm.RSA256(publicKey, null);

        JWTVerifier verifier = JWT.require(algorithm)
                .withIssuer("https://" + domain + "/")
                .withAudience(audience)
                .build();

        return verifier.verify(token);
    }
}
