package com.hisaablite.admin.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.hisaablite.entity.Role;
import com.hisaablite.entity.SupportTicket;
import com.hisaablite.entity.TicketPriority;
import com.hisaablite.entity.TicketReply;
import com.hisaablite.entity.TicketStatus;
import com.hisaablite.entity.User;
import com.hisaablite.repository.SupportTicketRepository;
import com.hisaablite.repository.TicketReplyRepository;
import com.hisaablite.repository.UserRepository;
import com.hisaablite.service.SupportService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequestMapping("/admin/support")
@RequiredArgsConstructor
@Slf4j
public class AdminSupportController {

    private final SupportTicketRepository ticketRepository;
    private final TicketReplyRepository ticketReplyRepository;
    private final SupportService supportService;
    private final UserRepository userRepository;

    // Admin Support Dashboard
    @GetMapping
    public String supportDashboard(Model model,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            Authentication authentication) {

        // Check if user is admin
        User admin = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        model.addAttribute("role", admin.getRole().name());

        if (admin.getRole() != Role.ADMIN) {
            return "redirect:/dashboard";
        }

        Pageable pageable = PageRequest.of(page, 20, Sort.by("createdAt").descending());

        Page<SupportTicket> tickets;
        if (status != null && !status.isEmpty()) {
            tickets = ticketRepository.findByStatus(TicketStatus.valueOf(status), pageable);
        } else if (priority != null && !priority.isEmpty()) {
            tickets = ticketRepository.findByPriority(TicketPriority.valueOf(priority), pageable);
        } else {
            tickets = ticketRepository.findAll(pageable);
        }

        model.addAttribute("tickets", tickets);
        model.addAttribute("currentPage", page);
        model.addAttribute("status", status);
        model.addAttribute("priority", priority);
        model.addAttribute("totalTickets", ticketRepository.count());
        model.addAttribute("openTickets", ticketRepository.countByStatus(TicketStatus.OPEN));
        model.addAttribute("inProgressTickets", ticketRepository.countByStatus(TicketStatus.IN_PROGRESS));
        model.addAttribute("resolvedTickets", ticketRepository.countByStatus(TicketStatus.RESOLVED));

        return "admin/support-dashboard";
    }

    // View Single Ticket
    @GetMapping("/ticket/{ticketNumber}")
    public String viewTicket(@PathVariable String ticketNumber,
            Model model,
            Authentication authentication) {

        User admin = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        try {
            // 🔴 Admin is always true
            boolean isAdmin = true;

            // 🔴 Use service method with permission check
            SupportTicket ticket = supportService.getTicket(ticketNumber, admin, isAdmin);

            // 🔴 Get replies with admin permissions
            List<TicketReply> replies = supportService.getTicketReplies(ticket.getId(), admin, isAdmin);

            model.addAttribute("ticket", ticket);
            model.addAttribute("replies", replies);
            model.addAttribute("isAdmin", isAdmin);

            return "admin/support-ticket";

        } catch (RuntimeException e) {
            log.error("Error viewing ticket: {}", e.getMessage());
            model.addAttribute("error", e.getMessage());
            return "redirect:/admin/support/tickets";
        }
    }

    // Admin Reply to Ticket
    @PostMapping("/ticket/{ticketNumber}/reply")
    public String adminReply(@PathVariable String ticketNumber,
            @RequestParam String message,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        User admin = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        SupportTicket ticket = ticketRepository.findByTicketNumber(ticketNumber);
        if (ticket == null) {
            redirectAttributes.addFlashAttribute("error", "Ticket not found");
            return "redirect:/admin/support";
        }

        supportService.addReply(ticket.getId(), admin, message, true);

        redirectAttributes.addFlashAttribute("success", "Reply sent successfully");
        return "redirect:/admin/support/ticket/" + ticketNumber;
    }

    // Update Ticket Status
    @PostMapping("/ticket/{ticketNumber}/status")
    public String updateStatus(@PathVariable String ticketNumber,
            @RequestParam TicketStatus status,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        User admin = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        SupportTicket ticket = ticketRepository.findByTicketNumber(ticketNumber);
        if (ticket == null) {
            redirectAttributes.addFlashAttribute("error", "Ticket not found");
            return "redirect:/admin/support";
        }

        ticket.setStatus(status);
        ticket.setUpdatedAt(LocalDateTime.now());

        if (status == TicketStatus.RESOLVED) {
            ticket.setResolvedAt(LocalDateTime.now());
        }

        ticketRepository.save(ticket);

        redirectAttributes.addFlashAttribute("success", "Ticket status updated");
        return "redirect:/admin/support/ticket/" + ticketNumber;
    }

    // Assign Ticket to Admin (optional)
    @PostMapping("/ticket/{ticketNumber}/assign")
    public String assignTicket(@PathVariable String ticketNumber,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        User admin = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        SupportTicket ticket = ticketRepository.findByTicketNumber(ticketNumber);
        if (ticket == null) {
            redirectAttributes.addFlashAttribute("error", "Ticket not found");
            return "redirect:/admin/support";
        }

        // You can add an 'assignedTo' field in SupportTicket if needed
        // ticket.setAssignedTo(admin);
        ticket.setStatus(TicketStatus.IN_PROGRESS);
        ticket.setUpdatedAt(LocalDateTime.now());
        ticketRepository.save(ticket);

        redirectAttributes.addFlashAttribute("success", "Ticket assigned to you");
        return "redirect:/admin/support/ticket/" + ticketNumber;
    }

    @PostMapping("/ticket/{ticketNumber}/close")
    public String closeTicket(@PathVariable String ticketNumber,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        User admin = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        try {
            boolean isAdmin = true;
            SupportTicket ticket = supportService.getTicket(ticketNumber, admin, isAdmin);

            supportService.closeTicket(ticket.getId(), admin, isAdmin);

            redirectAttributes.addFlashAttribute("success",
                    "Ticket closed successfully and email sent to owner");
            return "redirect:/admin/support/ticket/" + ticketNumber;

        } catch (RuntimeException e) {
            log.error("Failed to close ticket: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/support/ticket/" + ticketNumber;
        }
    }


    @PostMapping("/ticket/{ticketNumber}/resolve")
public String resolveTicket(@PathVariable String ticketNumber,
                           Authentication authentication,
                           RedirectAttributes redirectAttributes) {

    User admin = userRepository.findByUsername(authentication.getName())
            .orElseThrow(() -> new RuntimeException("Admin not found"));

    try {
        boolean isAdmin = true;
        SupportTicket ticket = supportService.getTicket(ticketNumber, admin, isAdmin);

        supportService.resolveTicket(ticket.getId(), admin, isAdmin);

        redirectAttributes.addFlashAttribute("success",
                "Ticket resolved successfully and email sent to owner");
        return "redirect:/admin/support/ticket/" + ticketNumber;

    } catch (RuntimeException e) {
        log.error("Failed to resolve ticket: {}", e.getMessage());
        redirectAttributes.addFlashAttribute("error", e.getMessage());
        return "redirect:/admin/support/ticket/" + ticketNumber;
    }
}
}