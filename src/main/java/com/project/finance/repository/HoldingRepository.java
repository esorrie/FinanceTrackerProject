package com.project.finance.repository;

import com.project.finance.entity.Holding;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HoldingRepository extends JpaRepository<Holding, Integer> {
}

