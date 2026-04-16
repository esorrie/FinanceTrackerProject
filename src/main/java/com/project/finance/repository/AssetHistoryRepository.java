package com.project.finance.repository;

import com.project.finance.entity.AssetHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetHistoryRepository extends JpaRepository<AssetHistory, Integer> {
}
