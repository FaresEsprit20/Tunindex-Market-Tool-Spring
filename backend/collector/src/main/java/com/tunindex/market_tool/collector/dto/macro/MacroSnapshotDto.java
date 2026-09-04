package com.tunindex.market_tool.collector.dto.macro;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The macro backdrop a Tunisian equity investor is trading against.
 *
 * <p>Two groups, because they behave differently and should not be read as
 * one series: policy and money-market rates are published continuously by the
 * central bank, while inflation and growth are annual figures from the World
 * Bank and lag by up to a year. Mixing them into a single "indicators" list
 * would imply a currency they do not share.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MacroSnapshotDto {

    /** Central-bank rates, most policy-relevant first. */
    private List<MacroIndicatorDto> rates;

    /** Annual national accounts figures — inflation, growth. */
    private List<MacroIndicatorDto> economy;

    /** When we last successfully read the publishers. */
    private LocalDateTime fetchedAt;

    /**
     * Sources we could not reach on this attempt. Present so the UI can say
     * a section is missing rather than silently showing a shorter list.
     */
    private List<String> unavailable;
}
