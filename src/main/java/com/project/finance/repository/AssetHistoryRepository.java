package com.project.finance.repository;

import com.project.finance.entity.AssetHistory;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetHistoryRepository extends JpaRepository<AssetHistory, Integer> {

    Optional<AssetHistory> findTopByAssetAssetIdAndCurrencyCurrencyIdOrderByLastUpdateDesc(
            Integer assetId,
            Integer currencyId
    );
}
