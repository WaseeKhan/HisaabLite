package com.hisaablite.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.hisaablite.entity.Shop;
import com.hisaablite.entity.Supplier;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    List<Supplier> findByShopAndActiveTrueOrderByNameAsc(Shop shop);

    Page<Supplier> findByShopAndActiveTrueOrderByNameAsc(Shop shop, Pageable pageable);

    Optional<Supplier> findByIdAndShop(Long id, Shop shop);

    Optional<Supplier> findByShopAndNameIgnoreCase(Shop shop, String name);

    long countByShopAndActiveTrue(Shop shop);
}
