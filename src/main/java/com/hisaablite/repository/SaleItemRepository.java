package com.hisaablite.repository;

import com.hisaablite.entity.Sale;
import com.hisaablite.entity.SaleItem;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {

    List<SaleItem> findBySale(Sale sale);
}