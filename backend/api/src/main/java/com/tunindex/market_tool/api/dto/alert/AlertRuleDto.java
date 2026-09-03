package com.tunindex.market_tool.api.dto.alert;

import com.tunindex.market_tool.api.entities.AlertRule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertRuleDto {

    private Long id;
    private String symbol;
    private String type;
    private String typeDescription;
    private BigDecimal threshold;
    private boolean enabled;
    private LocalDateTime lastTriggeredAt;
    private LocalDateTime createdAt;

    public static AlertRuleDto fromEntity(AlertRule rule) {
        return AlertRuleDto.builder()
                .id(rule.getId())
                .symbol(rule.getSymbol())
                .type(rule.getType().name())
                .typeDescription(rule.getType().getDescription())
                .threshold(rule.getThreshold())
                .enabled(rule.isEnabled())
                .lastTriggeredAt(rule.getLastTriggeredAt())
                .createdAt(rule.getCreatedAt())
                .build();
    }
}
