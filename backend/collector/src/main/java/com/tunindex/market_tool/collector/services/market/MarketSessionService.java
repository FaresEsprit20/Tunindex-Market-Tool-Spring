package com.tunindex.market_tool.collector.services.market;

import com.tunindex.market_tool.collector.dto.market.MarketSessionDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Reports where the BVMT trading day currently stands.
 *
 * <p>Driven entirely by the configured timetable in Africa/Tunis time —
 * the exchange publishes no realtime session feed we consume, so this
 * computes the session from the clock rather than pretending to observe it.
 * Every response carries {@code scheduleBased=true} and the UI says so,
 * because a schedule cannot know about an exchange holiday or a halt.
 *
 * <p>Times are properties rather than constants so the schedule can be
 * corrected without a code change.
 */
@Slf4j
@Service
public class MarketSessionService {

    private static final ZoneId TUNIS = ZoneId.of("Africa/Tunis");

    @Value("${market-tool.session.pre-open:09:00}")
    private String preOpenTime;

    @Value("${market-tool.session.open:10:00}")
    private String openTime;

    @Value("${market-tool.session.pre-close:14:00}")
    private String preCloseTime;

    @Value("${market-tool.session.close:14:10}")
    private String closeTime;

    public MarketSessionDto currentSession() {
        LocalDateTime now = LocalDateTime.now(TUNIS);
        LocalTime time = now.toLocalTime();
        DayOfWeek day = now.getDayOfWeek();

        LocalTime preOpen = LocalTime.parse(preOpenTime);
        LocalTime open = LocalTime.parse(openTime);
        LocalTime preClose = LocalTime.parse(preCloseTime);
        LocalTime close = LocalTime.parse(closeTime);

        boolean weekend = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;

        if (weekend) {
            LocalDateTime nextOpen = nextWeekdayAt(now, preOpen);
            return build("WEEKEND", "Weekend — market closed", "Pre-opening", nextOpen, now);
        }

        if (time.isBefore(preOpen)) {
            return build("CLOSED", "Closed", "Pre-opening", now.with(preOpen), now);
        }
        if (time.isBefore(open)) {
            return build("PRE_OPEN", "Pre-opening auction", "Opens", now.with(open), now);
        }
        if (time.isBefore(preClose)) {
            return build("OPEN", "Continuous trading", "Pre-closing", now.with(preClose), now);
        }
        if (time.isBefore(close)) {
            return build("PRE_CLOSE", "Pre-closing auction", "Closes", now.with(close), now);
        }

        // After the close: next session is the next weekday's pre-opening.
        return build("CLOSED", "Closed", "Pre-opening", nextWeekdayAt(now, preOpen), now);
    }

    /** The next Monday-to-Friday day at the given time, strictly after now. */
    private LocalDateTime nextWeekdayAt(LocalDateTime now, LocalTime at) {
        LocalDate date = now.toLocalDate();
        do {
            date = date.plusDays(1);
        } while (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY);
        return LocalDateTime.of(date, at);
    }

    private MarketSessionDto build(String state, String label, String nextLabel,
                                   LocalDateTime nextAt, LocalDateTime now) {
        long seconds = Math.max(0, Duration.between(now, nextAt).getSeconds());
        return MarketSessionDto.builder()
                .state(state)
                .label(label)
                .nextTransitionLabel(nextLabel)
                .nextTransitionAt(nextAt)
                .secondsUntilTransition(seconds)
                .tunisTime(now)
                .timezone(TUNIS.getId())
                .scheduleBased(true)
                .build();
    }
}
