package com.project.finance.repository;

import com.project.finance.entity.Holding;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HoldingRepository extends JpaRepository<Holding, Integer> {

    List<Holding> findByUserUserIdOrderByHoldingIdAsc(Integer userId);

    List<Holding> findByUserUserIdAndPortfolioPortfolioIdOrderByHoldingIdAsc(Integer userId, Integer portfolioId);

    List<Holding> findByUserUserIdAndAssetAssetIdOrderByHoldingIdAsc(Integer userId, Integer assetId);

    List<Holding> findByUserUserIdAndAssetAssetIdAndPortfolioPortfolioIdOrderByHoldingIdAsc(
            Integer userId,
            Integer assetId,
            Integer portfolioId
    );

    Optional<Holding> findByUserUserIdAndAssetAssetIdAndPortfolioPortfolioId(Integer userId, Integer assetId, Integer portfolioId);

    List<Holding> findByPortfolioPortfolioIdOrderByHoldingIdAsc(Integer portfolioId);
}

