package com.expygen.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Data
@Table(name = "support_tickets")
public class SupportTicket {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String ticketNumber;
    
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @ManyToOne
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;
    
    @Column(nullable = false)
    private String subject;
    
    @Column(nullable = false, length = 5000)
    private String message;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status = TicketStatus.OPEN;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketPriority priority = TicketPriority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(name = "root_cause")
    private SupportRootCause rootCause;

    @Column(name = "internal_note", length = 4000)
    private String internalNote;

    @Column(name = "assigned_admin_username")
    private String assignedAdminUsername;

    @Column(name = "due_at")
    private LocalDateTime dueAt;

    @Column(name = "related_subscription_issue", nullable = false)
    private boolean relatedSubscriptionIssue = false;

    @Column(name = "related_whatsapp_issue", nullable = false)
    private boolean relatedWhatsappIssue = false;
    
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    private LocalDateTime updatedAt;
    
    private LocalDateTime resolvedAt;
    
    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<TicketReply> replies;
    
    @Column(length = 500)
    private String attachmentUrl;
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
