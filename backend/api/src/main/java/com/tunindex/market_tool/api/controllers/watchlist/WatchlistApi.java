package com.tunindex.market_tool.api.controllers.watchlist;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.tunindex.market_tool.common.utils.constants.Constants.APP_ROOT;

@Tag(name = "Watchlist", description = "The authenticated user's saved list of stock symbols")
public interface WatchlistApi {

    @GetMapping(value = APP_ROOT + "/watchlist", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List the current user's watchlist symbols")
    List<String> list(Authentication authentication);

    @PostMapping(APP_ROOT + "/watchlist/{symbol}")
    @Operation(summary = "Add a symbol to the current user's watchlist")
    ResponseEntity<Void> add(@PathVariable("symbol") String symbol, Authentication authentication);

    @DeleteMapping(APP_ROOT + "/watchlist/{symbol}")
    @Operation(summary = "Remove a symbol from the current user's watchlist")
    ResponseEntity<Void> remove(@PathVariable("symbol") String symbol, Authentication authentication);
}
