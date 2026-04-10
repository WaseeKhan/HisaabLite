package com.expygen.repository;

import com.expygen.entity.SupportTicket;
import com.expygen.entity.TicketPriority;
import com.expygen.entity.TicketStatus;
import com.expygen.entity.Shop;
import com.expygen.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {
    Page<SupportTicket> findByShop(Shop shop, Pageable pageable);
    Page<SupportTicket> findByUser(User user, Pageable pageable);
    long countByShop(Shop shop);
    long countByShopAndStatus(Shop shop, TicketStatus status);
    long countByShopAndPriority(Shop shop, TicketPriority priority);
    List<SupportTicket> findByStatus(String status);
    SupportTicket findByTicketNumber(String ticketNumber);

    // Admin methods
    Page<SupportTicket> findByStatus(TicketStatus status, Pageable pageable);
    Page<SupportTicket> findByPriority(TicketPriority priority, Pageable pageable);
    
    @Query("SELECT COUNT(t) FROM SupportTicket t WHERE t.status = ?1")
    long countByStatus(TicketStatus status);
    
    @Query("SELECT t FROM SupportTicket t ORDER BY " +
           "CASE t.priority " +
           "WHEN 'URGENT' THEN 1 " +
           "WHEN 'HIGH' THEN 2 " +
           "WHEN 'MEDIUM' THEN 3 " +
           "WHEN 'LOW' THEN 4 END, " +
           "t.createdAt DESC")
    Page<SupportTicket> findAllOrderByPriority(Pageable pageable);

    @Query("SELECT COUNT(t) FROM SupportTicket t WHERE t.priority = ?1")
    long countByPriority(TicketPriority priority);

    @Query("SELECT COUNT(t) FROM SupportTicket t WHERE t.createdAt BETWEEN ?1 AND ?2")
    Long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    
    // Count tickets by user
    Long countByUser(User user);
    
    // Reassign all tickets from one user to another
    @Modifying
    @Transactional
    @Query("UPDATE SupportTicket t SET t.user = :newUser WHERE t.user = :oldUser")
    int reassignTickets(@Param("oldUser") User oldUser, @Param("newUser") User newUser);
}
