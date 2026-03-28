package com.hisaablite.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {
    
    // Home Page
    @GetMapping("/")
    public String home() {
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
    public String pricing() {
        return "pricing";
    }
    
    // Blog Page
    @GetMapping("/blog")
    public String blog() {
        return "blog";
    }

     // Contact Page
    @GetMapping("/contact")
    public String contact() {
        return "contact"; // contact.html template
    }
    
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


    
    @GetMapping("/how-it-works")
    public String howItWorks() {
        return "how-it-works";
    }
    
   
    @GetMapping("/help")
    public String help() {
        return "help";
    }
}