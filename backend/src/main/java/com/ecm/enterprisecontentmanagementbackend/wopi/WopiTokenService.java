
package com.ecm.enterprisecontentmanagementbackend.wopi;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class WopiTokenService {

    private static final String WOPI_ISSUER = "urn:ecm:wopi";
    private static final long TOKEN_EXPIRATION_SECONDS = 3600;

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;

    public WopiTokenService(
            JwtEncoder encoder,
            @Qualifier("wopiJwtDecoder") JwtDecoder decoder
    ) {
        this.encoder = encoder;
        this.decoder = decoder;
    }

    public WopiToken generateToken(
            Long documentId,
            String username,
            boolean canWrite
    ) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(TOKEN_EXPIRATION_SECONDS);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(WOPI_ISSUER)
                .subject(username)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("purpose", "wopi")
                .claim("documentId", documentId)
                .claim("canWrite", canWrite)
                .build();

        JwsHeader header = JwsHeader
                .with(MacAlgorithm.HS256)
                .build();

        String token = encoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();

        return new WopiToken(
                token,
                expiresAt.toEpochMilli()
        );
    }

    public WopiAccess validateToken(String token, Long documentId) {
        Jwt jwt = decoder.decode(token);

        // Validate token purpose
        if (!"wopi".equals(jwt.getClaimAsString("purpose"))) {
            throw new IllegalArgumentException("Invalid WOPI token purpose");
        }

        // Validate issuer without URL conversion
        if (!WOPI_ISSUER.equals(jwt.getClaimAsString("iss"))) {
            throw new IllegalArgumentException("Invalid WOPI token issuer");
        }

        // Validate document scope
        Number tokenDocumentId = jwt.getClaim("documentId");

        if (tokenDocumentId == null ||
                tokenDocumentId.longValue() != documentId.longValue()) {
            throw new IllegalArgumentException("Invalid document scope");
        }

        // Validate username
        if (jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new IllegalArgumentException("Missing WOPI user");
        }

        return new WopiAccess(
                jwt.getSubject(),
                Boolean.TRUE.equals(jwt.getClaim("canWrite"))
        );
    }

    public record WopiToken(
            String accessToken,
            long accessTokenTtl
    ) {}

    public record WopiAccess(
            String username,
            boolean canWrite
    ) {}
}
