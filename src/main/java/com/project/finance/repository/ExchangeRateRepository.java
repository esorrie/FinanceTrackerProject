package com.project.finance.repository;

import com.project.finance.entity.ExchangeRate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Integer> {

    Optional<ExchangeRate> findTopByStartCurrencyCurrencyCodeIgnoreCaseAndEndCurrencyCurrencyCodeIgnoreCaseOrderByLastUpdatedDesc(
            String startCurrencyCode,
            String endCurrencyCode
    );

    Optional<ExchangeRate> findTopByStartCurrencyCurrencyIdAndEndCurrencyCurrencyIdOrderByLastUpdatedDesc(
            Integer startCurrencyId,
            Integer endCurrencyId
    );
}

