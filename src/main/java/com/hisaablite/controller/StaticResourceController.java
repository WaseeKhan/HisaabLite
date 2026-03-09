package com.hisaablite.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;

@Controller
public class StaticResourceController {

    // Favicon handler - exactly waisa hi jaisa pehle tha
    @GetMapping("/favicon.png")
    @ResponseBody
    public ResponseEntity<Resource> getFavicon() {
        try {
            ClassPathResource resource = new ClassPathResource("static/favicon.png");
            if (resource.exists()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_PNG)
                        .body(resource);
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Chart.js handler - same pattern
    @GetMapping("/js/chart.min.js")
    @ResponseBody
    public ResponseEntity<Resource> getChartJs() {
        try {
            ClassPathResource resource = new ClassPathResource("static/js/chart.min.js");
            if (resource.exists()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.valueOf("application/javascript"))
                        .body(resource);
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Generic handler for any static file (optional)
    @GetMapping("/static/{folder}/{file}")
    @ResponseBody
    public ResponseEntity<Resource> getStaticFile(
            @PathVariable String folder,
            @PathVariable String file) {
        try {
            String path = "static/" + folder + "/" + file;
            ClassPathResource resource = new ClassPathResource(path);
            
            if (resource.exists()) {
                // Detect content type based on file extension
                MediaType mediaType = getMediaType(file);
                return ResponseEntity.ok()
                        .contentType(mediaType)
                        .body(resource);
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Helper to determine media type
    private MediaType getMediaType(String filename) {
        if (filename.endsWith(".js")) {
            return MediaType.valueOf("application/javascript");
        } else if (filename.endsWith(".css")) {
            return MediaType.valueOf("text/css");
        } else if (filename.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        } else if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        } else if (filename.endsWith(".gif")) {
            return MediaType.IMAGE_GIF;
        } else if (filename.endsWith(".svg")) {
            return MediaType.valueOf("image/svg+xml");
        } else if (filename.endsWith(".json")) {
            return MediaType.APPLICATION_JSON;
        } else {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}