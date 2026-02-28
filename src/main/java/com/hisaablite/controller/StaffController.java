package com.hisaablite.controller;

import com.hisaablite.entity.Role;
import com.hisaablite.entity.Shop;
import com.hisaablite.entity.User;
import com.hisaablite.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/staff")
public class StaffController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // ===============================
    // LIST STAFF
    // ===============================
    @GetMapping
    public String listStaff(Model model, Authentication auth) {

        User owner = userRepository
                .findByUsername(auth.getName())
                .orElseThrow();

        Shop shop = owner.getShop();

        List<User> staff = userRepository.findByShop(shop);

        model.addAttribute("staffList", staff);

        return "staff-list";
    }

    // ===============================
    // ADD STAFF FORM
    // ===============================
    @GetMapping("/new")
    public String newStaffForm(Model model) {
        model.addAttribute("user", new User());
        return "staff-form";
    }

    // ===============================
    // SAVE STAFF
    // ===============================
@PostMapping
public String saveStaff(@ModelAttribute User user,
                        Authentication auth,
                        Model model) {

    User owner = userRepository
            .findByUsername(auth.getName())
            .orElseThrow();

    // Check duplicate username
    if (userRepository.findByUsername(user.getUsername()).isPresent()) {
        model.addAttribute("error", "Username already exists");
        return "staff-form";
    }

    // Check duplicate phone
    if (userRepository.findByPhone(user.getPhone()).isPresent()) {
        model.addAttribute("error", "Phone number already exists");
        return "staff-form";
    }

    user.setShop(owner.getShop());
    user.setActive(true);

    if (user.getRole() != Role.MANAGER &&
        user.getRole() != Role.CASHIER) {
        user.setRole(Role.CASHIER);
    }

    user.setPassword(passwordEncoder.encode(user.getPassword()));

    userRepository.save(user);

    return "redirect:/staff";
}
    // ===============================
    // CHANGE ROLE
    // ===============================
    @PostMapping("/{id}/role")
public String changeRole(@PathVariable Long id,
                         @RequestParam Role role) {

    User user = userRepository.findById(id).orElseThrow();

    if (role == Role.MANAGER || role == Role.CASHIER) {
        user.setRole(role);
        userRepository.save(user);
    }

    return "redirect:/staff";
}
    // ===============================
    // RESET PASSWORD
    // ===============================
    @PostMapping("/{id}/reset")
    public String resetPassword(@PathVariable Long id) {

        User user = userRepository.findById(id).orElseThrow();

        user.setPassword(passwordEncoder.encode("123456"));
        userRepository.save(user);

        return "redirect:/staff";
    }

    // ===============================
    // ACTIVATE / DEACTIVATE
    // ===============================
    @PostMapping("/{id}/toggle")
    public String toggleStatus(@PathVariable Long id) {

        User user = userRepository.findById(id).orElseThrow();

        user.setActive(!user.isActive());
        userRepository.save(user);

        return "redirect:/staff";
    }

    // ===============================
    // DELETE STAFF
    // ===============================
    @PostMapping("/{id}/delete")
    public String deleteStaff(@PathVariable Long id) {

        userRepository.deleteById(id);

        return "redirect:/staff";
    }
}