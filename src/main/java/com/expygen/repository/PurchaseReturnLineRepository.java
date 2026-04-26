package com.expygen.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import com.expygen.entity.PurchaseReturnLine;

public interface PurchaseReturnLineRepository extends JpaRepository<PurchaseReturnLine, Long> {

    @Query("""
            SELECT line.purchaseReturn.id, COUNT(line)
            FROM PurchaseReturnLine line
            WHERE line.purchaseReturn.id IN :purchaseReturnIds
            GROUP BY line.purchaseReturn.id
            """)
    List<Object[]> countByPurchaseReturnIds(@Param("purchaseReturnIds") List<Long> purchaseReturnIds);
}
