package com.tunindex.market_tool.ads.service;

import com.tunindex.market_tool.ads.entities.AdEvent;
import com.tunindex.market_tool.ads.entities.Advertisement;
import com.tunindex.market_tool.ads.entities.enums.AdStatus;
import com.tunindex.market_tool.ads.entities.enums.AdPlacement;
import com.tunindex.market_tool.ads.repository.AdEventRepository;
import com.tunindex.market_tool.ads.repository.AdvertisementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Choosing what to show, and accounting for what happened.
 *
 * <p>Two responsibilities that look separate but are not: an ad stops being
 * servable partly because of what its own events have already earned, so
 * selection has to read the same numbers recording writes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdService {

    private final AdvertisementRepository advertisementRepository;
    private final AdEventRepository adEventRepository;
    private final AdRevenueCalculator revenueCalculator;

    // ── Delivery ──────────────────────────────────────────────────────────

    /**
     * The ad to show in a slot, if any.
     *
     * <p>Highest priority wins among those actually eligible. Eligibility is
     * re-checked here rather than trusted from the query, because budget
     * exhaustion depends on accrued revenue and changes between runs - an ad
     * can become unservable without anything about its row being edited.
     *
     * <p>Returns empty rather than a filler ad. A slot with nothing to show
     * should collapse; rendering a placeholder that earns nothing costs the
     * reader attention and pays us nothing for it.
     */
    @Transactional(readOnly = true)
    public Optional<Advertisement> selectForPlacement(AdPlacement placement) {
        LocalDateTime now = LocalDateTime.now();
        return advertisementRepository.findCandidates(placement, AdStatus.ACTIVE, now).stream()
                .filter(ad -> ad.isServableNow(now))
                .findFirst();
    }

    @Transactional(readOnly = true)
    public List<Advertisement> findAll() {
        return advertisementRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Advertisement> findById(Long id) {
        return advertisementRepository.findById(id);
    }

    // ── Inventory ─────────────────────────────────────────────────────────

    @Transactional
    public Advertisement create(Advertisement ad) {
        LocalDateTime now = LocalDateTime.now();
        ad.setCreatedAt(now);
        ad.setUpdatedAt(now);
        if (ad.getStatus() == null) {
            // New inventory starts unservable on purpose: an ad with no
            // creative or rate yet should not be able to reach a reader
            // because someone forgot a field.
            ad.setStatus(AdStatus.DRAFT);
        }
        if (ad.getRevenueAccrued() == null) {
            ad.setRevenueAccrued(BigDecimal.ZERO);
        }
        return advertisementRepository.save(ad);
    }

    @Transactional
    public Optional<Advertisement> update(Long id, Advertisement changes) {
        return advertisementRepository.findById(id).map(existing -> {
            changes.setId(existing.getId());
            changes.setCreatedAt(existing.getCreatedAt());
            changes.setUpdatedAt(LocalDateTime.now());
            // Earnings belong to the events that produced them, not to the
            // edit form. Letting an update carry this field would let a typo
            // rewrite a campaign's revenue history.
            changes.setRevenueAccrued(existing.getRevenueAccrued());
            return advertisementRepository.save(changes);
        });
    }

    @Transactional
    public Optional<Advertisement> setStatus(Long id, AdStatus status) {
        return advertisementRepository.findById(id).map(ad -> {
            ad.setStatus(status);
            ad.setUpdatedAt(LocalDateTime.now());
            return advertisementRepository.save(ad);
        });
    }

    @Transactional
    public boolean delete(Long id) {
        if (!advertisementRepository.existsById(id)) {
            return false;
        }
        advertisementRepository.deleteById(id);
        return true;
    }

    // ── Accounting ────────────────────────────────────────────────────────

    /**
     * Records something that happened to an ad, and what it earned.
     *
     * <p>The rate is resolved and stored now rather than referenced later, so
     * renegotiating a rate does not retroactively change what past events were
     * worth.
     */
    @Transactional
    public Optional<AdEvent> recordEvent(Long adId, AdEvent.EventType type,
                                         Long userId, Integer watchedPercent,
                                         String deviceCategory) {

        return advertisementRepository.findById(adId).map(ad -> {
            BigDecimal revenue = revenueCalculator.revenueFor(ad, type);

            AdEvent event = adEventRepository.save(AdEvent.builder()
                    .advertisementId(adId)
                    .eventType(type)
                    .occurredAt(LocalDateTime.now())
                    .userId(userId)
                    .watchedPercent(watchedPercent)
                    .pricingModel(ad.getPricingModel())
                    .revenue(revenue)
                    .currency(ad.getCurrency())
                    .deviceCategory(deviceCategory)
                    .build());

            if (revenue.signum() > 0) {
                accrue(ad, revenue);
            }
            return event;
        });
    }

    /**
     * Adds to the running total and stops the ad if it has hit its cap.
     *
     * <p>Checked on every accrual rather than on a schedule: a campaign that
     * keeps serving past its budget is delivering work nobody agreed to pay
     * for, and the gap would be however long until the next sweep.
     */
    private void accrue(Advertisement ad, BigDecimal revenue) {
        BigDecimal accrued = ad.getRevenueAccrued() == null
                ? BigDecimal.ZERO : ad.getRevenueAccrued();
        ad.setRevenueAccrued(accrued.add(revenue));

        if (ad.hasExhaustedBudget() && ad.getStatus() == AdStatus.ACTIVE) {
            ad.setStatus(AdStatus.BUDGET_EXHAUSTED);
            log.info("Ad {} reached its budget cap of {} and stopped serving",
                    ad.getId(), ad.getBudgetCap());
        }
        ad.setUpdatedAt(LocalDateTime.now());
        advertisementRepository.save(ad);
    }

    @Transactional(readOnly = true)
    public BigDecimal revenueFor(Long adId) {
        return adEventRepository.totalRevenueFor(adId);
    }

    @Transactional(readOnly = true)
    public BigDecimal revenueBetween(LocalDateTime from, LocalDateTime to) {
        return adEventRepository.totalRevenueBetween(from, to);
    }
}
