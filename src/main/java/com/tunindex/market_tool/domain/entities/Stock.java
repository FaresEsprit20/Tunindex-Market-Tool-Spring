package com.tunindex.market_tool.domain.entities;

import com.tunindex.market_tool.domain.entities.enums.OwnershipType;
import com.tunindex.market_tool.domain.entities.enums.SectorType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "stocks",
        uniqueConstraints = @UniqueConstraint(columnNames = {"symbol", "exchange"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Basic Information
    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false)
    private String name;

    private String url;
    private String isin;

    @Column(nullable = false)
    private String exchange;

    private String exchangeFullName;
    private String market;
    private String currency;

    @Enumerated(EnumType.STRING)
    private SectorType sector;

    private String industry;

    // Ownership Type (Private vs Government)
    @Enumerated(EnumType.STRING)
    @Column(name = "ownership_type")
    private OwnershipType ownershipType;

    // Embedded Data
    @Embedded
    private PriceData priceData;

    @Embedded
    private VolumeData volumeData;

    @Embedded
    private FundamentalData fundamentalData;

    @Embedded
    private com.tunindex.market_tool.entity.embedded.RatiosData ratiosData;

    @Embedded
    private com.tunindex.market_tool.entity.embedded.TechnicalData technicalData;

    @Embedded
    private com.tunindex.market_tool.entity.embedded.AnalystData analystData;

    @Embedded
    private CalculatedValues calculatedValues;

    // Timestamps
    @Column(name = "last_update", nullable = false)
    private LocalDateTime lastUpdate;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (lastUpdate == null) {
            lastUpdate = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}