package com.hisaablite.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class FaviconController {

    @GetMapping("/favicon.png")
    @ResponseBody
    public ResponseEntity<Resource> getFaviconPng() {
        try {
            // Static folder 
            Resource resource = new ClassPathResource("static/favicon.png");
            
            if (resource.exists()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_PNG)
                        .header("Cache-Control", "no-cache")
                        .body(resource);
            }
            
            
            resource = new ClassPathResource("favicon.png");
            if (resource.exists()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_PNG)
                        .body(resource);
            }
            
        } catch (Exception e) {
            System.err.println("Favicon error: " + e.getMessage());
        }
        
        return ResponseEntity.notFound().build();
    }
    
    @GetMapping("/favicon.ico")
    @ResponseBody
    public ResponseEntity<Resource> getFaviconIco() {
        try {
            Resource resource = new ClassPathResource("static/favicon.ico");
            if (resource.exists()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.valueOf("image/x-icon"))
                        .body(resource);
            }
        } catch (Exception e) {
            System.err.println("Favicon error: " + e.getMessage());
        }
        return ResponseEntity.notFound().build();
    }
}