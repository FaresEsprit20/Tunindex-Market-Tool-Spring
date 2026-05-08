package com.tunindex.market_tool.common.dto.token;

import com.tunindex.market_tool.common.entities.UnifiedToken;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetTokenDto {
    private Long id;
    private String token;
    private String userEmail;
    private LocalDateTime expirationDate;
    private LocalDateTime creationDate;
    private boolean isUsed;

    public static PasswordResetTokenDto fromEntity(UnifiedToken entity) {
        if (entity == null) return null;
        return PasswordResetTokenDto.builder()
                .id(entity.getId())
                .token(entity.getToken())
                .userEmail(entity.getUserEmail())
                .expirationDate(entity.getExpirationDate())
                .creationDate(entity.getCreationDate())
                .isUsed(entity.isUsed())
                .build();
    }
} 