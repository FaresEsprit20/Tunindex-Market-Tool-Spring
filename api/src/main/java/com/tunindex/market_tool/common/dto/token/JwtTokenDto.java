package com.tunindex.market_tool.common.dto.token;

import com.tunindex.market_tool.common.entities.UnifiedToken;
import com.tunindex.market_tool.common.entities.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JwtTokenDto {
    private Integer id;
    private String token;
    private String ipHash;
    private String userAgentHash;
    private boolean revoked;
    private boolean expired;
    private User user;
    
    public boolean isExpired() {
        return expired;
    }
    
    public boolean isRevoked() {
        return revoked;
    }

    public static JwtTokenDto fromEntity(UnifiedToken entity) {
        if (entity == null) return null;
        return JwtTokenDto.builder()
                .id(entity.getId() != null ? entity.getId().intValue() : null)
                .token(entity.getToken())
                .ipHash(entity.getIpHash())
                .userAgentHash(entity.getUserAgentHash())
                .revoked(entity.isRevoked())
                .expired(entity.isExpired())
                .user(entity.getUser())
                .build();
    }
} 