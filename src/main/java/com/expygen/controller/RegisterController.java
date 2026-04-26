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
            Model model) {

        if (userRepository.existsByUsername(request.getUsername())) {
            bindingResult.rejectValue("username", null, "Username already registered");
        }

        if (userRepository.existsByPhone(request.getPhone())) {
            bindingResult.rejectValue("phone", null, "Phone Number already registered");
        }

        if (bindingResult.hasErrors()) {
            return "register";
        }

        try {
            boolean verificationEmailSent = registrationService.registerShop(request);

            if (!verificationEmailSent) {
                model.addAttribute("success",
                        "Shop registered successfully, but we could not send the verification email right now. Please use resend verification after mail credentials are fixed.");
                model.addAttribute("registerRequest", new RegisterRequest());
                return "register";
            }
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage() != null && !e.getMessage().isBlank()
                    ? e.getMessage()
                    : "Registration completed partially, but verification email could not be sent. Please check email configuration and try again.");
            model.addAttribute("registerRequest", request);
            return "register";
        }

       

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
