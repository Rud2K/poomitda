package com.poomitda.global.storage;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 프로필 이미지 설정값을 관리한다.
 *
 * @param mode 이미지 URL 제공 모드
 */
@Validated
@ConfigurationProperties(prefix = "app.profile-image")
public record ProfileImageProperties(
    @NotBlank
    String mode
) {}
