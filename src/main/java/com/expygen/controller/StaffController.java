package com.expygen.controller;

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

import com.expygen.entity.Role;
import com.expygen.entity.Shop;
import com.expygen.entity.User;
import com.expygen.admin.service.AuditService;
import com.expygen.repository.UserRepository;
import com.expygen.repository.SaleRepository;
import com.expygen.repository.SupportTicketRepository;
import com.expygen.repository.TicketReplyRepository;
import com.expygen.service.PlanLimitService;
import com.expygen.service.SubscriptionAccessService;
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
    private final AuditService auditService;
    private final SubscriptionAccessService subscriptionAccessService;

    @GetMapping
    public String listStaff(Model model, Authentication auth) {
        User owner = userRepository
                .findByUsername(auth.getName())
                .orElseThrow();
        
        User user = userRepository
                .findByUsername(auth.getName())
                .orElseThrow();
        
        Shop shop = owner.getShop();
        List<User> staff = userRepository.findByShop(shop);
        
        model.addAttribute("shop", shop);
        
        model.addAttribute("planType", subscriptionAccessService.getPlanName(shop));
        
        model.addAttribute("staffList", staff);
        model.addAttribute("role", owner.getRole().name());
        model.addAttribute("user", user);
        model.addAttribute("usageStats", planLimitService.getUsageStats(shop));
        model.addAttribute("currentPage", "staff");
        
        return "staff-list";
    }

    @GetMapping("/new")
    public String newStaffForm(Model model, Authentication authentication) {
        User owner = userRepository
                .findByUsername(authentication.getName())
                .orElseThrow();

        Shop shop = owner.getShop();
        
        if (!planLimitService.canAddUser(shop)) {
            model.addAttribute("error",
                    "User limit reached! Your " + shop.getPlanType() +
                            " plan allows maximum " + planLimitService.getUserLimit(shop) + " users.");
            
            List<User> staff = userRepository.findByShop(shop);
            
            model.addAttribute("shop", shop);
            model.addAttribute("staffList", staff);
            model.addAttribute("role", owner.getRole().name());
            model.addAttribute("currentPage", "staff");
            model.addAttribute("user", owner);
            
            model.addAttribute("planType", subscriptionAccessService.getPlanName(shop));
            
            model.addAttribute("usageStats", planLimitService.getUsageStats(shop));
            
            return "staff-list";
        }

        // Create new user object for the form
        User newUser = new User();
        newUser.setShop(shop);
        newUser.setActive(true);
        
        model.addAttribute("user", newUser);
        model.addAttribute("authUser", owner);
        model.addAttribute("shop", shop);
        model.addAttribute("planType", subscriptionAccessService.getPlanName(shop));
        model.addAttribute("role", owner.getRole().name());
        model.addAttribute("currentPage", "staff");
        
        return "staff-form";
    }

    // ========== EDIT STAFF METHOD - ADD THIS ==========
    @GetMapping("/edit/{id}")
    public String editStaffForm(@PathVariable Long id, Model model, Authentication authentication,
            RedirectAttributes redirectAttributes) {
        User owner = userRepository
                .findByUsername(authentication.getName())
                .orElseThrow();

        User staff = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Staff not found"));

        // Verify staff belongs to owner's shop
        if (!staff.getShop().getId().equals(owner.getShop().getId())) {
            redirectAttributes.addFlashAttribute("error",
                    "Access Denied: You can manage staff only within your own shop");
            return "redirect:/staff";
        }

        if (staff.getRole() == Role.OWNER) {
            redirectAttributes.addFlashAttribute("error",
                    "Owner account can only be managed from admin portal");
            return "redirect:/staff";
        }

        Shop shop = owner.getShop();
        model.addAttribute("user", staff);
        model.addAttribute("authUser", owner);
        model.addAttribute("shop", shop);
        model.addAttribute("planType", subscriptionAccessService.getPlanName(shop));
        model.addAttribute("role", owner.getRole().name());
        model.addAttribute("currentPage", "staff");
        
        return "staff-form";
    }

    @PostMapping("/save")
    public String saveStaff(@ModelAttribute User user,
            Authentication auth,
            RedirectAttributes redirectAttributes,
            Model model) {

        User owner = userRepository
                .findByUsername(auth.getName())
                .orElseThrow();

        Shop shop = owner.getShop();

        // Check if this is an update or new staff
        boolean isUpdate = user.getId() != null;
        
        if (!isUpdate && !planLimitService.canAddUser(shop)) {
            redirectAttributes.addFlashAttribute("error",
                    "User limit reached! Your " + shop.getPlanType() +
                            " plan allows maximum " + planLimitService.getUserLimit(shop) + " users.");
            return "redirect:/staff";
        }

        // Check email uniqueness (exclude current user if updating)
        User existingByEmail = userRepository.findByUsername(user.getUsername()).orElse(null);
        if (existingByEmail != null && (isUpdate ? !existingByEmail.getId().equals(user.getId()) : true)) {
            redirectAttributes.addFlashAttribute("error", "Email already exists");
            return "redirect:/staff";
        }

        // Check phone uniqueness (exclude current user if updating)
        User existingByPhone = userRepository.findByPhone(user.getPhone()).orElse(null);
        if (existingByPhone != null && (isUpdate ? !existingByPhone.getId().equals(user.getId()) : true)) {
            redirectAttributes.addFlashAttribute("error", "Phone number already exists");
            return "redirect:/staff";
        }

        if (isUpdate) {
            // UPDATE EXISTING STAFF
            User existingStaff = userRepository.findById(user.getId())
                    .orElseThrow(() -> new RuntimeException("Staff not found"));
            Map<String, Object> oldStaffState = snapshotStaff(existingStaff);
            
            // Update fields
            existingStaff.setName(user.getName());
            existingStaff.setUsername(user.getUsername());
            existingStaff.setPhone(user.getPhone());
            existingStaff.setRole(user.getRole());
            
            // Update password only if provided
            if (user.getPassword() != null && !user.getPassword().trim().isEmpty()) {
                existingStaff.setPassword(passwordEncoder.encode(user.getPassword()));
            }
            
            userRepository.save(existingStaff);
            auditService.logAction(
                    owner.getUsername(),
                    owner.getRole().name(),
                    shop,
                    "STAFF_UPDATED",
                    "User",
                    existingStaff.getId(),
                    "SUCCESS",
                    oldStaffState,
                    snapshotStaff(existingStaff),
                    "Staff member updated");
            
            log.info("Staff updated: {} with role {} for shop {}",
                    existingStaff.getUsername(), existingStaff.getRole(), shop.getName());
            
            redirectAttributes.addFlashAttribute("success", 
                    "Staff member updated successfully!");
            
        } else {
            // CREATE NEW STAFF
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
            auditService.logAction(
                    owner.getUsername(),
                    owner.getRole().name(),
                    shop,
                    "STAFF_CREATED",
                    "User",
                    user.getId(),
                    "SUCCESS",
                    null,
                    snapshotStaff(user),
                    "Staff member created");

            log.info("Staff created: {} with role {} for shop {}",
                    user.getUsername(), user.getRole(), shop.getName());
            
            redirectAttributes.addFlashAttribute("success", 
                    "Staff member added successfully!");
        }

        return "redirect:/staff";
    }

    @PostMapping("/{id}/role")
    public String changeRole(@PathVariable Long id,
            @RequestParam Role role,
            RedirectAttributes redirectAttributes,
            Authentication authentication) {

        User user = userRepository.findById(id).orElseThrow();
        User actingUser = userRepository.findByUsername(authentication.getName()).orElseThrow();
        Map<String, Object> oldStaffState = snapshotStaff(user);

        if (!user.getShop().getId().equals(actingUser.getShop().getId()) || user.getRole() == Role.OWNER) {
            redirectAttributes.addFlashAttribute("error",
                    "Owner account can only be managed from admin portal");
            return "redirect:/staff";
        }

        if (role == Role.MANAGER || role == Role.CASHIER) {
            user.setRole(role);
            userRepository.save(user);
            auditService.logAction(
                    actingUser.getUsername(),
                    actingUser.getRole().name(),
                    actingUser.getShop(),
                    "STAFF_ROLE_CHANGED",
                    "User",
                    user.getId(),
                    "SUCCESS",
                    oldStaffState,
                    snapshotStaff(user),
                    "Staff role changed");
            log.info("Role changed for user {} to {}", user.getUsername(), role);
            redirectAttributes.addFlashAttribute("success", 
                    "Role changed to " + role + " for " + user.getName());
        }

        return "redirect:/staff";
    }

    @PostMapping("/{id}/reset")
    public String resetPassword(@PathVariable Long id,
            RedirectAttributes redirectAttributes,
            Authentication authentication) {

        User user = userRepository.findById(id).orElseThrow();
        User actingUser = userRepository.findByUsername(authentication.getName()).orElseThrow();

        if (!user.getShop().getId().equals(actingUser.getShop().getId()) || user.getRole() == Role.OWNER) {
            redirectAttributes.addFlashAttribute("error",
                    "Owner account can only be managed from admin portal");
            return "redirect:/staff";
        }

        user.setPassword(passwordEncoder.encode("123456"));
        userRepository.save(user);
        auditService.logAction(
                actingUser.getUsername(),
                actingUser.getRole().name(),
                actingUser.getShop(),
                "STAFF_PASSWORD_RESET",
                "User",
                user.getId(),
                "SUCCESS",
                null,
                Map.of("username", user.getUsername()),
                "Staff password reset to default by owner/manager");

        redirectAttributes.addFlashAttribute("success",
                "Password reset to 123456 for " + user.getUsername());

        return "redirect:/staff";
    }

    @PostMapping("/{id}/toggle")
    public String toggleStatus(@PathVariable Long id,
            RedirectAttributes redirectAttributes,
            Authentication authentication) {

        User user = userRepository.findById(id).orElseThrow();
        User actingUser = userRepository.findByUsername(authentication.getName()).orElseThrow();
        Map<String, Object> oldStaffState = snapshotStaff(user);

        if (!user.getShop().getId().equals(actingUser.getShop().getId()) || user.getRole() == Role.OWNER) {
            redirectAttributes.addFlashAttribute("error",
                    "Owner account can only be managed from admin portal");
            return "redirect:/staff";
        }

        user.setActive(!user.isActive());
        userRepository.save(user);
        auditService.logAction(
                actingUser.getUsername(),
                actingUser.getRole().name(),
                actingUser.getShop(),
                "STAFF_STATUS_TOGGLED",
                "User",
                user.getId(),
                "SUCCESS",
                oldStaffState,
                snapshotStaff(user),
                "Staff status changed");

        String status = user.isActive() ? "activated" : "deactivated";
        redirectAttributes.addFlashAttribute("success",
                "User " + user.getUsername() + " " + status);

        return "redirect:/staff";
    }

    @GetMapping("/{id}/active-staff")
    @ResponseBody
    public Map<String, Object> getActiveStaff(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        log.debug("Loading active staff list for reassignment, userId={}", id);
        
        try {
            log.debug("Looking up user {}", id);
            
            User userToDelete = userRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("User not found with ID: " + id));

            if (userToDelete.getRole() == Role.OWNER) {
                response.put("success", false);
                response.put("message", "Owner account cannot be deleted from shop panel");
                return response;
            }
            
            log.debug("Found user: {}, shopId: {}, role: {}", 
                userToDelete.getUsername(), userToDelete.getShop().getId(), userToDelete.getRole());
            
            List<User> allShopUsers = userRepository.findByShop(userToDelete.getShop());
            log.debug("Total users in shop: {}", allShopUsers.size());
            
            List<Map<String, Object>> activeStaff = allShopUsers
                    .stream()
                    .filter(u -> {
                        boolean condition = !u.getId().equals(id) && 
                                           u.isActive() && 
                                           u.getRole() != Role.OWNER;  
                        
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
            
            log.debug("Found {} active staff members to reassign to", activeStaff.size());
            
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
        
        log.debug("Returning active staff response: {}", response);
        return response;
    }

    @GetMapping("/{id}/deactivate")
    public String deactivateUser(@PathVariable Long id, RedirectAttributes redirectAttributes,
            Authentication authentication) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        User actingUser = userRepository.findByUsername(authentication.getName()).orElseThrow();
        Map<String, Object> oldStaffState = snapshotStaff(user);

        user.setActive(false);
        userRepository.save(user);
        auditService.logAction(
                actingUser.getUsername(),
                actingUser.getRole().name(),
                actingUser.getShop(),
                "STAFF_DEACTIVATED",
                "User",
                user.getId(),
                "SUCCESS",
                oldStaffState,
                snapshotStaff(user),
                "Staff deactivated without reassignment");

        redirectAttributes.addFlashAttribute("warning",
                "User has been deactivated (no active staff to reassign sales)");

        return "redirect:/staff";
    }

    @PostMapping("/{id}/delete")
    public String deleteStaff(@PathVariable Long id,
                             @RequestParam(required = false) Long reassignToId,  
                             RedirectAttributes redirectAttributes,
                             Authentication authentication) {  

        User currentUser = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        User userToDelete = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + id));

        if (!userToDelete.getShop().getId().equals(currentUser.getShop().getId())) {
            redirectAttributes.addFlashAttribute("error",
                "Access Denied: You can manage staff only within your own shop");
            return "redirect:/staff";
        }

        if (currentUser.getId().equals(userToDelete.getId())) {
            redirectAttributes.addFlashAttribute("error", 
                "You cannot delete your own account! 🤦");
            return "redirect:/staff";
        }

        if (currentUser.getRole() != Role.OWNER) {
            redirectAttributes.addFlashAttribute("error", 
                "Access Denied: Only owners can delete staff");
            return "redirect:/staff";
        }

        if (userToDelete.getRole() == Role.OWNER) {
            redirectAttributes.addFlashAttribute("error", 
                "Owner account can only be deleted from admin portal");
            return "redirect:/staff";
        }

        if (reassignToId == null) {
            Map<String, Object> oldStaffState = snapshotStaff(userToDelete);
            userToDelete.setActive(false);
            userRepository.save(userToDelete);
            auditService.logAction(
                    currentUser.getUsername(),
                    currentUser.getRole().name(),
                    currentUser.getShop(),
                    "STAFF_DEACTIVATED",
                    "User",
                    userToDelete.getId(),
                    "SUCCESS",
                    oldStaffState,
                    snapshotStaff(userToDelete),
                    "Staff deactivated without reassignment during delete flow");
            redirectAttributes.addFlashAttribute("warning", 
                "User has been deactivated (no reassignment needed)");
            return "redirect:/staff";
        }
        
        User reassignTo = userRepository.findById(reassignToId)
                .orElseThrow(() -> new RuntimeException("Target user not found with ID: " + reassignToId));

        if (!reassignTo.getShop().getId().equals(currentUser.getShop().getId())) {
            redirectAttributes.addFlashAttribute("error",
                "Please select a staff member from your own shop.");
            return "redirect:/staff";
        }

        if (reassignTo.getRole() == Role.OWNER) {
            redirectAttributes.addFlashAttribute("error", 
                "Cannot reassign records to owner. Please select a staff member.");
            return "redirect:/staff";
        }

        try {
            Map<String, Object> deletedStaffState = snapshotStaff(userToDelete);
            int salesReassigned = saleRepository.reassignSales(userToDelete, reassignTo);
            int ticketsReassigned = supportTicketRepository.reassignTickets(userToDelete, reassignTo);
            int repliesReassigned = ticketReplyRepository.reassignReplies(userToDelete, reassignTo);

            userRepository.delete(userToDelete);
            auditService.logAction(
                    currentUser.getUsername(),
                    currentUser.getRole().name(),
                    currentUser.getShop(),
                    "STAFF_DELETED",
                    "User",
                    userToDelete.getId(),
                    "SUCCESS",
                    deletedStaffState,
                    Map.of(
                            "reassignedToId", reassignTo.getId(),
                            "reassignedToUsername", reassignTo.getUsername(),
                            "salesReassigned", salesReassigned,
                            "ticketsReassigned", ticketsReassigned,
                            "repliesReassigned", repliesReassigned),
                    "Staff deleted and records reassigned");

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

    private Map<String, Object> snapshotStaff(User user) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("id", user.getId());
        snapshot.put("name", user.getName());
        snapshot.put("username", user.getUsername());
        snapshot.put("phone", user.getPhone());
        snapshot.put("role", user.getRole() != null ? user.getRole().name() : null);
        snapshot.put("active", user.isActive());
        snapshot.put("approved", user.isApproved());
        snapshot.put("shopId", user.getShop() != null ? user.getShop().getId() : null);
        return snapshot;
    }
}
