package com.hisaablite.repository;

import com.hisaablite.entity.SupportTicket;
import com.hisaablite.entity.TicketReply;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TicketReplyRepository extends JpaRepository<TicketReply, Long> {
    List<TicketReply> findByTicketOrderByCreatedAtAsc(SupportTicket ticket);
}