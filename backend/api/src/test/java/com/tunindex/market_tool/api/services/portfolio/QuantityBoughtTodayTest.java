package com.tunindex.market_tool.api.services.portfolio;

import com.tunindex.market_tool.api.entities.PortfolioTransaction;
import com.tunindex.market_tool.api.entities.enums.TransactionSide;
import com.tunindex.market_tool.api.repository.PortfolioAccountRepository;
import com.tunindex.market_tool.api.repository.PortfolioPositionRepository;
import com.tunindex.market_tool.api.repository.PortfolioTransactionRepository;
import com.tunindex.market_tool.api.repository.UserRepository;
import com.tunindex.market_tool.api.services.notification.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Covers the query that decides which shares earn today's change.
 *
 * <p>{@link DayChangeCalculator} is tested on its own, but a correct
 * calculation fed the wrong quantity is still wrong. This is the wiring
 * between them: reading the account's transactions and working out how many
 * shares of each symbol were bought today.
 *
 * <p>It exists because the branch could not be observed in practice - the live
 * account's only position predates today, so the exclusion path never ran, and
 * confirming it any other way would have meant writing a trade into a real
 * portfolio.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("quantityBoughtToday")
class QuantityBoughtTodayTest {

    @Mock private PortfolioAccountRepository portfolioAccountRepository;
    @Mock private PortfolioPositionRepository portfolioPositionRepository;
    @Mock private PortfolioTransactionRepository portfolioTransactionRepository;
    @Mock private UserRepository userRepository;
    @Mock private WebClient.Builder webClientBuilder;
    @Mock private NotificationService notificationService;

    private PortfolioServiceImpl service() {
        return new PortfolioServiceImpl(
                portfolioAccountRepository,
                portfolioPositionRepository,
                portfolioTransactionRepository,
                userRepository,
                webClientBuilder,
                notificationService,
                new DayChangeCalculator());
    }

    /**
     * A time on today's date.
     *
     * <p>Deliberately anchored to the calendar day rather than expressed as
     * an offset from now. Written as {@code now().minusHours(3)} these cases
     * passed during the day and failed after midnight, because the offset
     * silently crossed into yesterday - the method then behaved correctly and
     * the test reported a bug that did not exist.
     */
    private LocalDateTime todayAt(int hour, int minute) {
        return LocalDate.now().atStartOfDay().plusHours(hour).plusMinutes(minute);
    }

    private PortfolioTransaction transaction(String symbol, TransactionSide side,
                                             String quantity, LocalDateTime executedAt) {
        PortfolioTransaction transaction = new PortfolioTransaction();
        transaction.setSymbol(symbol);
        transaction.setSide(side);
        transaction.setQuantity(new BigDecimal(quantity));
        transaction.setExecutedAt(executedAt);
        return transaction;
    }

    /** The repository returns newest first, which the method relies on. */
    private void given(PortfolioTransaction... transactions) {
        when(portfolioTransactionRepository.findByAccountIdOrderByExecutedAtDesc(anyLong()))
                .thenReturn(List.of(transactions));
    }

    @Test
    @DisplayName("counts a purchase made today")
    void countsTodaysBuy() {
        given(transaction("BT", TransactionSide.BUY, "50", todayAt(10, 30)));

        Map<String, BigDecimal> bought = service().quantityBoughtToday(1L);

        assertThat(bought).containsKey("BT");
        assertThat(bought.get("BT")).isEqualByComparingTo("50");
    }

    @Test
    @DisplayName("ignores a purchase made before today")
    void ignoresOlderBuys() {
        // The live account's situation: bought days ago, so every share is
        // eligible for today's move.
        given(transaction("BT", TransactionSide.BUY, "100", LocalDateTime.now().minusDays(5)));

        assertThat(service().quantityBoughtToday(1L)).isEmpty();
    }

    @Test
    @DisplayName("sums several purchases of the same symbol today")
    void sumsRepeatBuys() {
        given(transaction("BT", TransactionSide.BUY, "30", todayAt(10, 30)),
              transaction("BT", TransactionSide.BUY, "20", todayAt(8, 45)));

        assertThat(service().quantityBoughtToday(1L).get("BT")).isEqualByComparingTo("50");
    }

    @Test
    @DisplayName("ignores sales, which the position quantity already reflects")
    void ignoresSells() {
        // Selling reduces the holding, and the smaller quantity already
        // accounts for it. Subtracting the sale here too would double-count.
        given(transaction("BT", TransactionSide.SELL, "40", todayAt(10, 30)),
              transaction("BT", TransactionSide.BUY, "10", todayAt(9, 15)));

        assertThat(service().quantityBoughtToday(1L).get("BT")).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("keeps symbols separate")
    void separatesSymbols() {
        given(transaction("BT", TransactionSide.BUY, "10", todayAt(10, 30)),
              transaction("BIAT", TransactionSide.BUY, "5", todayAt(9, 15)));

        Map<String, BigDecimal> bought = service().quantityBoughtToday(1L);
        assertThat(bought.get("BT")).isEqualByComparingTo("10");
        assertThat(bought.get("BIAT")).isEqualByComparingTo("5");
    }

    @Test
    @DisplayName("stops at the first older row rather than scanning all history")
    void stopsAtFirstOlderRow() {
        // Newest first, so once a row predates today the rest do too. An
        // account with years of trades should not be walked end to end on
        // every portfolio load.
        given(transaction("BT", TransactionSide.BUY, "10", todayAt(11, 0)),
              transaction("OLD", TransactionSide.BUY, "99", LocalDateTime.now().minusDays(2)),
              transaction("OLDER", TransactionSide.BUY, "99", LocalDateTime.now().minusDays(9)));

        Map<String, BigDecimal> bought = service().quantityBoughtToday(1L);
        assertThat(bought).containsOnlyKeys("BT");
    }

    @Test
    @DisplayName("counts a trade from just after midnight as today")
    void countsEarlyMorningTrade() {
        LocalDateTime justAfterMidnight = LocalDate.now().atStartOfDay().plusMinutes(1);
        given(transaction("BT", TransactionSide.BUY, "25", justAfterMidnight));

        assertThat(service().quantityBoughtToday(1L).get("BT")).isEqualByComparingTo("25");
    }

    @Test
    @DisplayName("end to end: today's shares are excluded from the day's move")
    void wiringExcludesTodaysPurchase() {
        // The case that could not be observed live. Held 100 from last week,
        // bought 50 this morning; the move from 8.00 to 8.30 belongs to the
        // 100 only.
        given(transaction("BT", TransactionSide.BUY, "50", todayAt(10, 30)),
              transaction("BT", TransactionSide.BUY, "100", LocalDateTime.now().minusDays(6)));

        BigDecimal boughtToday = service().quantityBoughtToday(1L).get("BT");
        DayChangeCalculator.DayChange change = new DayChangeCalculator()
                .calculate(new BigDecimal("150"), boughtToday, new BigDecimal("8.00"), new BigDecimal("8.30"));

        assertThat(change.eligibleQuantity()).isEqualByComparingTo("100");
        assertThat(change.value()).isEqualByComparingTo("30.000");
    }
}
