package com.expygen.repository;

import com.expygen.entity.Shop;
import com.expygen.entity.UpgradeRequest;
import com.expygen.entity.UpgradeRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

import com.expygen.entity.User;
import jakarta.transaction.Transactional;

public interface UpgradeRequestRepository extends JpaRepository<UpgradeRequest, Long> {
    @EntityGraph(attributePaths = {"shop", "requestedBy", "activatedBy"})
    Page<UpgradeRequest> findByStatusOrderByCreatedAtDesc(UpgradeRequestStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"shop", "requestedBy", "activatedBy"})
    Page<UpgradeRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"shop", "requestedBy", "activatedBy"})
    List<UpgradeRequest> findTop10ByShopOrderByCreatedAtDesc(Shop shop);

    @EntityGraph(attributePaths = {"shop", "requestedBy", "activatedBy"})
    List<UpgradeRequest> findByShopInAndStatusInOrderByCreatedAtDesc(List<Shop> shops, List<UpgradeRequestStatus> statuses);

    @EntityGraph(attributePaths = {"shop", "requestedBy", "activatedBy"})
    Optional<UpgradeRequest> findFirstByShopAndStatusInOrderByCreatedAtDesc(Shop shop, List<UpgradeRequestStatus> statuses);
    long countByShop(Shop shop);
    long countByStatus(UpgradeRequestStatus status);
    long countByStatusIn(List<UpgradeRequestStatus> statuses);

    @Modifying
    @Transactional
    @Query("UPDATE UpgradeRequest ur SET ur.requestedBy = :newUser WHERE ur.requestedBy = :oldUser")
    int reassignRequestedBy(@Param("oldUser") User oldUser, @Param("newUser") User newUser);

    @Modifying
    @Transactional
    @Query("UPDATE UpgradeRequest ur SET ur.activatedBy = :newUser WHERE ur.activatedBy = :oldUser")
    int reassignActivatedBy(@Param("oldUser") User oldUser, @Param("newUser") User newUser);

    @Query("select ur.status, count(ur) from UpgradeRequest ur group by ur.status")
    List<Object[]> countGroupedByStatus();
}
