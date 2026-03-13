package com.hisaablite.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hisaablite.entity.SupportTicket;
import com.hisaablite.entity.TicketPriority;
import com.hisaablite.entity.TicketReply;
import com.hisaablite.entity.TicketStatus;
import com.hisaablite.entity.User;
import com.hisaablite.repository.SupportTicketRepository;
import com.hisaablite.repository.TicketReplyRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class SupportService {
    
    private final SupportTicketRepository ticketRepository;
    private final TicketReplyRepository replyRepository;
    private final EmailService emailService;
    
    private static final DateTimeFormatter TICKET_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    
    @Transactional
    public SupportTicket createTicket(User user, String subject, String message, TicketPriority priority) {
        // Generate ticket number: HL + YYYYMMDDHHMM + random 4 digits
        String timestamp = LocalDateTime.now().format(TICKET_FORMAT);
        String random = String.format("%04d", (int)(Math.random() * 10000));
        String ticketNumber = "HL" + timestamp + random;
        
        SupportTicket ticket = new SupportTicket();
        ticket.setTicketNumber(ticketNumber);
        ticket.setUser(user);
        ticket.setShop(user.getShop());
        ticket.setSubject(subject);
        ticket.setMessage(message);
        ticket.setPriority(priority != null ? priority : TicketPriority.MEDIUM);
        ticket.setStatus(TicketStatus.OPEN);
        
        ticket = ticketRepository.save(ticket);
        
        // Send confirmation email
        sendTicketCreatedEmail(user, ticket);
        
        log.info("Support ticket created: {} for user: {}", ticketNumber, user.getUsername());
        
        return ticket;
    }
    
    @Transactional
    public TicketReply addReply(Long ticketId, User user, String message, boolean isAdmin) {
        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));
        
        TicketReply reply = new TicketReply();
        reply.setTicket(ticket);
        reply.setUser(user);
        reply.setMessage(message);
        reply.setAdminReply(isAdmin);
        reply.setCreatedAt(LocalDateTime.now());
        
        reply = replyRepository.save(reply);
        
        // Update ticket status
        ticket.setUpdatedAt(LocalDateTime.now());
        if (!isAdmin) {
            ticket.setStatus(TicketStatus.WAITING_CUSTOMER);
        } else {
            ticket.setStatus(TicketStatus.IN_PROGRESS);
        }
        ticketRepository.save(ticket);
        
        // Send notification email
        sendReplyNotification(ticket, reply, isAdmin);
        
        return reply;
    }
    
    @Transactional
    public void resolveTicket(Long ticketId, User user) {
        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));
        
        ticket.setStatus(TicketStatus.RESOLVED);
        ticket.setResolvedAt(LocalDateTime.now());
        ticket.setUpdatedAt(LocalDateTime.now());
        ticketRepository.save(ticket);
        
        log.info("Ticket resolved: {}", ticket.getTicketNumber());
    }
    
    @Transactional
    public void closeTicket(Long ticketId) {
        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));
        
        ticket.setStatus(TicketStatus.CLOSED);
        ticket.setUpdatedAt(LocalDateTime.now());
        ticketRepository.save(ticket);
    }
    
    public Page<SupportTicket> getUserTickets(User user, int page) {
        Pageable pageable = PageRequest.of(
            page, 
            10, 
            Sort.by("createdAt").descending()
        );
        return ticketRepository.findByShop(user.getShop(), pageable);
    }
    
    public SupportTicket getTicket(String ticketNumber) {
        return ticketRepository.findByTicketNumber(ticketNumber);
    }
    
    public List<TicketReply> getTicketReplies(Long ticketId) {
        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));
        return replyRepository.findByTicketOrderByCreatedAtAsc(ticket);
    }
   // Inside SupportService.java, replace email calls with:

private void sendTicketCreatedEmail(User user, SupportTicket ticket) {
    String subject = "[HisaabLite Support] Ticket Created #" + ticket.getTicketNumber();
    
    // Plain text content (will be wrapped in HTML by sendSupportEmail)
    String content = String.format(
        "Dear %s,\n\n" +
        "Your support ticket has been created successfully.\n\n" +
        "Ticket Number: %s\n" +
        "Subject: %s\n" +
        "Priority: %s\n" +
        "Status: %s\n\n" +
        "We will get back to you shortly.\n\n" +
        "Track your ticket: %s/support/ticket/%s\n\n" +
        "Thanks,\nHisaabLite Support Team",
        user.getName() != null ? user.getName() : user.getUsername(),
        ticket.getTicketNumber(),
        ticket.getSubject(),
        ticket.getPriority().getDisplayName(),
        ticket.getStatus().getDisplayName(),
        "https://hisaablite.duckdns.org",
        ticket.getTicketNumber()
    );
    
    
    emailService.sendSupportEmail(user.getUsername(), subject, content);
}

private void sendReplyNotification(SupportTicket ticket, TicketReply reply, boolean isAdmin) {
    if (isAdmin) {
        String subject = "New reply on your ticket #" + ticket.getTicketNumber();
        String content = String.format(
            "Dear %s,\n\n" +
            "Our support team has replied to your ticket.\n\n" +
            "Ticket: %s\n" +
            "Reply: %s\n\n" +
            "View: %s/support/ticket/%s\n\n" +
            "Thanks,\nHisaabLite Support Team",
            ticket.getUser().getName() != null ? ticket.getUser().getName() : ticket.getUser().getUsername(),
            ticket.getSubject(),
            reply.getMessage(),
            "https://hisaablite.duckdns.org",
            ticket.getTicketNumber()
        );
        
        
        emailService.sendSupportEmail(ticket.getUser().getUsername(), subject, content);
        
    } else {
        log.info("Customer replied to ticket: {}", ticket.getTicketNumber());
        
        // Optional: Send to support team
        String subject = "Customer replied to ticket #" + ticket.getTicketNumber();
        String content = String.format(
            "Customer %s has replied to ticket #%s.\n\n" +
            "Reply: %s\n\n" +
            "View in admin panel.",
            ticket.getUser().getUsername(),
            ticket.getTicketNumber(),
            reply.getMessage()
        );
        
        // Send to support team -uncomment if needed
        // emailService.sendSupportEmail("support@hisaablite.com", subject, content);
    }
}
}