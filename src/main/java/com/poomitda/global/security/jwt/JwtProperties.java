package com.poomitda.global.security.jwt;

import jakarta.validation.constraints.*;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
    @NotBlank
    String issuer,

    @NotBlank
    String secret,

    @NotNull
    Duration accessTokenExpiration,

    @NotNull
    Duration refreshTokenExpiration
) {}
