package com.hisaablite.repository;

import com.hisaablite.entity.Shop;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopRepository extends JpaRepository<Shop, Long> {

    boolean existsByPanNumber(String panNumber);

    Optional<Shop> findByPanNumber(String panNumber);
}