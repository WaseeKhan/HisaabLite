package com.hisaablite.controller;

import com.hisaablite.dto.RegisterRequest;
import com.hisaablite.repository.ShopRepository;
import com.hisaablite.repository.UserRepository;
import com.hisaablite.service.RegistrationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;


@Controller
@RequiredArgsConstructor
public class RegisterController {

    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RegistrationService registrationService;

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("registerRequest", new RegisterRequest());
        return "register";
    }

   @PostMapping("/register")
    public String register(@Valid @ModelAttribute RegisterRequest request,
                       BindingResult bindingResult,
                       Model model) {

    if (shopRepository.existsByPanNumber(request.getPanNumber())) {
        bindingResult.rejectValue("panNumber", null, "PAN already registered");
    }

    if (userRepository.existsByUsername(request.getUsername())) {
        bindingResult.rejectValue("username", null, "Username already registered");
    }

    if (bindingResult.hasErrors()) {
        return "register";
    }

    registrationService.registerShop(request);

    // Success message for display
    model.addAttribute("success", "Shop registered successfully! You will be redirected to login.");

    // Reset form
    model.addAttribute("registerRequest", new RegisterRequest());

    return "register";
}

}