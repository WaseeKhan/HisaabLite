package com.hisaablite.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/manager/products")
public class ManagerProductController {

    @GetMapping("/new")
    public String newProduct() {
        return "manager/product-form";
    }
}