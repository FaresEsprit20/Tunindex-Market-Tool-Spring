package com.tunindex.market_tool.api.dto.two_factor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TotpStatusResponseDto {
    private boolean enabled;
}
