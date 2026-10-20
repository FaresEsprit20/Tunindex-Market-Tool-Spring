package com.tunindex.market_tool.api.services.notification;

import com.tunindex.market_tool.api.dto.notification.NotificationDto;
import com.tunindex.market_tool.api.entities.User;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface NotificationService {

    /** Persists a notification and pushes it to any live stream that user has open. */
    NotificationDto publish(User user, String title, String body, String category, String tone, String symbol);

    List<NotificationDto> list(Integer userId, int limit);

    long unreadCount(Integer userId);

    void markRead(Integer userId, Long notificationId);

    int markAllRead(Integer userId);

    /** Opens a server-sent event stream that receives this user's notifications live. */
    SseEmitter stream(Integer userId);
}
