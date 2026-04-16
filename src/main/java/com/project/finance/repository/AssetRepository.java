package com.project.finance.repository;

import com.project.finance.entity.Asset;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetRepository extends JpaRepository<Asset, Integer> {

    Optional<Asset> findByAssetSymbolIgnoreCase(String assetSymbol);
}

