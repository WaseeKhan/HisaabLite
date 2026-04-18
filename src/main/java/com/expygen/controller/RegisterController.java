package com.expygen.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import com.expygen.dto.RegisterRequest;
import com.expygen.repository.UserRepository;
import com.expygen.service.RegistrationService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class RegisterController {

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
            HttpServletRequest httpRequest) {

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

       

        // Fetch the created shop to get QR code
        //disbleing whatsapp while registring
        // Shop shop = shopRepository.findByPanNumber(request.getPanNumber()).orElse(null);
        // if (shop != null && shop.getWhatsappQrCode() != null) {
        //     model.addAttribute("showQrModal", true);
        //     model.addAttribute("qrCode", shop.getWhatsappQrCode());
        //     model.addAttribute("shopName", shop.getName());
        //     model.addAttribute("instanceName", shop.getWhatsappInstanceName());
        // }

        model.addAttribute("success", "Shop registered successfully! Please check your email to verify your account.");
        model.addAttribute("registerRequest", new RegisterRequest());
        return "register";

        // Success message for display
        // model.addAttribute("success", "Shop registered successfully! You will be
        // redirected to login.");

        // changed msg
        // model.addAttribute("success", "Shop registered successfully! Please check your email to verify your account.");
        // Reset form
        // model.addAttribute("registerRequest", new RegisterRequest());

        // return "register";
    }

}
