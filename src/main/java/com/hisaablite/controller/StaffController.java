package com.hisaablite.controller;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.hisaablite.entity.Role;
import com.hisaablite.entity.Shop;
import com.hisaablite.entity.User;
import com.hisaablite.repository.UserRepository;
import com.hisaablite.repository.SaleRepository;
import com.hisaablite.repository.SupportTicketRepository;
import com.hisaablite.repository.TicketReplyRepository;
import com.hisaablite.service.PlanLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequiredArgsConstructor
@RequestMapping("/staff")
@Slf4j
public class StaffController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PlanLimitService planLimitService;
    private final SaleRepository saleRepository;
    private final SupportTicketRepository supportTicketRepository;
    private final TicketReplyRepository ticketReplyRepository;

    @GetMapping
    public String listStaff(Model model, Authentication auth) {
        User owner = userRepository
                .findByUsername(auth.getName())
                .orElseThrow();

        Shop shop = owner.getShop();
        List<User> staff = userRepository.findByShop(shop);

        model.addAttribute("staffList", staff);
        model.addAttribute("role", owner.getRole().name());
        model.addAttribute("usageStats", planLimitService.getUsageStats(shop));

        return "staff-list";
    }

    @GetMapping("/new")
    public String newStaffForm(Model model, Authentication authentication) {
        User owner = userRepository
                .findByUsername(authentication.getName())
                .orElseThrow();

        if (!planLimitService.canAddUser(owner.getShop())) {
            model.addAttribute("error",
                    "User limit reached! Your " + owner.getShop().getPlanType() +
                            " plan allows maximum " + planLimitService.getUserLimit(owner.getShop()) + " users.");
            return "staff-list";
        }

        model.addAttribute("user", new User());
        model.addAttribute("role", owner.getRole().name());
        return "staff-form";
    }

    @PostMapping
    public String saveStaff(@ModelAttribute User user,
            Authentication auth,
            Model model) {

        User owner = userRepository
                .findByUsername(auth.getName())
                .orElseThrow();

        Shop shop = owner.getShop();

        if (!planLimitService.canAddUser(shop)) {
            model.addAttribute("error",
                    "User limit reached! Your " + shop.getPlanType() +
                            " plan allows maximum " + planLimitService.getUserLimit(shop) + " users.");
            return "staff-form";
        }

        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            model.addAttribute("error", "Email already exists");
            return "staff-form";
        }

        if (userRepository.findByPhone(user.getPhone()).isPresent()) {
            model.addAttribute("error", "Phone number already exists");
            return "staff-form";
        }

        user.setShop(shop);
        user.setActive(true);
        user.setApproved(true);
        user.setCurrentPlan(shop.getPlanType());

        if (user.getRole() != Role.MANAGER && user.getRole() != Role.CASHIER) {
            user.setRole(Role.CASHIER);
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setCreatedAt(LocalDateTime.now());

        userRepository.save(user);

        log.info("Staff created: {} with role {} for shop {}",
                user.getUsername(), user.getRole(), shop.getName());

        return "redirect:/staff";
    }

    @PostMapping("/{id}/role")
    public String changeRole(@PathVariable Long id,
            @RequestParam Role role,
            RedirectAttributes redirectAttributes) {

        User user = userRepository.findById(id).orElseThrow();

        if (role == Role.MANAGER || role == Role.CASHIER) {
            user.setRole(role);
            userRepository.save(user);
            log.info("Role changed for user {} to {}", user.getUsername(), role);
        }

        return "redirect:/staff";
    }

    @PostMapping("/{id}/reset")
    public String resetPassword(@PathVariable Long id,
            RedirectAttributes redirectAttributes) {

        User user = userRepository.findById(id).orElseThrow();

        user.setPassword(passwordEncoder.encode("123456"));
        userRepository.save(user);

        redirectAttributes.addFlashAttribute("success",
                "Password reset to 123456 for " + user.getUsername());

        return "redirect:/staff";
    }

    @PostMapping("/{id}/toggle")
    public String toggleStatus(@PathVariable Long id,
            RedirectAttributes redirectAttributes) {

        User user = userRepository.findById(id).orElseThrow();

        user.setActive(!user.isActive());
        userRepository.save(user);

        String status = user.isActive() ? "activated" : "deactivated";
        redirectAttributes.addFlashAttribute("success",
                "User " + user.getUsername() + " " + status);

        return "redirect:/staff";
    }

@GetMapping("/{id}/active-staff")
@ResponseBody
public Map<String, Object> getActiveStaff(@PathVariable Long id) {
    Map<String, Object> response = new HashMap<>();
    log.info("=== GET ACTIVE STAFF CALLED for userId: {} ===", id);
    
    try {
        log.info("Looking for user with ID: {}", id);
        
        User userToDelete = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + id));
        
        log.info("Found user: {}, Shop ID: {}, Role: {}", 
            userToDelete.getUsername(), userToDelete.getShop().getId(), userToDelete.getRole());
        
        // Get other active staff in same shop
        List<User> allShopUsers = userRepository.findByShop(userToDelete.getShop());
        log.info("Total users in shop: {}", allShopUsers.size());
        
        // 🔴 FIXED: Filter out owners and the user being deleted
        List<Map<String, Object>> activeStaff = allShopUsers
                .stream()
                .filter(u -> {
                    // Not the same user, is active, and NOT an owner
                    boolean condition = !u.getId().equals(id) && 
                                       u.isActive() && 
                                       u.getRole() != Role.OWNER;  // 🔴 EXCLUDE OWNERS
                    
                    log.debug("User {}: Role={}, id match={}, active={}, condition={}", 
                        u.getUsername(), u.getRole(), !u.getId().equals(id), u.isActive(), condition);
                    return condition;
                })
                .map(u -> {
                    Map<String, Object> staff = new HashMap<>();
                    staff.put("id", u.getId());
                    staff.put("name", u.getName());
                    staff.put("role", u.getRole().name());
                    return staff;
                })
                .collect(Collectors.toList());
        
        log.info("Found {} active staff members to reassign to", activeStaff.size());
        
        response.put("success", true);
        response.put("activeStaff", activeStaff);
        
        if (activeStaff.isEmpty()) {
            response.put("message", "No other active staff members found");
        }
        
    } catch (Exception e) {
        log.error("Error getting active staff: {}", e.getMessage(), e);
        response.put("success", false);
        response.put("message", e.getMessage());
        response.put("error", e.toString());
    }
    
    log.info("Returning response: {}", response);
    return response;
}


    @GetMapping("/{id}/deactivate")
    public String deactivateUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setActive(false);
        userRepository.save(user);

        redirectAttributes.addFlashAttribute("warning",
                "User has been deactivated (no active staff to reassign sales)");

        return "redirect:/staff";
    }



@PostMapping("/{id}/delete")
public String deleteStaff(@PathVariable Long id,
                         @RequestParam(required = false) Long reassignToId,  // 🔴 Make optional
                         RedirectAttributes redirectAttributes,
                         Authentication authentication) {  // 🔴 Add Authentication

    // 🔴 Get current logged in user
    User currentUser = userRepository.findByUsername(authentication.getName())
            .orElseThrow(() -> new RuntimeException("User not found"));
    
    User userToDelete = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("User not found with ID: " + id));

    // 🔴 CHECK 1: Owner cannot delete himself
    if (currentUser.getId().equals(userToDelete.getId())) {
        redirectAttributes.addFlashAttribute("error", 
            "You cannot delete your own account! 🤦");
        return "redirect:/staff";
    }

    // 🔴 CHECK 2: Only owners can delete
    if (currentUser.getRole() != Role.OWNER) {
        redirectAttributes.addFlashAttribute("error", 
            "Access Denied: Only owners can delete staff");
        return "redirect:/staff";
    }

    // 🔴 CHECK 3: Cannot delete another owner
    if (userToDelete.getRole() == Role.OWNER) {
        redirectAttributes.addFlashAttribute("error", 
            "Cannot delete another owner account");
        return "redirect:/staff";
    }

    // 🔴 If no reassignToId, just deactivate (for users with no records)
    if (reassignToId == null) {
        userToDelete.setActive(false);
        userRepository.save(userToDelete);
        redirectAttributes.addFlashAttribute("warning", 
            "User has been deactivated (no reassignment needed)");
        return "redirect:/staff";
    }
    
    User reassignTo = userRepository.findById(reassignToId)
            .orElseThrow(() -> new RuntimeException("Target user not found with ID: " + reassignToId));

    // 🔴 CHECK 4: Cannot reassign to owner
    if (reassignTo.getRole() == Role.OWNER) {
        redirectAttributes.addFlashAttribute("error", 
            "Cannot reassign records to owner. Please select a staff member.");
        return "redirect:/staff";
    }

    try {
        // Reassign sales
        int salesReassigned = saleRepository.reassignSales(userToDelete, reassignTo);
        
        // Reassign tickets
        int ticketsReassigned = supportTicketRepository.reassignTickets(userToDelete, reassignTo);
        
        // Reassign replies
        int repliesReassigned = ticketReplyRepository.reassignReplies(userToDelete, reassignTo);

        // Now delete the user
        userRepository.delete(userToDelete);

        log.info("User deleted: {} → {} (Sales: {}, Tickets: {}, Replies: {})", 
            userToDelete.getUsername(), reassignTo.getUsername(),
            salesReassigned, ticketsReassigned, repliesReassigned);

        redirectAttributes.addFlashAttribute("success", 
            String.format("User deleted! %d sales, %d tickets reassigned to %s",
                salesReassigned, ticketsReassigned, reassignTo.getName()));

    } catch (Exception e) {
        log.error("Error deleting user: {}", e.getMessage());
        redirectAttributes.addFlashAttribute("error", 
            "Failed to delete user: " + e.getMessage());
    }

    return "redirect:/staff";
}

}