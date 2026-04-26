package com.expygen.repository;

import com.expygen.entity.Shop;
import com.expygen.entity.UpgradeRequest;
import com.expygen.entity.UpgradeRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

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
    long countByStatus(UpgradeRequestStatus status);
    long countByStatusIn(List<UpgradeRequestStatus> statuses);

    @Query("select ur.status, count(ur) from UpgradeRequest ur group by ur.status")
    List<Object[]> countGroupedByStatus();
}
