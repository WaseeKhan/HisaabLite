package com.expygen.controller;

import org.springframework.ui.Model;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import com.expygen.service.PublicPricingService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final PublicPricingService publicPricingService;
    
    // Home Page
    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("publicPlans", publicPricingService.getActivePublicPlans());
        return "landing"; // landing page
    }

    
    // About Page
    @GetMapping("/about")
    public String about() {
        return "about"; // about.html template
    }
    
    // Features Page
    @GetMapping("/features")
    public String features() {
        return "features";
    }
    
    // Pricing Page
    @GetMapping("/pricing")
    public String pricing(Model model) {
        model.addAttribute("publicPlans", publicPricingService.getActivePublicPlans());
        return "pricing";
    }
    
    // Blog Page
    @GetMapping("/blog")
    public String blog() {
        return "blog";
    }

     // Contact Page
    // moved to ContactController 


    
    // Careers Page
    @GetMapping("/careers")
    public String careers() {
        return "careers";
    }

    // privacy Page
    @GetMapping("/privacy")
    public String privacy() {
        return "privacy";
    }

    // terms Page
    @GetMapping("/terms")
    public String terms() {
        return "terms";
    }


    
    @GetMapping("/workflow")
    public String workflow() {
        return "workflow";
    }

    @GetMapping("/how-it-works")
    public String howItWorksRedirect() {
        return "redirect:/workflow";
    }
    
   
    @GetMapping("/help")
    public String help() {
        return "help";
    }

}
