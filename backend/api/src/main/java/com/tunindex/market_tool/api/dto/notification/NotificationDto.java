package com.tunindex.market_tool.api.dto.notification;

import com.tunindex.market_tool.api.entities.UserNotification;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDto {

    private Long id;
    private String title;
    private String body;
    private String category;
    private String tone;
    private String symbol;
    private boolean read;
    private LocalDateTime createdAt;

    public static NotificationDto fromEntity(UserNotification entity) {
        return NotificationDto.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .body(entity.getBody())
                .category(entity.getCategory())
                .tone(entity.getTone())
                .symbol(entity.getSymbol())
                .read(entity.isRead())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
