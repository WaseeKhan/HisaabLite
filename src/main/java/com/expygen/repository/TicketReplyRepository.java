package com.expygen.repository;

import com.expygen.entity.SupportTicket;
import com.expygen.entity.TicketReply;
import com.expygen.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface TicketReplyRepository extends JpaRepository<TicketReply, Long> {
    List<TicketReply> findByTicketOrderByCreatedAtAsc(SupportTicket ticket);
    
    // Count replies by user
    Long countByUser(User user);
    
    // Reassign all replies from one user to another
    @Modifying
    @Transactional
    @Query("UPDATE TicketReply r SET r.user = :newUser WHERE r.user = :oldUser")
    int reassignReplies(@Param("oldUser") User oldUser, @Param("newUser") User newUser);
}