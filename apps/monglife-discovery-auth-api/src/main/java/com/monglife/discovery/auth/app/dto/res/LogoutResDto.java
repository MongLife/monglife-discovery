package com.monglife.discovery.auth.app.dto.res;

import lombok.Builder;

@Builder
public record LogoutResDto(
        Long accountId
) {
}