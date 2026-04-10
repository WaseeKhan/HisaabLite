package com.expygen.admin.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class AdminDashboardDTO {
    // Stats Cards
    private long totalShops;
    private long totalUsers;
    private long activeUsers;
    private long pendingApprovals;
    private long activeShops;
    private long inactiveShops;
    private double totalRevenue;
    private long totalInvoices;
    
    // Lists
    private List<Map<String, Object>> recentShops;
    private List<Map<String, Object>> recentUsers;
    private List<Map<String, Object>> subscriptionStats;
    private List<PopularPlanDTO> popularPlans;
    
    // Charts Data
    private List<String> revenueLabels;
    private List<Double> revenueData;
    private List<Long> usersData;
    private List<String> planLabels;
    private List<Long> planData;
    
    // Period
    private String period;


      //  NEW: Ticket stats fields
    private Long totalTickets;
    private Long openTickets;
    private Long inProgressTickets;
    private Long resolvedTickets;
    private Long closedTickets;
    private Long urgentTickets;
    
    // Ticket trend data (optional)
    private List<String> ticketLabels;
    private List<Long> ticketData;
}