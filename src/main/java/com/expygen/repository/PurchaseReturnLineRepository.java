package com.expygen.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.expygen.entity.PurchaseReturnLine;

public interface PurchaseReturnLineRepository extends JpaRepository<PurchaseReturnLine, Long> {
}
