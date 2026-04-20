package com.project.finance.repository;

import com.project.finance.entity.Portfolio;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioRepository extends JpaRepository<Portfolio, Integer> {

    Optional<Portfolio> findByUserUserIdAndPortfolioNameIgnoreCase(Integer userId, String portfolioName);

    Optional<Portfolio> findByPortfolioIdAndUserUserId(Integer portfolioId, Integer userId);

    List<Portfolio> findByUserUserIdOrderByPortfolioNameAscPortfolioIdAsc(Integer userId);
}
