package com.tunindex.market_tool.domain.entities.embedded;

import jakarta.persistence.Embeddable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.math.BigDecimal;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TechnicalData {

    private BigDecimal beta;
    private String technicalSummary1d;
    private String technicalSummary1w;
    private String technicalSummary1m;
}