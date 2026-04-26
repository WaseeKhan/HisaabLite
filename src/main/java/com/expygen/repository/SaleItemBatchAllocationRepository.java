package com.expygen.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import com.expygen.entity.SaleItem;
import com.expygen.entity.SaleItemBatchAllocation;

public interface SaleItemBatchAllocationRepository extends JpaRepository<SaleItemBatchAllocation, Long> {

    List<SaleItemBatchAllocation> findBySaleItem(SaleItem saleItem);

    @Query("""
            SELECT allocation
            FROM SaleItemBatchAllocation allocation
            JOIN FETCH allocation.saleItem saleItem
            JOIN FETCH allocation.purchaseBatch purchaseBatch
            WHERE saleItem IN :saleItems
            """)
    List<SaleItemBatchAllocation> findBySaleItemIn(@Param("saleItems") List<SaleItem> saleItems);
}
