package com.hisaablite.controller;

import com.hisaablite.dto.ShopProfileUpdateRequest;
import com.hisaablite.entity.Shop;
import com.hisaablite.entity.User;
import com.hisaablite.repository.UserRepository;
import com.hisaablite.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
@RequestMapping("/profile")
public class ProfileController {

    private final ShopService shopService;
    private final UserRepository userRepository;

    // GET PROFILE PAGE (Prefill data)
    @GetMapping
    @Transactional(readOnly = true)
    public String profilePage(Authentication authentication,
                              Model model) {

        User user = userRepository
                .findByUsername(authentication.getName())
                .orElseThrow();

        Shop shop = user.getShop();

        // Prefill DTO from DB
        ShopProfileUpdateRequest request = new ShopProfileUpdateRequest();
        request.setGstNumber(shop.getGstNumber());
        request.setAddress(shop.getAddress());
        request.setCity(shop.getCity());
        request.setState(shop.getState());
        request.setPincode(shop.getPincode());
        request.setUpiId(shop.getUpiId());

        model.addAttribute("shop", shop);
        model.addAttribute("profileRequest", request);

        return "profile";
    }

    // UPDATE PROFILE
    @PostMapping
    public String updateProfile(
            @ModelAttribute ShopProfileUpdateRequest request,
            Authentication authentication) {

        User user = userRepository
                .findByUsername(authentication.getName())
                .orElseThrow();

        shopService.updateProfile(user, request);

        return "redirect:/app";   // back to dashboard
    }
}