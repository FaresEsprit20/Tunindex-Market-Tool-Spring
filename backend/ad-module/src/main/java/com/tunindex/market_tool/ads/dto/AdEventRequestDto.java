package com.tunindex.market_tool.ads.dto;

import com.tunindex.market_tool.ads.entities.AdEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Something the client observed happening to an ad. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdEventRequestDto {

    private AdEvent.EventType eventType;

    /** Null for a signed-out visitor, whose impression still counts. */
    private Long userId;

    /** How much of a video played, for completion-based pricing. */
    private Integer watchedPercent;

    private String deviceCategory;
}
