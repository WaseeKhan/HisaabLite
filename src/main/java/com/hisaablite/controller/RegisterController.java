package com.hisaablite.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import com.hisaablite.dto.RegisterRequest;
import com.hisaablite.repository.ShopRepository;
import com.hisaablite.repository.UserRepository;
import com.hisaablite.service.RegistrationService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class RegisterController {

    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final RegistrationService registrationService;

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("registerRequest", new RegisterRequest());
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute RegisterRequest request,
            BindingResult bindingResult,
            Model model,
            HttpServletRequest httpRequest
        ) {

        if (shopRepository.existsByPanNumber(request.getPanNumber())) {
            bindingResult.rejectValue("panNumber", null, "PAN already registered");
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            bindingResult.rejectValue("username", null, "Username already registered");
        }

         if (userRepository.existsByPhone(request.getPhone())) {
            bindingResult.rejectValue("phone", null, "Phone Number already registered");
        }

        if (bindingResult.hasErrors()) {
            return "register";
        }

        String appUrl = httpRequest.getRequestURL().toString()
        .replace(httpRequest.getServletPath(), "");

        registrationService.registerShop(request, appUrl);

        // Success message for display
        // model.addAttribute("success", "Shop registered successfully! You will be redirected to login.");
        
        //changed msg 
        model.addAttribute("success", "Shop registered successfully! Please check your email to verify your account.");
        // Reset form
        model.addAttribute("registerRequest", new RegisterRequest());

        return "register";
    }

}