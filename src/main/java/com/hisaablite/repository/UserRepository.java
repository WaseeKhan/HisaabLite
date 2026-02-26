package com.hisaablite.repository;

import com.hisaablite.entity.Role;
import com.hisaablite.entity.Shop;
import com.hisaablite.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);   //  REQUIRED FOR LOGIN

    boolean existsByUsername(String username);

    boolean existsByPhone(String phone);

    Long countByShop(Shop shop);
    Long countByShopAndRole(Shop shop, Role role);
}