package com.duy.book.auth;

import jakarta.validation.constraints.NotBlank;

public record SocialLoginRequest(
        @NotBlank(message = "code is required")
        String code,
        @NotBlank(message = "provider is required")
        String provider
) {
}
