package com.hisaablite.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/cashier")
public class CashierController {

    @GetMapping("/billing")
    public String billingPage() {
        return "cashier/billing";
    }
}