package com.hisaablite.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hisaablite.entity.SaleItem;
import com.hisaablite.entity.SaleItemBatchAllocation;

public interface SaleItemBatchAllocationRepository extends JpaRepository<SaleItemBatchAllocation, Long> {

    List<SaleItemBatchAllocation> findBySaleItem(SaleItem saleItem);

    List<SaleItemBatchAllocation> findBySaleItemIn(List<SaleItem> saleItems);
}
