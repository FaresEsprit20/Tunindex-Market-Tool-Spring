package com.tunindex.market_tool.api.dto.macro;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The macro backdrop for the Tunisian market.
 *
 * <p>Rates and economy stay in separate lists because they are not the same
 * kind of number: the central bank publishes rates continuously, while
 * inflation and growth are annual and can lag by a year.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MacroSnapshotResponseDto {

    /** Central-bank rates, most policy-relevant first. */
    private List<MacroIndicatorResponseDto> rates;

    /** Annual national-accounts figures. */
    private List<MacroIndicatorResponseDto> economy;

    private LocalDateTime fetchedAt;

    /** Publishers we could not reach, so the UI can say so explicitly. */
    private List<String> unavailable;
}
