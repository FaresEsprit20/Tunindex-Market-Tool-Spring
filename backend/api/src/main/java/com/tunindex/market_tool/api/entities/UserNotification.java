package com.tunindex.market_tool.api.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A delivered notification. Persisted rather than only pushed, so the
 * notification centre survives a reload and a user who was offline when a
 * rule fired still sees it.
 */
@Entity
@Table(name = "user_notifications", indexes = {
        @Index(name = "idx_notifications_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_notifications_unread", columnList = "user_id, read_flag")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 500)
    private String body;

    /** ALERT | SYSTEM — lets the UI separate rule hits from housekeeping. */
    @Column(nullable = false, length = 24)
    @Builder.Default
    private String category = "ALERT";

    /** POSITIVE | NEGATIVE | NEUTRAL — drives the accent colour in the UI. */
    @Column(length = 16)
    @Builder.Default
    private String tone = "NEUTRAL";

    /** Symbol this concerns, so the UI can deep-link to its page. */
    @Column(length = 20)
    private String symbol;

    // "read" is reserved in some SQL dialects; the column is named explicitly.
    @Column(name = "read_flag", nullable = false)
    @Builder.Default
    private boolean read = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
