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
                Page<SupportTicket> tickets = supportService.getUserTickets(user, page);
                model.addAttribute("tickets", tickets);
                model.addAttribute("currentPage", page);
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
        
        TicketPriority ticketPriority = priority != null ? 
            TicketPriority.valueOf(priority) : TicketPriority.MEDIUM;
        
        SupportTicket ticket = supportService.createTicket(user, subject, message, ticketPriority);
        
        redirectAttributes.addFlashAttribute("success", 
            "Ticket created successfully! Ticket #: " + ticket.getTicketNumber());
        
        return "redirect:/support/ticket/" + ticket.getTicketNumber();
    }
    
    @GetMapping("/ticket/{ticketNumber}")
    public String viewTicket(@PathVariable String ticketNumber,
                             Model model,
                             Authentication authentication) {
        
        SupportTicket ticket = supportService.getTicket(ticketNumber);
        if (ticket == null) {
            return "redirect:/support?error=Ticket not found";
        }
        
        // Security check - user can only see their own tickets
        User user = userRepository.findByUsername(authentication.getName()).orElse(null);
        if (user == null || !ticket.getUser().getId().equals(user.getId())) {
            return "redirect:/support?error=Unauthorized";
        }
        model.addAttribute("role", user.getRole().name());
        model.addAttribute("ticket", ticket);
        model.addAttribute("replies", supportService.getTicketReplies(ticket.getId()));
        
        return "ticket-detail";
    }
    
    @PostMapping("/ticket/{ticketNumber}/reply")
    public String addReply(@PathVariable String ticketNumber,
                           @RequestParam String message,
                           Authentication authentication,
                           RedirectAttributes redirectAttributes) {
        
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        SupportTicket ticket = supportService.getTicket(ticketNumber);
        if (ticket == null || !ticket.getUser().getId().equals(user.getId())) {
            return "redirect:/support?error=Invalid ticket";
        }
        
        supportService.addReply(ticket.getId(), user, message, false);
        
        redirectAttributes.addFlashAttribute("success", "Reply added successfully");
        return "redirect:/support/ticket/" + ticketNumber;
    }
    
    @PostMapping("/ticket/{ticketNumber}/resolve")
    public String resolveTicket(@PathVariable String ticketNumber,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        SupportTicket ticket = supportService.getTicket(ticketNumber);
        if (ticket == null || !ticket.getUser().getId().equals(user.getId())) {
            return "redirect:/support?error=Invalid ticket";
        }
        
        supportService.resolveTicket(ticket.getId(), user);
        
        redirectAttributes.addFlashAttribute("success", "Ticket marked as resolved");
        return "redirect:/support/ticket/" + ticketNumber;
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
                "Your data is automatically backed up daily. Contact support for manual backup.")
        );
    }
    
    // Inner class for FAQs
    static class FAQ {
        private String question;
        private String answer;
        
        FAQ(String question, String answer) {
            this.question = question;
            this.answer = answer;
        }
        
        public String getQuestion() { return question; }
        public String getAnswer() { return answer; }
    }
}