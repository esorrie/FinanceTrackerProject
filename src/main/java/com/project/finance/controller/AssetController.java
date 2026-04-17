package com.project.finance.controller;

import com.project.finance.dto.AssetResponse;
import com.project.finance.entity.Asset;
import com.project.finance.repository.AssetRepository;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assets")
public class AssetController {

    private final AssetRepository assetRepository;

    public AssetController(AssetRepository assetRepository) {
        this.assetRepository = assetRepository;
    }

    @GetMapping
    public List<AssetResponse> listAssets() {
        List<Asset> assets = assetRepository.findAll();
        return assets.stream().map(a -> new AssetResponse(
                a.getAssetId(),
                a.getAssetSymbol(),
                a.getAssetName(),
                a.getCurrency() != null ? a.getCurrency().getCurrencyCode() : null,
                a.getOpenPrice(),
                a.getClosePrice(),
                a.getStockExchange()
        )).collect(Collectors.toList());
    }
}
