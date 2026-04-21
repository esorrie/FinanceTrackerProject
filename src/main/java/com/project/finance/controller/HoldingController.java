package com.project.finance.controller;

import com.project.finance.dto.HoldingCreateRequest;
import com.project.finance.dto.HoldingCreateResponse;
import com.project.finance.dto.HoldingHistoryResponse;
import com.project.finance.dto.HoldingUnitsUpdateRequest;
import com.project.finance.dto.HoldingUnitsUpdateResponse;
import com.project.finance.dto.HoldingsInCurrencyResponse;
import com.project.finance.dto.PortfolioHistoryResponse;
import com.project.finance.dto.PortfolioPerformanceResponse;
import com.project.finance.dto.UserCurrencyUpdateResponse;
import com.project.finance.service.HoldingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/holdings")
public class HoldingController {

    private final HoldingService holdingService;

    public HoldingController(HoldingService holdingService) {
        this.holdingService = holdingService;
    }

    @PostMapping
    public HoldingCreateResponse createHolding(@RequestBody HoldingCreateRequest request) {
        try {
            return holdingService.createHolding(request);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        }
    }

    @PatchMapping("/units")
    public HoldingUnitsUpdateResponse updateHoldingUnits(@RequestBody HoldingUnitsUpdateRequest request) {
        try {
            return holdingService.updateHoldingUnits(request);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        }
    }

    @GetMapping
    public HoldingsInCurrencyResponse getHoldings(
            @RequestParam String username,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) Integer portfolioId
    ) {
        try {
            return holdingService.getHoldingsInCurrency(username, currency, portfolioId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        }
    }

    @PatchMapping("/currency")
    public UserCurrencyUpdateResponse updateUserCurrency(
            @RequestParam String username,
            @RequestParam String currency
    ) {
        try {
            return holdingService.updateUserCurrency(username, currency);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        }
    }

    @GetMapping("/performance")
    public PortfolioPerformanceResponse getPortfolioPerformance(
            @RequestParam String username,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) Integer portfolioId
    ) {
        try {
            return holdingService.getPortfolioPerformance(username, currency, portfolioId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        }
    }

    @GetMapping("/history/asset")
    public HoldingHistoryResponse getHoldingHistory(
            @RequestParam String username,
            @RequestParam String symbol,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) String interval,
            @RequestParam(required = false) Integer portfolioId
    ) {
        try {
            return holdingService.getHoldingHistory(username, symbol, currency, interval, portfolioId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        }
    }

    @GetMapping("/history/portfolio")
    public PortfolioHistoryResponse getPortfolioHistory(
            @RequestParam String username,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) String interval,
            @RequestParam(required = false) Integer portfolioId
    ) {
        try {
            return holdingService.getPortfolioHistory(username, currency, interval, portfolioId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        }
    }
}
