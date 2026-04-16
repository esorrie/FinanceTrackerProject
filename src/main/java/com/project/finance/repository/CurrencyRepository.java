package com.project.finance.repository;

import com.project.finance.entity.Currency;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CurrencyRepository extends JpaRepository<Currency, Integer> {

    Optional<Currency> findByCurrencyCodeIgnoreCase(String currencyCode);
}
