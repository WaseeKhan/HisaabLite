package com.expygen.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "ticket_replies")
public class TicketReply {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "ticket_id", nullable = false)
    private SupportTicket ticket;
    
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user; 
    @Column(nullable = false, length = 5000)
    private String message;
    
    @Column(name = "is_admin_reply", nullable = false)
    private boolean isAdminReply = false;
    
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(length = 500)
    private String attachmentUrl;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}