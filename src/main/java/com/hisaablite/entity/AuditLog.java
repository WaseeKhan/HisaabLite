package com.hisaablite.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Entity;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String userRole;

    @Column
    private Long shopId;

    @Column
    private String shopName;

    @Column(nullable = false)
    private String action;

    @Column(length = 1000)
    private String details;

    @Column
    private String entityType;

    @Column
    private Long entityId;

    @Column
    private String ipAddress;

    @Column
    private String status;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;

    @Column(length = 2000)
    private String oldValue;

    @Column(length = 2000)
    private String newValue;
}
