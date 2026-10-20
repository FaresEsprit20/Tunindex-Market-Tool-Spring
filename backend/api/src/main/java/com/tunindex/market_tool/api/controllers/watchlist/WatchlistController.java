package com.tunindex.market_tool.api.controllers.watchlist;

import com.tunindex.market_tool.api.entities.User;
import com.tunindex.market_tool.api.entities.WatchlistItem;
import com.tunindex.market_tool.api.repository.UserRepository;
import com.tunindex.market_tool.api.repository.WatchlistItemRepository;
import com.tunindex.market_tool.common.exception.ErrorCodes;
import com.tunindex.market_tool.common.exception.InvalidEntityException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class WatchlistController implements WatchlistApi {

    private final WatchlistItemRepository watchlistItemRepository;
    private final UserRepository userRepository;

    @Override
    public List<String> list(Authentication authentication) {
        User user = resolveUser(authentication);
        return watchlistItemRepository.findByUserIdOrderByAddedAtAsc(user.getId())
                .stream()
                .map(WatchlistItem::getSymbol)
                .toList();
    }

    @Override
    public ResponseEntity<Void> add(String symbol, Authentication authentication) {
        User user = resolveUser(authentication);
        String normalized = symbol.trim().toUpperCase();

        if (!watchlistItemRepository.existsByUserIdAndSymbol(user.getId(), normalized)) {
            watchlistItemRepository.save(WatchlistItem.builder().user(user).symbol(normalized).build());
            log.info("Added {} to watchlist for user {}", normalized, user.getEmail());
        }
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> remove(String symbol, Authentication authentication) {
        User user = resolveUser(authentication);
        watchlistItemRepository.deleteByUserIdAndSymbol(user.getId(), symbol.trim().toUpperCase());
        log.info("Removed {} from watchlist for user {}", symbol, user.getEmail());
        return ResponseEntity.ok().build();
    }

    private User resolveUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new InvalidEntityException("Not authenticated", ErrorCodes.USER_NOT_AUTHENTICATED, Collections.emptyList());
        }
        String email = authentication.getName();
        return userRepository.findUserByEmail(email)
                .orElseThrow(() -> new InvalidEntityException(
                        "User not found", ErrorCodes.USER_NOT_FOUND, Collections.singletonList("email: " + email)));
    }
}
