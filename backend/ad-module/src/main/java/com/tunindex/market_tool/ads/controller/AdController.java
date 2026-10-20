package com.tunindex.market_tool.ads.controller;

import com.tunindex.market_tool.ads.dto.AdEventRequestDto;
import com.tunindex.market_tool.ads.dto.AdRevenueSummaryDto;
import com.tunindex.market_tool.ads.entities.AdEvent;
import com.tunindex.market_tool.ads.entities.Advertisement;
import com.tunindex.market_tool.ads.entities.enums.AdPlacement;
import com.tunindex.market_tool.ads.entities.enums.AdSource;
import com.tunindex.market_tool.ads.entities.enums.AdStatus;
import com.tunindex.market_tool.ads.entities.enums.AdType;
import com.tunindex.market_tool.ads.entities.enums.PricingModel;
import com.tunindex.market_tool.ads.repository.AdEventRepository;
import com.tunindex.market_tool.ads.service.AdService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ad inventory, delivery and reporting.
 *
 * <p>Split into three concerns by path: {@code /ads} manages inventory,
 * {@code /ads/serve} answers the runtime question of what to show, and
 * {@code /ads/{id}/events} records what happened. They are separated because
 * their callers differ - an admin screen, the site itself, and the site again
 * reporting back.
 */
@RestController
@RequestMapping("/api/v1/ads")
@RequiredArgsConstructor
@Tag(name = "Ads", description = "Ad inventory, delivery and revenue")
public class AdController {

    private final AdService adService;
    private final AdEventRepository adEventRepository;

    // ── Inventory ─────────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "Every ad, whatever its status")
    public List<Advertisement> findAll() {
        return adService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Advertisement> findById(@PathVariable Long id) {
        return adService.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Create an ad", description = "Starts as DRAFT unless a status is given")
    public ResponseEntity<Advertisement> create(@RequestBody Advertisement ad) {
        return ResponseEntity.ok(adService.create(ad));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Advertisement> update(@PathVariable Long id, @RequestBody Advertisement ad) {
        return adService.update(id, ad)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Activate, pause, archive")
    public ResponseEntity<Advertisement> setStatus(@PathVariable Long id, @RequestParam AdStatus status) {
        return adService.setStatus(id, status)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return adService.delete(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    // ── Delivery ──────────────────────────────────────────────────────────

    /**
     * What to show in a slot.
     *
     * <p>204 when there is nothing eligible, so the caller collapses the slot
     * rather than reserving space for an ad that is not coming.
     */
    @GetMapping("/serve/{placement}")
    @Operation(summary = "The ad to show in a placement, or 204 if none applies")
    public ResponseEntity<Advertisement> serve(@PathVariable AdPlacement placement) {
        return adService.selectForPlacement(placement)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    // ── Events ────────────────────────────────────────────────────────────

    @PostMapping("/{id}/events")
    @Operation(summary = "Record an impression, view, skip, click or conversion")
    public ResponseEntity<AdEvent> recordEvent(@PathVariable Long id,
                                               @RequestBody AdEventRequestDto request) {
        return adService.recordEvent(id, request.getEventType(), request.getUserId(),
                        request.getWatchedPercent(), request.getDeviceCategory())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ── Reporting ─────────────────────────────────────────────────────────

    @GetMapping("/{id}/summary")
    @Operation(summary = "Totals and rates for one ad")
    public ResponseEntity<AdRevenueSummaryDto> summary(@PathVariable Long id) {
        return adService.findById(id).map(ad -> {
            Map<AdEvent.EventType, Long> counts = countsFor(id);
            long impressions = counts.getOrDefault(AdEvent.EventType.IMPRESSION, 0L);
            long clicks = counts.getOrDefault(AdEvent.EventType.CLICK, 0L);

            return ResponseEntity.ok(AdRevenueSummaryDto.builder()
                    .advertisementId(id)
                    .name(ad.getName())
                    .pricingModel(ad.getPricingModel() == null ? null : ad.getPricingModel().name())
                    .revenue(adService.revenueFor(id))
                    .currency(ad.getCurrency())
                    .impressions(impressions)
                    .completedViews(counts.getOrDefault(AdEvent.EventType.COMPLETED_VIEW, 0L))
                    .clicks(clicks)
                    .conversions(counts.getOrDefault(AdEvent.EventType.CONVERSION, 0L))
                    // Left null with no impressions: a rate with no
                    // denominator is not zero, it is unknown, and 0% would
                    // read as a failing ad rather than an unserved one.
                    .clickThroughRatePct(impressions == 0 ? null
                            : BigDecimal.valueOf(clicks)
                                    .divide(BigDecimal.valueOf(impressions), 6, RoundingMode.HALF_UP)
                                    .multiply(BigDecimal.valueOf(100))
                                    .setScale(2, RoundingMode.HALF_UP))
                    .build());
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/revenue")
    @Operation(summary = "Total revenue over a period")
    public Map<String, Object> revenue(@RequestParam(defaultValue = "30") int days) {
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minusDays(days);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", from);
        result.put("to", to);
        result.put("revenue", adService.revenueBetween(from, to));
        return result;
    }

    // ── Metadata ──────────────────────────────────────────────────────────

    /**
     * The available types, sources, models, placements and statuses.
     *
     * <p>Served so an admin screen builds its dropdowns from what the backend
     * actually accepts. A hard-coded client list drifts the first time an enum
     * gains a value, and the symptom is a save that fails for no visible
     * reason.
     */
    @GetMapping("/options")
    @Operation(summary = "Enum values for building the admin form")
    public Map<String, Object> options() {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("types", describe(AdType.values(), t ->
                Map.of("value", t.name(), "label", t.getDisplayName(),
                        "video", t.isVideo(), "defaultSkippable", t.isDefaultSkippable())));
        options.put("sources", describe(AdSource.values(), s ->
                Map.of("value", s.name(), "label", s.getDisplayName(),
                        "networkManaged", s.isNetworkManaged(), "earnsRevenue", s.earnsRevenue())));
        options.put("pricingModels", describe(PricingModel.values(), m ->
                Map.of("value", m.name(), "label", m.getDisplayName(),
                        "billableEvent", m.getBillableEvent().name(), "eventDriven", m.isEventDriven())));
        options.put("placements", describe(AdPlacement.values(), p ->
                Map.of("value", p.name(), "label", p.getDisplayName())));
        options.put("statuses", describe(AdStatus.values(), s ->
                Map.of("value", s.name(), "label", s.getDisplayName(), "servable", s.isServable())));
        return options;
    }

    private <T> List<Map<String, Object>> describe(T[] values, java.util.function.Function<T, Map<String, Object>> mapper) {
        List<Map<String, Object>> described = new ArrayList<>();
        for (T value : values) {
            described.add(mapper.apply(value));
        }
        return described;
    }

    private Map<AdEvent.EventType, Long> countsFor(Long adId) {
        Map<AdEvent.EventType, Long> counts = new LinkedHashMap<>();
        for (Object[] row : adEventRepository.countsByTypeFor(adId)) {
            counts.put((AdEvent.EventType) row[0], (Long) row[1]);
        }
        return counts;
    }
}
