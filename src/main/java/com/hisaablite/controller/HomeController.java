package com.hisaablite.controller;

import com.hisaablite.entity.User;
import com.hisaablite.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final UserRepository userRepository;

    @GetMapping("/")
    public String home(Model model, Authentication authentication) {

        String username = authentication.getName();

        User user = userRepository.findByUsername(username).orElseThrow();

        model.addAttribute("username", user.getName());
        model.addAttribute("shopName", user.getShop().getName());
        model.addAttribute("role", user.getRole());

        return "dashboard";
    }
}