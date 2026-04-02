package com.tunindex.market_tool.domain.entities;

import jakarta.persistence.Embeddable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VolumeData {

    private Long volume;
    private Long avgVolume3m;
}