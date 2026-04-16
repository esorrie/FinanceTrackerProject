package com.project.finance.controller;

import com.project.finance.dto.MarketDataImportResponse;
import com.project.finance.service.MarketDataImportService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/market-data")
public class MarketDataController {

    private final MarketDataImportService marketDataImportService;

    public MarketDataController(MarketDataImportService marketDataImportService) {
        this.marketDataImportService = marketDataImportService;
    }

    @PostMapping("/import")
    public MarketDataImportResponse importQuotes(@RequestParam List<String> symbols) {
        try {
            return marketDataImportService.importQuotes(symbols);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        }
    }
}
