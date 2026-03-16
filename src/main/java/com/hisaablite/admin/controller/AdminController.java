package com.hisaablite.admin.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.hisaablite.admin.dto.AdminDashboardDTO;
import com.hisaablite.admin.dto.PopularPlanDTO;
import com.hisaablite.admin.dto.ShopDTO;
import com.hisaablite.admin.dto.SubscriptionPlanDTO;
import com.hisaablite.admin.repository.AdminShopRepository;
import com.hisaablite.admin.repository.AdminSubscriptionRepository;
import com.hisaablite.admin.repository.AdminUserRepository;
import com.hisaablite.admin.service.AdminService;
import com.hisaablite.entity.PlanType;
import com.hisaablite.entity.Role;
import com.hisaablite.entity.Shop;
import com.hisaablite.entity.SubscriptionPlan;
import com.hisaablite.entity.User;
import com.hisaablite.service.EmailService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final AdminService adminService;
    private final AdminUserRepository adminUserRepo;
    private final AdminShopRepository adminShopRepo;
    private final AdminSubscriptionRepository adminSubscriptionRepo;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;  // Added EmailService

    // ===== LOGIN PAGES =====

    @GetMapping("/login")
    public String loginPage(@RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "logout", required = false) String logout,
            Model model) {
        log.info("Accessing admin login page");
        if (error != null)
            model.addAttribute("error", "Invalid username or password");
        if (logout != null)
            model.addAttribute("message", "Logged out successfully");
        return "admin/login";
    }

    @PostMapping("/login")
    public String processLogin() {
        return "redirect:/admin/dashboard";
    }

    @GetMapping("/logout")
    public String logout() {
        log.info("Admin logout initiated");
        return "redirect:/admin/login?logout=true";
    }

    @GetMapping("/login-success")
    public String loginSuccess() {
        log.info("Admin login successful");
        return "redirect:/admin/dashboard";
    }

    // ===== DASHBOARD WITH ERROR HANDLING =====

    @GetMapping({ "", "/", "/dashboard" })
    public String dashboard(Model model) {
        log.info("Loading admin dashboard");
        try {
            AdminDashboardDTO stats = adminService.getDashboardStats();
            model.addAttribute("stats", stats);
            log.info("Dashboard loaded successfully: {} shops, {} users",
                    stats.getTotalShops(), stats.getTotalUsers());

            Map<String, Long> ticketStats = adminService.getTicketStats();
            stats.setTotalTickets(ticketStats.get("totalTickets"));
            stats.setOpenTickets(ticketStats.get("openTickets"));
            stats.setInProgressTickets(ticketStats.get("inProgressTickets"));
            stats.setResolvedTickets(ticketStats.get("resolvedTickets"));
            stats.setClosedTickets(ticketStats.get("closedTickets"));
            stats.setUrgentTickets(ticketStats.get("urgentTickets"));

            model.addAttribute("stats", stats);
            log.info("Dashboard loaded successfully: {} shops, {} users, {} tickets",
                    stats.getTotalShops(), stats.getTotalUsers(), stats.getTotalTickets());

        } catch (Exception e) {
            log.error("Error loading dashboard: {}", e.getMessage(), e);

            // Create a safe default stats object
            AdminDashboardDTO defaultStats = new AdminDashboardDTO();

            // Set default values
            defaultStats.setTotalShops(0L);
            defaultStats.setTotalUsers(0L);
            defaultStats.setActiveUsers(0L);
            defaultStats.setPendingApprovals(0L);
            defaultStats.setActiveShops(0L);
            defaultStats.setInactiveShops(0L);
            defaultStats.setTotalRevenue(0.0);

            // Create default popular plans
            List<PopularPlanDTO> defaultPlans = new ArrayList<>();
            defaultPlans.add(new PopularPlanDTO("FREE", 0L));
            defaultPlans.add(new PopularPlanDTO("BASIC", 0L));
            defaultPlans.add(new PopularPlanDTO("PREMIUM", 0L));
            defaultPlans.add(new PopularPlanDTO("ENTERPRISE", 0L));
            defaultStats.setPopularPlans(defaultPlans);

            // Set default chart data
            defaultStats.setRevenueLabels(getLast7DaysLabels());
            defaultStats.setRevenueData(getDefaultRevenueData());
            defaultStats.setUsersData(getDefaultUsersData());
            defaultStats.setPlanLabels(Arrays.asList("FREE", "BASIC", "PREMIUM", "ENTERPRISE"));
            defaultStats.setPlanData(Arrays.asList(0L, 0L, 0L, 0L));
            defaultStats.setPeriod("Last 7 days");

            defaultStats.setTotalTickets(0L);
            defaultStats.setOpenTickets(0L);
            defaultStats.setInProgressTickets(0L);
            defaultStats.setResolvedTickets(0L);
            defaultStats.setClosedTickets(0L);
            defaultStats.setUrgentTickets(0L);

            // Empty lists for recent items
            defaultStats.setRecentShops(new ArrayList<>());
            defaultStats.setRecentUsers(new ArrayList<>());

            model.addAttribute("stats", defaultStats);
            model.addAttribute("error", "Could not load live data. Showing default values.");
        }
        return "admin/dashboard";
    }

    private List<String> getLast7DaysLabels() {
        List<String> labels = new ArrayList<>();
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM");
        for (int i = 6; i >= 0; i--) {
            labels.add(today.minusDays(i).format(formatter));
        }
        return labels;
    }

    private List<Double> getDefaultRevenueData() {
        List<Double> data = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            data.add(0.0);
        }
        return data;
    }

    private List<Long> getDefaultUsersData() {
        List<Long> data = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            data.add(0L);
        }
        return data;
    }

    // ===== USER MANAGEMENT =====

    @GetMapping("/users")
    public String users(Model model,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search) {
        log.info("Loading admin users page - page: {}, size: {}, search: {}", page, size, search);

        try {
            // Create pageable object
            Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

            // Get paginated users with search if provided
            Page<User> userPage;
            if (search != null && !search.trim().isEmpty()) {
                userPage = adminUserRepo.searchUsers(search.trim(), pageable);
                log.info("Searching users with term: {}", search);
            } else {
                userPage = adminUserRepo.findAll(pageable);
            }

            // Get user statistics
            long totalUsers = adminUserRepo.count();
            long activeUsers = adminUserRepo.countByActiveTrue();
            long inactiveUsers = totalUsers - activeUsers;
            long pendingApprovals = adminUserRepo.countByApprovedFalse();

            // Add to model
            model.addAttribute("users", userPage.getContent());
            model.addAttribute("currentPage", userPage.getNumber());
            model.addAttribute("totalPages", userPage.getTotalPages());
            model.addAttribute("totalItems", userPage.getTotalElements());
            model.addAttribute("pageSize", size);
            model.addAttribute("search", search);

            // Stats
            model.addAttribute("totalUsers", totalUsers);
            model.addAttribute("activeUsers", activeUsers);
            model.addAttribute("inactiveUsers", inactiveUsers);
            model.addAttribute("pendingApprovals", pendingApprovals);

            log.info("Loaded {} users out of {} total", userPage.getNumberOfElements(), totalUsers);

        } catch (Exception e) {
            log.error("Error loading users: {}", e.getMessage(), e);
            model.addAttribute("users", new ArrayList<>());
            model.addAttribute("totalUsers", 0);
            model.addAttribute("activeUsers", 0);
            model.addAttribute("inactiveUsers", 0);
            model.addAttribute("pendingApprovals", 0);
            model.addAttribute("currentPage", 0);
            model.addAttribute("totalPages", 0);
            model.addAttribute("totalItems", 0);
            model.addAttribute("pageSize", size);
            model.addAttribute("error", "Could not load users: " + e.getMessage());
        }

        return "admin/users";
    }

   // ===== PENDING APPROVALS PAGE =====
@GetMapping("/users/pending-approvals")
public String pendingApprovals(Model model,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String search) {
    
    log.info("Loading pending approvals page - page: {}, size: {}, search: {}", page, size, search);
    
    Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").ascending());
    Page<User> pendingUsers;
    
    // Handle search if provided
    if (search != null && !search.trim().isEmpty()) {
        // You'll need to add this method to your repository
        pendingUsers = adminUserRepo.searchPendingUsers(search.trim(), pageable);
    } else {
        pendingUsers = adminUserRepo.findByActiveTrueAndApprovedFalse(pageable);
    }
    
    long totalPending = adminUserRepo.countByActiveTrueAndApprovedFalse();
    
    // Calculate additional stats
    long verifiedCount = pendingUsers.getTotalElements(); // All in this list are verified (active=true)
    long notVerifiedCount = 0; // You might want to calculate this separately
    long paidPlansCount = pendingUsers.getContent().stream()
            .filter(u -> u.getCurrentPlan() != null && u.getCurrentPlan() != PlanType.FREE)
            .count();
    
    model.addAttribute("users", pendingUsers.getContent());
    model.addAttribute("currentPage", pendingUsers.getNumber());
    model.addAttribute("totalPages", pendingUsers.getTotalPages());
    model.addAttribute("totalItems", pendingUsers.getTotalElements());
    model.addAttribute("pageSize", size);
    model.addAttribute("search", search);
    model.addAttribute("totalPending", totalPending);
    model.addAttribute("verifiedCount", verifiedCount);
    model.addAttribute("notVerifiedCount", notVerifiedCount);
    model.addAttribute("paidPlansCount", paidPlansCount);
    
    return "admin/pending-approvals";
}
    @GetMapping("/users/new")
    public String newUserForm(Model model) {
        log.info("Loading new user form");
        model.addAttribute("user", new User()); // empty user object
        model.addAttribute("shops", adminShopRepo.findAll()); // shops dropdown ke liye
        return "admin/user-form";
    }

    // users/save
    @PostMapping("/users/save")
    public String saveUser(@ModelAttribute User user,
            @RequestParam("shop.id") Long shopId,
            RedirectAttributes redirectAttributes) {
        log.info("Saving user: {}", user.getUsername());
        try {
            // Fetch shop from database
            Shop shop = adminShopRepo.findById(shopId)
                    .orElseThrow(() -> new RuntimeException("Shop not found with ID: " + shopId));
            user.setShop(shop);

            // Check if new user or existing
            if (user.getId() == null) {
                // New user
                if (user.getPassword() == null || user.getPassword().isEmpty()) {
                    user.setPassword(passwordEncoder.encode("hisaablite@123"));
                } else {
                    user.setPassword(passwordEncoder.encode(user.getPassword()));
                }
                user.setCreatedAt(LocalDateTime.now());
            } else {
                // Existing user
                User existingUser = adminUserRepo.findById(user.getId()).orElse(null);
                if (existingUser != null) {
                    if (user.getPassword() == null || user.getPassword().isEmpty()) {
                        user.setPassword(existingUser.getPassword());
                    } else {
                        user.setPassword(passwordEncoder.encode(user.getPassword()));
                    }
                    user.setCreatedAt(existingUser.getCreatedAt());
                }
            }

            adminUserRepo.save(user);
            redirectAttributes.addFlashAttribute("success", "User saved successfully");
        } catch (Exception e) {
            log.error("Error saving user: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Error saving user: " + e.getMessage());
        }
        return "redirect:/admin/users";
    }
    // users/save end

    @GetMapping("/users/{id}")
    public String userDetails(@PathVariable Long id, Model model) {
        log.info("Loading user details for id: {}", id);
        try {
            User user = adminUserRepo.findById(id).orElse(null);
            if (user == null) {
                return "redirect:/admin/users?error=User+not+found";
            }
            model.addAttribute("user", user);
        } catch (Exception e) {
            log.error("Error loading user details: {}", e.getMessage());
            return "redirect:/admin/users?error=Error+loading+user";
        }
        return "admin/user-details";
    }

    @GetMapping("/users/edit/{id}")
    public String editUser(@PathVariable Long id, Model model) {
        log.info("Editing user id: {}", id);
        try {
            User user = adminUserRepo.findById(id).orElse(null);
            if (user == null) {
                return "redirect:/admin/users?error=User+not+found";
            }
            model.addAttribute("user", user);
            model.addAttribute("shops", adminShopRepo.findAll());
        } catch (Exception e) {
            log.error("Error editing user: {}", e.getMessage());
            return "redirect:/admin/users?error=Error+loading+user";
        }
        return "admin/user-form";
    }

    @GetMapping("/users/delete/{id}")
    public String deleteUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("Deleting user id: {}", id);
        try {
            adminUserRepo.deleteById(id);
            redirectAttributes.addFlashAttribute("success", "User deleted successfully");
        } catch (Exception e) {
            log.error("Error deleting user: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Error deleting user: " + e.getMessage());
        }
        return "redirect:/admin/users";
    }

    // ===== ENHANCED APPROVE USER WITH EMAIL =====
    @PostMapping("/users/approve/{id}")
    @ResponseBody
    public Map<String, Object> approveUser(@PathVariable Long id) {
        log.info("API: Approving user ID: {}", id);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            User user = adminUserRepo.findById(id)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            // Check if user is active (email verified)
            if (!user.isActive()) {
                response.put("success", false);
                response.put("message", "User has not verified email yet. Cannot approve.");
                return response;
            }
            
            // Check if already approved
            if (user.isApproved()) {
                response.put("success", false);
                response.put("message", "User already approved");
                return response;
            }
            
            // Check if FREE plan (should be auto-approved)
            if (user.getCurrentPlan() == PlanType.FREE) {
                response.put("success", false);
                response.put("message", "FREE plan users are auto-approved during registration");
                return response;
            }
            
            // Fetch plan details from database
            SubscriptionPlan plan = adminSubscriptionRepo.findByPlanName(user.getCurrentPlan().name())
                    .orElseThrow(() -> new RuntimeException("Plan not found in database"));
            
            log.info("Approving user: {} with plan: {}", user.getUsername(), plan.getPlanName());
            log.info("Plan duration: {} days, Price: ₹{}", plan.getDurationInDays(), plan.getPrice());
            
            // Set approval
            user.setApproved(true);
            user.setApprovalDate(LocalDateTime.now());
            
            // Calculate expiry from approval date using plan duration
            if (plan.getDurationInDays() != null && plan.getDurationInDays() > 0) {
                LocalDateTime expiryDate = LocalDateTime.now().plusDays(plan.getDurationInDays());
                user.setSubscriptionEndDate(expiryDate);
                log.info("Subscription expiry set to: {}", expiryDate);
            }
            
            // Save user
            adminUserRepo.save(user);
            log.info("User approved and saved to database");
            
            // Update shop if owner
            if (user.getRole() == Role.OWNER && user.getShop() != null) {
                Shop shop = user.getShop();
                shop.setPlanType(user.getCurrentPlan());
                shop.setSubscriptionStartDate(user.getApprovalDate());
                shop.setSubscriptionEndDate(user.getSubscriptionEndDate());
                adminShopRepo.save(shop);
                log.info("Shop subscription updated for shop: {}", shop.getName());
            }
            
            // SEND APPROVAL EMAIL - This is the key part!
            try {
                emailService.sendApprovalEmail(user, plan);
                log.info("Approval email sent successfully to: {}", user.getUsername());
            } catch (Exception e) {
                log.error("Failed to send approval email but user was approved: {}", e.getMessage());
                // Don't fail the approval if email fails
            }
            
            response.put("success", true);
            response.put("message", "User approved successfully and email sent");
            response.put("userId", user.getId());
            response.put("userName", user.getName());
            response.put("userEmail", user.getUsername());
            response.put("planName", plan.getPlanName());
            response.put("expiryDate", user.getSubscriptionEndDate() != null ? 
                    user.getSubscriptionEndDate().toString() : "No expiry");
            
        } catch (Exception e) {
            log.error("Error approving user: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
        }
        
        return response;
    }

    // ===== GET APPROVAL EMAIL PREVIEW =====
    @GetMapping("/users/preview-approval-email/{id}")
    @ResponseBody
    public Map<String, Object> previewApprovalEmail(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            User user = adminUserRepo.findById(id)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            SubscriptionPlan plan = adminSubscriptionRepo.findByPlanName(user.getCurrentPlan().name())
                    .orElseThrow(() -> new RuntimeException("Plan not found"));
            
            response.put("success", true);
            response.put("userName", user.getName());
            response.put("userEmail", user.getUsername());
            response.put("planName", plan.getPlanName());
            response.put("planPrice", plan.getPrice());
            response.put("planDuration", plan.getDurationInDays());
            response.put("maxUsers", plan.getMaxUsers());
            response.put("maxProducts", plan.getMaxProducts());
            response.put("description", plan.getDescription());
            response.put("expiryDate", user.getSubscriptionEndDate() != null ? 
                    user.getSubscriptionEndDate().toString() : "Not set yet");
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
        }
        
        return response;
    }

    // ===== KEEP YOUR EXISTING GET APPROVE METHOD FOR REDIRECTS =====
 
@GetMapping("/users/approve/{id}")
@ResponseBody
public Map<String, Object> approveUserGet(@PathVariable Long id) {
    Map<String, Object> response = new HashMap<>();
    try {
        User user = adminUserRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        if (user.getCurrentPlan() == PlanType.FREE) {
            response.put("success", false);
            response.put("message", "FREE plan users are auto-approved");
            return response;
        }
        
        SubscriptionPlan plan = adminSubscriptionRepo.findByPlanName(user.getCurrentPlan().name())
                .orElseThrow(() -> new RuntimeException("Plan not found"));
        
        user.setApproved(true);
        user.setApprovalDate(LocalDateTime.now());
        
        if (plan.getDurationInDays() != null && plan.getDurationInDays() > 0) {
            user.setSubscriptionEndDate(LocalDateTime.now().plusDays(plan.getDurationInDays()));
        }
        
        adminUserRepo.save(user);
        
        if (user.getRole() == Role.OWNER && user.getShop() != null) {
            Shop shop = user.getShop();
            shop.setPlanType(user.getCurrentPlan());
            shop.setSubscriptionStartDate(user.getApprovalDate());
            shop.setSubscriptionEndDate(user.getSubscriptionEndDate());
            adminShopRepo.save(shop);
        }
        
        emailService.sendApprovalEmail(user, plan);
        
        response.put("success", true);
        response.put("message", "User approved successfully");
        
    } catch (Exception e) {
        log.error("Error approving user: {}", e.getMessage());
        response.put("success", false);
        response.put("message", "Error: " + e.getMessage());
    }
    return response;
}

    // ===== BULK APPROVE USERS =====
    @PostMapping("/users/bulk-approve")
    @ResponseBody
    public Map<String, Object> bulkApprove(@RequestBody List<Long> userIds) {
        log.info("Bulk approving {} users", userIds.size());
        
        Map<String, Object> response = new HashMap<>();
        List<Long> approved = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        
        for (Long userId : userIds) {
            try {
                User user = adminUserRepo.findById(userId).orElse(null);
                if (user != null && user.isActive() && !user.isApproved() && user.getCurrentPlan() != PlanType.FREE) {
                    
                    user.setApproved(true);
                    user.setApprovalDate(LocalDateTime.now());
                    
                    SubscriptionPlan plan = adminSubscriptionRepo.findByPlanName(user.getCurrentPlan().name())
                            .orElse(null);
                    
                    if (plan != null && plan.getDurationInDays() != null) {
                        user.setSubscriptionEndDate(LocalDateTime.now().plusDays(plan.getDurationInDays()));
                    }
                    
                    adminUserRepo.save(user);
                    
                    // Send email
                    if (plan != null) {
                        emailService.sendApprovalEmail(user, plan);
                    }
                    
                    approved.add(userId);
                } else if (user != null && user.getCurrentPlan() == PlanType.FREE) {
                    errors.add("User " + userId + " is FREE plan (auto-approved)");
                } else if (user != null && !user.isActive()) {
                    errors.add("User " + userId + " has not verified email");
                }
            } catch (Exception e) {
                errors.add("User " + userId + ": " + e.getMessage());
            }
        }
        
        response.put("success", true);
        response.put("approved", approved.size());
        response.put("approvedIds", approved);
        response.put("errors", errors);
        
        return response;
    }

    @GetMapping("/users/suspend/{id}")
    public String suspendUserGet(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("Suspending user id: {} via GET", id);
        try {
            User user = adminUserRepo.findById(id).orElse(null);
            if (user != null) {
                user.setActive(false);
                adminUserRepo.save(user);
                redirectAttributes.addFlashAttribute("success", "User suspended successfully");
            }
        } catch (Exception e) {
            log.error("Error suspending user: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Error suspending user");
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/activate/{id}")
    public String activateUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("Activating user id: {}", id);
        try {
            User user = adminUserRepo.findById(id).orElse(null);
            if (user != null) {
                user.setActive(true);
                adminUserRepo.save(user);
                redirectAttributes.addFlashAttribute("success", "User activated successfully");
            }
        } catch (Exception e) {
            log.error("Error activating user: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Error activating user");
        }
        return "redirect:/admin/users";
    }

    // ===== SHOP MANAGEMENT =====

    @GetMapping("/shops")
    public String shops(Model model,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search) {
        log.info("Loading admin shops page - page: {}, size: {}, search: {}", page, size, search);

        try {
            // Create pageable object
            Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

            // Get paginated shops with search if provided
            Page<Shop> shopPage;
            if (search != null && !search.trim().isEmpty()) {
              
                shopPage = adminShopRepo.searchShops(search.trim(), pageable);
                log.info("Searching shops with term: {}", search);
            } else {
                shopPage = adminShopRepo.findAll(pageable);
            }

            // Get shop statistics
            long totalShops = adminShopRepo.count();
            long activeShops = adminShopRepo.countByActive(true);
            long inactiveShops = totalShops - activeShops;

            // Add to model
            model.addAttribute("shops", shopPage.getContent());
            model.addAttribute("currentPage", shopPage.getNumber());
            model.addAttribute("totalPages", shopPage.getTotalPages());
            model.addAttribute("totalItems", shopPage.getTotalElements());
            model.addAttribute("pageSize", size);
            model.addAttribute("search", search);

            // Stats
            model.addAttribute("totalShops", totalShops);
            model.addAttribute("activeShops", activeShops);
            model.addAttribute("inactiveShops", inactiveShops);
            model.addAttribute("planStats", adminShopRepo.countShopsByPlanType().size());

            log.info("Loaded {} shops out of {} total", shopPage.getNumberOfElements(), totalShops);

        } catch (Exception e) {
            log.error("Error loading shops: {}", e.getMessage(), e);
            model.addAttribute("shops", new ArrayList<>());
            model.addAttribute("totalShops", 0);
            model.addAttribute("activeShops", 0);
            model.addAttribute("inactiveShops", 0);
            model.addAttribute("planStats", 0);
            model.addAttribute("currentPage", 0);
            model.addAttribute("totalPages", 0);
            model.addAttribute("totalItems", 0);
            model.addAttribute("pageSize", size);
            model.addAttribute("error", "Could not load shops: " + e.getMessage());
        }

        return "admin/shops";
    }

    @GetMapping("/shops/new")
    public String newShopForm(Model model) {
        log.info("Loading new shop form");
        model.addAttribute("shop", new ShopDTO());
        model.addAttribute("planTypes", PlanType.values());
        return "admin/shop-form";
    }

    @PostMapping("/shops/save")
    public String saveShop(@ModelAttribute Shop shop,
            @RequestParam(required = false) String ownerName,
            @RequestParam(required = false) String ownerEmail,
            @RequestParam(required = false) String ownerPhone,
            RedirectAttributes redirectAttributes) {

        log.info("Saving shop: {}", shop.getName());

        try {
            // ===== VALIDATION - Same as Customer =====
            List<String> errors = new ArrayList<>();

            // 1. Check PAN number uniqueness
            if (shop.getId() == null) {
                // New shop - check if PAN exists
                if (adminShopRepo.existsByPanNumber(shop.getPanNumber())) {
                    errors.add("PAN number already registered");
                }
            } else {
                // Edit shop - check if PAN exists for different shop
                Optional<Shop> existing = adminShopRepo.findByPanNumber(shop.getPanNumber());
                if (existing.isPresent() && !existing.get().getId().equals(shop.getId())) {
                    errors.add("PAN number already registered with another shop");
                }
            }

            // 2. GST validation (optional but if provided, should be unique)
            if (shop.getGstNumber() != null && !shop.getGstNumber().isEmpty()) {
                if (shop.getId() == null) {
                    if (adminShopRepo.existsByGstNumber(shop.getGstNumber())) {
                        errors.add("GST number already registered");
                    }
                } else {
                    Optional<Shop> existing = adminShopRepo.findByGstNumber(shop.getGstNumber());
                    if (existing.isPresent() && !existing.get().getId().equals(shop.getId())) {
                        errors.add("GST number already registered with another shop");
                    }
                }
            }

            // Basic validation
            if (shop.getName() == null || shop.getName().trim().isEmpty()) {
                errors.add("Shop name is required");
            }

            if (shop.getPanNumber() == null || shop.getPanNumber().trim().isEmpty()) {
                errors.add("PAN number is required");
            }

            // If there are errors, return to form
            if (!errors.isEmpty()) {
                redirectAttributes.addFlashAttribute("errors", errors);
                redirectAttributes.addFlashAttribute("shop", shop);
                return "redirect:/admin/shops/new";
            }

            // ===== SHOP CREATION =====

            // Set timestamps
            if (shop.getId() == null) {
                shop.setCreatedAt(LocalDateTime.now());
                log.info("Creating new shop: {}", shop.getName());
            } else {
                Optional<Shop> existingShop = adminShopRepo.findById(shop.getId());
                if (existingShop.isPresent()) {
                    shop.setCreatedAt(existingShop.get().getCreatedAt());
                    log.info("Updating shop: {} (ID: {})", shop.getName(), shop.getId());
                }
            }

            if (shop.getPlanType() == null) {
                shop.setPlanType(PlanType.FREE);
            }

            // Save shop
            Shop savedShop = adminShopRepo.save(shop);
            log.info("Shop saved successfully with ID: {}", savedShop.getId());

            if (ownerName != null && !ownerName.trim().isEmpty() &&
                    ownerEmail != null && !ownerEmail.trim().isEmpty() &&
                    ownerPhone != null && !ownerPhone.trim().isEmpty()) {

                // Check if user already exists
                if (!adminUserRepo.existsByUsername(ownerEmail) &&
                        !adminUserRepo.existsByPhone(ownerPhone)) {

                    User owner = User.builder()
                            .name(ownerName)
                            .username(ownerEmail)
                            .phone(ownerPhone)
                            .password(passwordEncoder.encode("hisaablite@123")) // Default password
                            .role(Role.OWNER)
                            .shop(savedShop)
                            .active(true)
                            .approved(true)
                            .createdAt(LocalDateTime.now())
                            .build();

                    adminUserRepo.save(owner);
                    log.info("Owner user created for shop: {}", savedShop.getName());
                } else {
                    log.warn("Owner user already exists with email: {} or phone: {}", ownerEmail, ownerPhone);
                }
            }

            redirectAttributes.addFlashAttribute("success", "Shop saved successfully");

        } catch (Exception e) {
            log.error("Error saving shop: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Error saving shop: " + e.getMessage());
            return "redirect:/admin/shops/new";
        }

        return "redirect:/admin/shops";
    }
    // save shop end here

    @GetMapping("/shops/{id}")
    public String shopDetails(@PathVariable Long id, Model model) {
        log.info("Loading shop details for id: {}", id);
        try {
            Shop shop = adminShopRepo.findById(id).orElse(null);
            if (shop == null) {
                return "redirect:/admin/shops?error=Shop+not+found";
            }
            model.addAttribute("shop", shop);
        } catch (Exception e) {
            log.error("Error loading shop details: {}", e.getMessage());
            return "redirect:/admin/shops?error=Error+loading+shop";
        }
        return "admin/shop-details";
    }

    @GetMapping("/shops/edit/{id}")
    public String editShop(@PathVariable Long id, Model model) {
        log.info("Editing shop id: {}", id);
        try {
            Shop shop = adminShopRepo.findById(id).orElse(null);
            if (shop == null) {
                return "redirect:/admin/shops?error=Shop+not+found";
            }
            model.addAttribute("shop", shop);
            model.addAttribute("planTypes", PlanType.values());
        } catch (Exception e) {
            log.error("Error editing shop: {}", e.getMessage());
            return "redirect:/admin/shops?error=Error+loading+shop";
        }
        return "admin/shop-form";
    }

    @GetMapping("/shops/delete/{id}")
    public String deleteShop(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("Deleting shop id: {}", id);
        try {
            adminShopRepo.deleteById(id);
            redirectAttributes.addFlashAttribute("success", "Shop deleted successfully");
        } catch (Exception e) {
            log.error("Error deleting shop: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Error deleting shop: " + e.getMessage());
        }
        return "redirect:/admin/shops";
    }

    @GetMapping("/shops/activate/{id}")
    public String activateShop(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("Activating shop id: {}", id);
        try {
            Shop shop = adminShopRepo.findById(id).orElse(null);
            if (shop != null) {
                shop.setActive(true);
                adminShopRepo.save(shop);
                redirectAttributes.addFlashAttribute("success", "Shop activated successfully");
            } else {
                redirectAttributes.addFlashAttribute("error", "Shop not found");
            }
        } catch (Exception e) {
            log.error("Error activating shop: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Error activating shop: " + e.getMessage());
        }
        return "redirect:/admin/shops";
    }

    @GetMapping("/shops/suspend/{id}")
    public String suspendShop(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("Suspending shop id: {}", id);
        try {
            Shop shop = adminShopRepo.findById(id).orElse(null);
            if (shop != null) {
                shop.setActive(false);
                adminShopRepo.save(shop);
                redirectAttributes.addFlashAttribute("success", "Shop suspended successfully");
            } else {
                redirectAttributes.addFlashAttribute("error", "Shop not found");
            }
        } catch (Exception e) {
            log.error("Error suspending shop: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Error suspending shop: " + e.getMessage());
        }
        return "redirect:/admin/shops";
    }

    @PostMapping("/shops/update-plan/{id}")
    public String updateShopPlan(@PathVariable Long id,
            @RequestParam PlanType planType,
            RedirectAttributes redirectAttributes) {
        log.info("Updating shop plan - id: {}, plan: {}", id, planType);
        try {
            Shop shop = adminShopRepo.findById(id).orElse(null);
            if (shop != null) {
                shop.setPlanType(planType);
                adminShopRepo.save(shop);
                redirectAttributes.addFlashAttribute("success", "Shop plan updated successfully");
            }
        } catch (Exception e) {
            log.error("Error updating shop plan: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Error updating plan");
        }
        return "redirect:/admin/shops";
    }

    // Subscription URL Start here

    // ===== SUBSCRIPTION MANAGEMENT =====

    @GetMapping("/subscriptions")
    public String subscriptions(Model model) {
        log.info("Loading admin subscriptions page");

        try {
            // Get total shops count
            long totalShops = adminShopRepo.count();

            // Get shop counts by plan type
            List<Object[]> shopStats = adminShopRepo.countShopsByPlanType();
            Map<String, Long> shopCountMap = new HashMap<>();

            for (Object[] stat : shopStats) {
                PlanType planType = (PlanType) stat[0];
                Long count = (Long) stat[1];
                shopCountMap.put(planType.name(), count);
            }

            // Get subscription plans from database
            List<SubscriptionPlan> dbPlans = adminSubscriptionRepo.findByActiveTrue();
            List<SubscriptionPlanDTO> plans = new ArrayList<>();

            if (!dbPlans.isEmpty()) {
                for (SubscriptionPlan plan : dbPlans) {
                    SubscriptionPlanDTO dto = convertToDTO(plan);

                    // Get shop count from the map
                    Long shopCount = shopCountMap.getOrDefault(plan.getPlanName().toUpperCase(), 0L);
                    dto.setShopCount(shopCount);

                    // Calculate percentage
                    double percentage = totalShops > 0 ? (shopCount * 100.0 / totalShops) : 0;
                    dto.setUsagePercent(Math.round(percentage * 10.0) / 10.0);

                    plans.add(dto);
                }
            } else {
                // Create default plans from enum
                for (PlanType planType : PlanType.values()) {
                    String planName = planType.name();
                    Long shopCount = shopCountMap.getOrDefault(planName, 0L);
                    double percentage = totalShops > 0 ? (shopCount * 100.0 / totalShops) : 0;

                    SubscriptionPlanDTO dto = createDefaultPlanDTO(planName, shopCount, percentage);
                    plans.add(dto);
                }
            }

            // Sort plans in logical order
            List<SubscriptionPlanDTO> sortedPlans = sortPlansByOrder(plans);

            model.addAttribute("plans", sortedPlans);

        } catch (Exception e) {
            log.error("Error loading subscription plans: {}", e.getMessage(), e);
            model.addAttribute("plans", getDefaultPlans());
            model.addAttribute("error", "Could not load live data. Showing default values.");
        }

        return "admin/subscriptions";
    }

    @GetMapping("/subscriptions/new")
    public String newSubscriptionForm(Model model) {
        log.info("Loading new subscription form");
        model.addAttribute("plan", new SubscriptionPlanDTO());
        model.addAttribute("planTypes", PlanType.values());
        return "admin/subscription-form";
    }

    @PostMapping("/subscriptions/save")
    public String saveSubscription(@ModelAttribute SubscriptionPlanDTO planDTO,
            RedirectAttributes redirectAttributes) {
        log.info("Saving subscription plan: {}", planDTO.getPlanName());

        try {
            // Convert DTO to Entity
            SubscriptionPlan plan = new SubscriptionPlan();

            if (planDTO.getId() != null) {

                Optional<SubscriptionPlan> existingPlan = adminSubscriptionRepo.findById(planDTO.getId());
                if (existingPlan.isPresent()) {
                    plan = existingPlan.get();
                }
            }

            // Set/Update fields
            plan.setPlanName(planDTO.getPlanName().toUpperCase());
            plan.setPrice(planDTO.getPrice());
            plan.setDurationInDays(planDTO.getDurationInDays());
            plan.setDescription(planDTO.getDescription());
            plan.setMaxUsers(planDTO.getMaxUsers());
            plan.setMaxProducts(planDTO.getMaxProducts());
            plan.setFeatures(planDTO.getFeatures());
            plan.setActive(planDTO.isActive());

            if (planDTO.getId() == null) {
                plan.setCreatedAt(LocalDateTime.now());
            }
            plan.setUpdatedAt(LocalDateTime.now());

            // Save to database
            adminSubscriptionRepo.save(plan);

            redirectAttributes.addFlashAttribute("success", "Subscription plan saved successfully");

        } catch (Exception e) {
            log.error("Error saving subscription plan: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Error saving plan: " + e.getMessage());
            return "redirect:/admin/subscriptions/new";
        }

        return "redirect:/admin/subscriptions";
    }

    @GetMapping("/subscriptions/edit/{planName}")
    public String editSubscription(@PathVariable String planName, Model model) {
        log.info("Editing subscription plan: {}", planName);

        try {

            Optional<SubscriptionPlan> planOpt = adminSubscriptionRepo.findByPlanName(planName.toUpperCase());

            SubscriptionPlanDTO planDTO;
            if (planOpt.isPresent()) {
                planDTO = convertToDTO(planOpt.get());
            } else {

                planDTO = createDefaultPlanDTO(planName, 0L, 0.0);
            }

            model.addAttribute("plan", planDTO);
            model.addAttribute("planTypes", PlanType.values());

        } catch (Exception e) {
            log.error("Error editing plan: {}", e.getMessage());
            model.addAttribute("plan", createDefaultPlanDTO(planName, 0L, 0.0));
            model.addAttribute("planTypes", PlanType.values());
            model.addAttribute("error", "Could not load plan details");
        }

        return "admin/subscription-form";
    }

    @GetMapping("/subscriptions/delete/{id}")
    public String deleteSubscription(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("Deleting subscription plan id: {}", id);

        try {
            adminSubscriptionRepo.deleteById(id);
            redirectAttributes.addFlashAttribute("success", "Subscription plan deleted successfully");
        } catch (Exception e) {
            log.error("Error deleting subscription plan: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Error deleting plan: " + e.getMessage());
        }

        return "redirect:/admin/subscriptions";
    }

    @PostMapping("/subscriptions/toggle/{id}")
    public String toggleSubscriptionStatus(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("Toggling subscription plan status for id: {}", id);

        try {
            Optional<SubscriptionPlan> planOpt = adminSubscriptionRepo.findById(id);
            if (planOpt.isPresent()) {
                SubscriptionPlan plan = planOpt.get();
                plan.setActive(!plan.isActive());
                plan.setUpdatedAt(LocalDateTime.now());
                adminSubscriptionRepo.save(plan);

                String status = plan.isActive() ? "activated" : "deactivated";
                redirectAttributes.addFlashAttribute("success", "Plan " + status + " successfully");
            }
        } catch (Exception e) {
            log.error("Error toggling plan status: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Error updating plan status");
        }

        return "redirect:/admin/subscriptions";
    }

    // ===== HELPER METHODS =====

    private SubscriptionPlanDTO convertToDTO(SubscriptionPlan plan) {
        SubscriptionPlanDTO dto = new SubscriptionPlanDTO(
                plan.getPlanName(),
                plan.getPrice(),
                plan.getDurationInDays(),
                plan.getDescription(),
                plan.getMaxUsers(),
                plan.getMaxProducts());
        dto.setId(plan.getId());
        dto.setFeatures(plan.getFeatures());
        dto.setActive(plan.isActive());
        return dto;
    }

    private SubscriptionPlanDTO createDefaultPlanDTO(String planName, Long shopCount, Double percentage) {
        SubscriptionPlanDTO dto;
        switch (planName.toUpperCase()) {
            case "FREE":
                dto = new SubscriptionPlanDTO("FREE", 0.0, 30, "Basic features for small businesses", 2, 50);
                break;
            case "BASIC":
                dto = new SubscriptionPlanDTO("BASIC", 499.0, 30, "Standard features for growing businesses", 5, 200);
                break;
            case "PREMIUM":
                dto = new SubscriptionPlanDTO("PREMIUM", 999.0, 30, "Advanced features for established businesses", 15,
                        1000);
                break;
            case "ENTERPRISE":
                dto = new SubscriptionPlanDTO("ENTERPRISE", 1999.0, 30, "All features for large enterprises", -1, -1);
                break;
            default:
                dto = new SubscriptionPlanDTO(planName, 0.0, 30, "", 0, 0);
        }
        dto.setShopCount(shopCount != null ? shopCount : 0L);
        dto.setUsagePercent(percentage != null ? percentage : 0.0);
        dto.setActive(true);
        return dto;
    }

    private List<SubscriptionPlanDTO> getDefaultPlans() {
        List<SubscriptionPlanDTO> defaultPlans = new ArrayList<>();
        defaultPlans.add(createDefaultPlanDTO("FREE", 0L, 0.0));
        defaultPlans.add(createDefaultPlanDTO("BASIC", 0L, 0.0));
        defaultPlans.add(createDefaultPlanDTO("PREMIUM", 0L, 0.0));
        defaultPlans.add(createDefaultPlanDTO("ENTERPRISE", 0L, 0.0));
        return defaultPlans;
    }

    private List<SubscriptionPlanDTO> sortPlansByOrder(List<SubscriptionPlanDTO> plans) {
        List<SubscriptionPlanDTO> sortedPlans = new ArrayList<>();
        String[] order = { "FREE", "BASIC", "PREMIUM", "ENTERPRISE" };

        for (String planName : order) {
            for (SubscriptionPlanDTO plan : plans) {
                if (plan.getPlanName().equalsIgnoreCase(planName)) {
                    sortedPlans.add(plan);
                    break;
                }
            }
        }

        for (SubscriptionPlanDTO plan : plans) {
            if (!sortedPlans.contains(plan)) {
                sortedPlans.add(plan);
            }
        }
        return sortedPlans;
    }

    // Subscription URLs end here

    // ===== API ENDPOINTS =====

    @GetMapping("/api/stats")
    @ResponseBody
    public AdminDashboardDTO getDashboardStats() {
        return adminService.getDashboardStats();
    }

    @GetMapping("/api/users")
    @ResponseBody
    public List<User> getAllUsers() {
        return adminUserRepo.findAll();
    }

    @GetMapping("/api/shops")
    @ResponseBody
    public List<Shop> getAllShops() {
        return adminShopRepo.findAll();
    }

    @GetMapping("/api/shops/plan-stats")
    @ResponseBody
    public List<Object[]> getPlanStats() {
        return adminShopRepo.countShopsByPlanType();
    }

    // ===== ERROR PAGES =====

    @GetMapping("/access-denied")
    public String accessDenied() {
        return "admin/access-denied";
    }

    @GetMapping("/404")
    public String notFound() {
        return "admin/404";
    }
}