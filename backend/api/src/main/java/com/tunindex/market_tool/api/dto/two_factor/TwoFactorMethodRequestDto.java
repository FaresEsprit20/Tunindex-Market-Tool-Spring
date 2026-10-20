package com.tunindex.market_tool.api.dto.two_factor;

import com.tunindex.market_tool.common.entities.enums.TwoFactorMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Which method the user wants their second factor delivered by. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TwoFactorMethodRequestDto {
    private TwoFactorMethod method;
}
