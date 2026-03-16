package com.hisaablite.controller;

import com.hisaablite.entity.*;
import com.hisaablite.repository.UserRepository;
import com.hisaablite.service.SupportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/support")
@RequiredArgsConstructor
@Slf4j
public class SupportController {

    private final SupportService supportService;
    private final UserRepository userRepository;

    @GetMapping
    public String supportPage(Model model,
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page) {

        if (authentication != null) {
            User user = userRepository.findByUsername(authentication.getName()).orElse(null);
            if (user != null) {
                try {
                    // 🔴 FIXED: getUserTickets now throws exception for non-owners
                    Page<SupportTicket> tickets = supportService.getShopTickets(user, page);
                    model.addAttribute("tickets", tickets);
                    model.addAttribute("currentPage", page);
                } catch (RuntimeException e) {
                    log.warn("User cannot view tickets: {}", e.getMessage());
                    model.addAttribute("tickets", Page.empty());
                }
                model.addAttribute("role", user.getRole().name());
            }
        }

        model.addAttribute("faqs", getFAQs());
        return "support";
    }

    @PostMapping("/create")
    public String createTicket(@RequestParam String subject,
            @RequestParam String message,
            @RequestParam(required = false) String priority,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getRole() != Role.OWNER) {
            redirectAttributes.addFlashAttribute("error",
                    "Access Denied: Only shop owners can create support tickets. Your role: " + user.getRole());
            return "redirect:/support";
        }
        try {
            TicketPriority ticketPriority = priority != null ? TicketPriority.valueOf(priority.toUpperCase())
                    : TicketPriority.LOW;

            SupportTicket ticket = supportService.createTicket(user, subject, message, ticketPriority);

            redirectAttributes.addFlashAttribute("success",
                    "Ticket created successfully! Ticket #: " + ticket.getTicketNumber());

            return "redirect:/support/ticket/" + ticket.getTicketNumber();

        } catch (RuntimeException e) {
            log.error("Failed to create ticket: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/support";
        }
    }

    @GetMapping("/ticket/{ticketNumber}")
    public String viewTicket(@PathVariable String ticketNumber,
            Model model,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        try {
         
            boolean isAdmin = user.getRole() == Role.ADMIN;
            SupportTicket ticket = supportService.getTicket(ticketNumber, user, isAdmin);

            
            List<TicketReply> replies = supportService.getTicketReplies(ticket.getId(), user, isAdmin);

            model.addAttribute("ticket", ticket);
            model.addAttribute("replies", replies);
            model.addAttribute("role", user.getRole().name());
            model.addAttribute("isAdmin", isAdmin);

            return "ticket-detail";

        } catch (RuntimeException e) {
            log.error("Error viewing ticket: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/support";
        }
    }

    @PostMapping("/ticket/{ticketNumber}/reply")
    public String addReply(@PathVariable String ticketNumber,
            @RequestParam String message,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        try {
            boolean isAdmin = user.getRole() == Role.ADMIN;

            SupportTicket ticket = supportService.getTicket(ticketNumber, user, isAdmin);

           
            supportService.addReply(ticket.getId(), user, message, isAdmin);

            redirectAttributes.addFlashAttribute("success", "Reply added successfully");
            return "redirect:/support/ticket/" + ticketNumber;

        } catch (RuntimeException e) {
            log.error("Failed to add reply: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/support/ticket/" + ticketNumber;
        }
    }

    @PostMapping("/ticket/{ticketNumber}/resolve")
    public String resolveTicket(@PathVariable String ticketNumber,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        try {
            boolean isAdmin = user.getRole() == Role.ADMIN;

            // First get the ticket to get its ID
            SupportTicket ticket = supportService.getTicket(ticketNumber, user, isAdmin);

            
            supportService.resolveTicket(ticket.getId(), user, isAdmin);

            redirectAttributes.addFlashAttribute("success", "Ticket marked as resolved");
            return "redirect:/support/ticket/" + ticketNumber;

        } catch (RuntimeException e) {
            log.error("Failed to resolve ticket: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/support/ticket/" + ticketNumber;
        }
    }

    @PostMapping("/ticket/{ticketNumber}/close")
    public String closeTicket(@PathVariable String ticketNumber,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        try {
            boolean isAdmin = user.getRole() == Role.ADMIN;

            // Only admin can close tickets
            if (!isAdmin) {
                throw new RuntimeException("Only admin can close tickets");
            }

            SupportTicket ticket = supportService.getTicket(ticketNumber, user, isAdmin);

           
            supportService.closeTicket(ticket.getId(), user, isAdmin);

            redirectAttributes.addFlashAttribute("success", "Ticket closed successfully");
            return "redirect:/admin/support/tickets";

        } catch (RuntimeException e) {
            log.error("Failed to close ticket: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/support/ticket/" + ticketNumber;
        }
    }

    private List<FAQ> getFAQs() {
        return List.of(
                new FAQ("How to add products?",
                        "Go to Products section and click 'Add Product'. Fill in name, price, stock and save."),
                new FAQ("How to create invoice?",
                        "In Billing page, add products to cart, enter customer details, and click 'Complete Sale'."),
                new FAQ("How to add staff members?",
                        "Owners can add staff from Staff section. Click 'Add Staff' and enter details."),
                new FAQ("Payment not received?",
                        "Payments are processed instantly. Check your bank statement or contact support."),
                new FAQ("How to change plan?",
                        "Go to Profile > Subscription Plan and select new plan. Changes apply next billing cycle."),
                new FAQ("Data backup?",
                        "Your data is automatically backed up daily. Contact support for manual backup."));
    }

    // Inner class for FAQs
    static class FAQ {
        private String question;
        private String answer;

        FAQ(String question, String answer) {
            this.question = question;
            this.answer = answer;
        }

        public String getQuestion() {
            return question;
        }

        public String getAnswer() {
            return answer;
        }
    }
}