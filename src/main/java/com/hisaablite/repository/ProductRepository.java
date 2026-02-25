package com.hisaablite.repository;

import com.hisaablite.entity.Product;
import com.hisaablite.entity.Shop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByShop(Shop shop);
}

