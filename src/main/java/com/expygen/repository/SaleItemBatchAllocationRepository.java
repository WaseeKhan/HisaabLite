package com.expygen.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.expygen.entity.SaleItem;
import com.expygen.entity.SaleItemBatchAllocation;

public interface SaleItemBatchAllocationRepository extends JpaRepository<SaleItemBatchAllocation, Long> {

    List<SaleItemBatchAllocation> findBySaleItem(SaleItem saleItem);

    List<SaleItemBatchAllocation> findBySaleItemIn(List<SaleItem> saleItems);
}
