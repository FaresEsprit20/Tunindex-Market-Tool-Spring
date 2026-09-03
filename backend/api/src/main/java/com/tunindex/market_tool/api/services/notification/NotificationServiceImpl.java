package com.tunindex.market_tool.api.services.notification;

import com.tunindex.market_tool.api.dto.notification.NotificationDto;
import com.tunindex.market_tool.api.entities.User;
import com.tunindex.market_tool.api.entities.UserNotification;
import com.tunindex.market_tool.api.repository.UserNotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    /** Streams stay open for the session; the client reconnects on its own. */
    // Five minutes, not thirty: each open stream pins an async servlet
    // request, and the client reconnects on its own, so a long timeout only
    // makes abandoned tabs expensive.
    private static final long STREAM_TIMEOUT_MS = 5 * 60 * 1000L;

    private final UserNotificationRepository repository;

    /**
     * Live streams per user. A user can have several (two tabs), hence a set.
     * In-memory by design: this is a single-instance deployment, and a
     * dropped stream costs nothing because every notification is also
     * persisted and re-read on reconnect.
     */
    private final Map<Integer, Set<SseEmitter>> streams = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public NotificationDto publish(User user, String title, String body, String category, String tone, String symbol) {
        UserNotification saved = repository.save(UserNotification.builder()
                .user(user)
                .title(title)
                .body(body)
                .category(category != null ? category : "ALERT")
                .tone(tone != null ? tone : "NEUTRAL")
                .symbol(symbol)
                .build());

        NotificationDto dto = NotificationDto.fromEntity(saved);
        push(user.getId(), dto);
        return dto;
    }

    private void push(Integer userId, NotificationDto dto) {
        Set<SseEmitter> userStreams = streams.get(userId);
        if (userStreams == null || userStreams.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : userStreams) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(dto));
            } catch (IOException | IllegalStateException e) {
                // The client went away mid-send. Drop the stream rather than
                // retrying — the notification is already persisted, so it
                // will be there when they reconnect.
                userStreams.remove(emitter);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationDto> list(Integer userId, int limit) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId, Limit.of(limit))
                .stream()
                .map(NotificationDto::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long unreadCount(Integer userId) {
        return repository.countByUserIdAndReadFalse(userId);
    }

    @Override
    @Transactional
    public void markRead(Integer userId, Long notificationId) {
        repository.findByIdAndUserId(notificationId, userId).ifPresent(notification -> {
            notification.setRead(true);
            repository.save(notification);
        });
    }

    @Override
    @Transactional
    public int markAllRead(Integer userId) {
        return repository.markAllRead(userId);
    }

    @Override
    public SseEmitter stream(Integer userId) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        streams.computeIfAbsent(userId, key -> new CopyOnWriteArraySet<>()).add(emitter);

        Runnable cleanup = () -> {
            Set<SseEmitter> userStreams = streams.get(userId);
            if (userStreams != null) {
                userStreams.remove(emitter);
                if (userStreams.isEmpty()) {
                    streams.remove(userId);
                }
            }
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        try {
            // An immediate event so the browser treats the stream as open
            // rather than sitting on a pending response.
            emitter.send(SseEmitter.event().name("connected").data(Map.of("ok", true)));
        } catch (IOException e) {
            cleanup.run();
        }

        return emitter;
    }
}
