package com.hisaablite.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/owner/products")
public class OwnerProductController {

    @GetMapping("/new")
    public String newProduct() {
        return "owner/product-form";
    }
}
