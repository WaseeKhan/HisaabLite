package com.hisaablite.repository;

import com.hisaablite.entity.Sale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    // Fetch sale with shop to avoid lazy loading issues
    @Query("SELECT s FROM Sale s JOIN FETCH s.shop WHERE s.id = :id")
    Optional<Sale> findByIdWithShop(@Param("id") Long id);
}