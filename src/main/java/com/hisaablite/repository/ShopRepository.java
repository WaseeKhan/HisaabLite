package com.hisaablite.repository;

import com.hisaablite.entity.Shop;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopRepository extends JpaRepository<Shop, Long> {

    boolean existsByPanNumber(String panNumber);
}