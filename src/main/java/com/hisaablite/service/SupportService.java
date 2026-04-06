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
import com.hisaablite.entity.Role;
import com.hisaablite.repository.SupportTicketRepository;
import com.hisaablite.repository.TicketReplyRepository;
import com.hisaablite.repository.UserRepository;
import com.hisaablite.config.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class SupportService {

    private final SupportTicketRepository ticketRepository;
    private final TicketReplyRepository replyRepository;
    private final EmailService emailService;
    private final UrlService urlService;
    private final UserRepository userRepository;
      private final AppConfig appConfig; 
    

    private static final DateTimeFormatter TICKET_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    /**
     * Create a support ticket - ONLY OWNERS CAN CREATE TICKETS
     */
    @Transactional
    public SupportTicket createTicket(User user, String subject, String message, TicketPriority priority) {

      
        if (user.getRole() != Role.OWNER) {
            throw new RuntimeException(
                    "Access Denied: Only shop owners can create support tickets. Your role: " + user.getRole());
        }

        // Generate ticket number: app short code + timestamp + random 4 digits
        String timestamp = LocalDateTime.now().format(TICKET_FORMAT);
        String random = String.format("%04d", (int) (Math.random() * 10000));
        String ticketNumber = appConfig.getAppShortCode() + timestamp + random;

        SupportTicket ticket = new SupportTicket();
        ticket.setTicketNumber(ticketNumber);
        ticket.setUser(user); // The creator (owner)
        ticket.setShop(user.getShop());
        ticket.setSubject(subject);
        ticket.setMessage(message);
        ticket.setPriority(priority != null ? priority : TicketPriority.LOW);
        ticket.setStatus(TicketStatus.OPEN);

        ticket = ticketRepository.save(ticket);

        // Send confirmation email to the owner
        sendTicketCreatedEmail(user, ticket);

        log.info("Support ticket created: {} by owner: {} for shop: {}",
                ticketNumber, user.getUsername(), user.getShop().getName());

        return ticket;
    }

    /**
     * Add reply to ticket - ONLY OWNER OR ADMIN CAN REPLY
     */
    @Transactional
    public TicketReply addReply(Long ticketId, User user, String message, boolean isAdmin) {
        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));


        if (!isAdmin && user.getRole() != Role.OWNER) {
            throw new RuntimeException(
                    "Access Denied: Only shop owners can reply to tickets. Your role: " + user.getRole());
        }

        // If not admin, verify user owns this shop
        if (!isAdmin) {
            if (!ticket.getShop().getId().equals(user.getShop().getId())) {
                throw new RuntimeException("Access Denied: You can only reply to tickets from your own shop");
            }
        }

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

        log.info("Reply added to ticket: {} by: {} (isAdmin: {})",
                ticket.getTicketNumber(), user.getUsername(), isAdmin);

        return reply;
    }

    /**
     * Get tickets for a shop - ONLY OWNER CAN VIEW TICKETS
     */
    public Page<SupportTicket> getShopTickets(User user, int page) {

        
        if (user.getRole() != Role.OWNER) {
            throw new RuntimeException(
                    "Access Denied: Only shop owners can view tickets. Your role: " + user.getRole());
        }

        Pageable pageable = PageRequest.of(
                page,
                10,
                Sort.by("createdAt").descending());
        return ticketRepository.findByShop(user.getShop(), pageable);
    }

    /**
     * Get ticket by number - ONLY OWNER OR ADMIN CAN VIEW
     */
    public SupportTicket getTicket(String ticketNumber, User user, boolean isAdmin) {
        SupportTicket ticket = ticketRepository.findByTicketNumber(ticketNumber);

        if (ticket == null) {
            throw new RuntimeException("Ticket not found");
        }

        if (!isAdmin) {
            if (user.getRole() != Role.OWNER) {
                throw new RuntimeException(
                        "Access Denied: Only shop owners can view tickets. Your role: " + user.getRole());
            }
            if (!ticket.getShop().getId().equals(user.getShop().getId())) {
                throw new RuntimeException("Access Denied: You can only view tickets from your own shop");
            }
        }

        return ticket;
    }

    /**
     * Get ticket replies - ONLY OWNER OR ADMIN CAN VIEW
     */
    public List<TicketReply> getTicketReplies(Long ticketId, User user, boolean isAdmin) {
        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));

       

        if (!isAdmin) {
            if (user.getRole() != Role.OWNER) {
                throw new RuntimeException(
                        "Access Denied: Only shop owners can view ticket replies. Your role: " + user.getRole());
            }
            if (!ticket.getShop().getId().equals(user.getShop().getId())) {
                throw new RuntimeException("Access Denied: You can only view replies from your own shop");
            }
        }

        return replyRepository.findByTicketOrderByCreatedAtAsc(ticket);
    }


@Transactional
public void resolveTicket(Long ticketId, User user, boolean isAdmin) {
    log.info("Resolving ticket {} by {} (admin={})", ticketId, user.getUsername(), isAdmin);
    
    SupportTicket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new RuntimeException("Ticket not found"));

    log.debug("Ticket found: {}, current status: {}", ticket.getTicketNumber(), ticket.getStatus());
    
    // Check if ticket is already resolved or closed
    if (ticket.getStatus() == TicketStatus.RESOLVED) {
        throw new RuntimeException("Ticket is already resolved");
    }
    
    if (ticket.getStatus() == TicketStatus.CLOSED) {
        throw new RuntimeException("Cannot resolve a closed ticket");
    }

  
    if (!isAdmin && user.getRole() != Role.OWNER) {
        throw new RuntimeException(
                "Access Denied: Only shop owners or admin can resolve tickets. Your role: " + user.getRole());
    }

    // If not admin, verify user owns this shop
    if (!isAdmin) {
        if (!ticket.getShop().getId().equals(user.getShop().getId())) {
            throw new RuntimeException("Access Denied: You can only resolve tickets from your own shop");
        }
    }

    ticket.setStatus(TicketStatus.RESOLVED);
    ticket.setResolvedAt(LocalDateTime.now());
    ticket.setUpdatedAt(LocalDateTime.now());
    ticketRepository.save(ticket);

    log.debug("Ticket {} marked resolved, preparing notification", ticket.getTicketNumber());
    
  
   
    sendTicketResolvedEmail(ticket);

    log.info("Ticket resolved: {} by user: {} (isAdmin: {})",
            ticket.getTicketNumber(), user.getUsername(), isAdmin);
}

/**
 * Send email notification when ticket is resolved
 */
private void sendTicketResolvedEmail(SupportTicket ticket) {
    log.debug("Preparing resolved email for ticket: {}", ticket.getTicketNumber());
    
    String subject = "Ticket #" + ticket.getTicketNumber() + " has been resolved";
    
    // Get ticket URL using UrlService
    String ticketUrl = urlService.getTicketUrl(ticket.getTicketNumber());
    
    String content = String.format(
        "Dear %s,\n\n" +
        "Your support ticket has been marked as resolved.\n\n" +
        "═══════════════════════════════════════\n" +
        "Ticket Number: %s\n" +
        "Subject: %s\n" +
        "Status: RESOLVED\n" +
        "═══════════════════════════════════════\n\n" +
        "If you are satisfied with the resolution, no further action is needed.\n" +
        "If you still need assistance, you can reply to reopen the ticket.\n\n" +
        "View your ticket: %s\n\n" +
        "Thanks,\n" +
        appConfig.getSupportTeamName(),
        ticket.getUser().getName() != null ? ticket.getUser().getName() : ticket.getUser().getUsername(),
        ticket.getTicketNumber(),
        ticket.getSubject(),
        ticketUrl
    );
    
    try {
        emailService.sendSupportEmail(ticket.getUser().getUsername(), subject, content);
        log.info("Resolved email sent successfully to: {}", ticket.getUser().getUsername());
    } catch (Exception e) {
        log.error("Failed to send resolved email: {}", e.getMessage(), e);
    }
}



@Transactional
public void closeTicket(Long ticketId, User user, boolean isAdmin) {
    log.info("Closing ticket {} by {} (admin={})", ticketId, user.getUsername(), isAdmin);
    
    SupportTicket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new RuntimeException("Ticket not found"));

    log.debug("Ticket found: {}, current status: {}", ticket.getTicketNumber(), ticket.getStatus());

    // Only admin can close
    if (!isAdmin) {
        throw new RuntimeException("Access Denied: Only admin can close tickets");
    }

    // Check if already closed
    if (ticket.getStatus() == TicketStatus.CLOSED) {
        throw new RuntimeException("Ticket is already closed");
    }

    ticket.setStatus(TicketStatus.CLOSED);
    ticket.setUpdatedAt(LocalDateTime.now());
    ticketRepository.save(ticket);

    log.debug("Ticket {} marked closed, preparing notification", ticket.getTicketNumber());

    // Send email notification to owner
    sendTicketClosedEmail(ticket);

    log.info("Ticket closed: {} by admin: {}", ticket.getTicketNumber(), user.getUsername());
}

/**
 * Send email notification when ticket is closed
 */
private void sendTicketClosedEmail(SupportTicket ticket) {
    log.debug("Preparing closed email for ticket: {}", ticket.getTicketNumber());
    
    String subject = "Ticket #" + ticket.getTicketNumber() + " has been closed";
    
    String ticketUrl = urlService.getTicketUrl(ticket.getTicketNumber());
    
    String content = String.format(
        "Dear %s,\n\n" +
        "Your support ticket has been closed by our support team.\n\n" +
        "═══════════════════════════════════════\n" +
        "Ticket Number: %s\n" +
        "Subject: %s\n" +
        "Status: CLOSED\n" +
        "═══════════════════════════════════════\n\n" +
        "If you have any further questions or need additional assistance, please create a new ticket.\n\n" +
        "View your closed ticket: %s\n\n" +
        "Thanks,\n" +
        appConfig.getSupportTeamName(),
        ticket.getUser().getName() != null ? ticket.getUser().getName() : ticket.getUser().getUsername(),
        ticket.getTicketNumber(),
        ticket.getSubject(),
        ticketUrl
    );
    
    try {
        emailService.sendSupportEmail(ticket.getUser().getUsername(), subject, content);
        log.info("Closed email sent successfully to: {}", ticket.getUser().getUsername());
    } catch (Exception e) {
        log.error("Failed to send closed email: {}", e.getMessage(), e);
    }
}




    /**
     * Get all tickets (Admin only)
     */
    public Page<SupportTicket> getAllTickets(int page, boolean isAdmin) {

      
        if (!isAdmin) {
            throw new RuntimeException("Access Denied: Only admin can view all tickets");
        }

        Pageable pageable = PageRequest.of(
                page,
                20,
                Sort.by("createdAt").descending());
        return ticketRepository.findAll(pageable);
    }

    // ==================== PRIVATE EMAIL METHODS ====================

    /**
     * Send email when ticket is created - TO THE OWNER ONLY
     */
    private void sendTicketCreatedEmail(User owner, SupportTicket ticket) {
        String subject = "[" + appConfig.getAppName() + " Support] Ticket Created #" + ticket.getTicketNumber();

        String ticketUrl = urlService.getTicketUrl(ticket.getTicketNumber());

        String content = String.format(
                "Dear %s,\n\n" +
                        "Your support ticket has been created successfully.\n\n" +
                        "═══════════════════════════════════════\n" +
                        "Ticket Number: %s\n" +
                        "Subject: %s\n" +
                        "Priority: %s\n" +
                        "Status: %s\n" +
                        "═══════════════════════════════════════\n\n" +
                        "We will get back to you shortly.\n\n" +
                        "Track your ticket: %s\n\n" +
                        "Thanks,\n" + appConfig.getSupportTeamName(),
                owner.getName() != null ? owner.getName() : owner.getUsername(),
                ticket.getTicketNumber(),
                ticket.getSubject(),
                ticket.getPriority().getDisplayName(),
                ticket.getStatus().getDisplayName(),
                ticketUrl);

        emailService.sendSupportEmail(owner.getUsername(), subject, content);
    }

    /**
     * Send notification when reply is added
     * - If admin replies → Email goes to owner
     * - If owner replies → Email goes to support team
     */
    private void sendReplyNotification(SupportTicket ticket, TicketReply reply, boolean isAdmin) {
        if (isAdmin) {
            
            String subject = "New reply on your ticket #" + ticket.getTicketNumber();

            String ticketUrl = urlService.getTicketUrl(ticket.getTicketNumber());

            String content = String.format(
                    "Dear %s,\n\n" +
                            "Our support team has replied to your ticket.\n\n" +
                            "═══════════════════════════════════════\n" +
                            "Ticket: %s\n" +
                            "Reply: %s\n" +
                            "═══════════════════════════════════════\n\n" +
                            "View your ticket: %s\n\n" +
                            "Thanks,\n" + appConfig.getSupportTeamName(),
                    ticket.getUser().getName() != null ? ticket.getUser().getName() : ticket.getUser().getUsername(),
                    ticket.getSubject(),
                    reply.getMessage(),
                    ticketUrl);

            emailService.sendSupportEmail(ticket.getUser().getUsername(), subject, content);
            log.info("Admin reply notification sent to owner: {}", ticket.getUser().getUsername());

        } else {
       
            log.info("Owner replied to ticket: {}", ticket.getTicketNumber());

            String subject = "Owner replied to ticket #" + ticket.getTicketNumber();

            String adminTicketUrl = urlService.getAdminTicketUrl(ticket.getTicketNumber());

            String content = String.format(
                    "Shop owner has replied to ticket #%s.\n\n" +
                            "═══════════════════════════════════════\n" +
                            "Shop: %s\n" +
                            "Owner: %s (%s)\n" +
                            "Subject: %s\n" +
                            "Reply: %s\n" +
                            "═══════════════════════════════════════\n\n" +
                            "View in admin panel: %s",
                    ticket.getTicketNumber(),
                    ticket.getShop().getName(),
                    ticket.getUser().getName(),
                    ticket.getUser().getUsername(),
                    ticket.getSubject(),
                    reply.getMessage(),
                    adminTicketUrl);

            emailService.sendSupportEmail(urlService.getSupportEmail(), subject, content);
            log.info("Owner reply notification sent to support team");
        }
    }
}
