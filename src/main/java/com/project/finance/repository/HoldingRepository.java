package com.project.finance.repository;

import com.project.finance.entity.Holding;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HoldingRepository extends JpaRepository<Holding, Integer> {

    List<Holding> findByUserUserIdOrderByHoldingIdAsc(Integer userId);
}

