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

    // ===== IMAGE HANDLERS - ADD THESE =====
    
    // Career page images
    @GetMapping("/images/{imageName}")
    @ResponseBody
    public ResponseEntity<Resource> getImage(@PathVariable String imageName) {
        try {
            String path = "static/images/" + imageName;
            ClassPathResource resource = new ClassPathResource(path);
            
            if (resource.exists()) {
                MediaType mediaType = getImageMediaType(imageName);
                return ResponseEntity.ok()
                        .contentType(mediaType)
                        .body(resource);
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Specific handlers for career images (optional but clean)
    @GetMapping("/images/teamouting.jpg")
    @ResponseBody
    public ResponseEntity<Resource> getTeamOuting() {
        return getImage("teamouting.jpg");
    }

    @GetMapping("/images/remotework.jpg")
    @ResponseBody
    public ResponseEntity<Resource> getRemoteWork() {
        return getImage("remotework.jpg");
    }

    @GetMapping("/images/learning.jpg")
    @ResponseBody
    public ResponseEntity<Resource> getLearning() {
        return getImage("learning.jpg");
    }

    @GetMapping("/images/image1.jpeg")
    @ResponseBody
    public ResponseEntity<Resource> getImage1() {
        return getImage("image1.jpeg");
    }

    @GetMapping("/images/image2.jpeg")
    @ResponseBody
    public ResponseEntity<Resource> getImage2() {
        return getImage("image2.jpeg");
    }

    @GetMapping("/images/image3.jpeg")
    @ResponseBody
    public ResponseEntity<Resource> getImage3() {
        return getImage("image3.jpeg");
    }

    // Generic handler for any static file
    @GetMapping("/static/{folder}/{file}")
    @ResponseBody
    public ResponseEntity<Resource> getStaticFile(
            @PathVariable String folder,
            @PathVariable String file) {
        try {
            String path = "static/" + folder + "/" + file;
            ClassPathResource resource = new ClassPathResource(path);
            
            if (resource.exists()) {
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

    // Helper to determine image media type
    private MediaType getImageMediaType(String filename) {
        if (filename.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        } else if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        } else if (filename.endsWith(".gif")) {
            return MediaType.IMAGE_GIF;
        } else if (filename.endsWith(".svg")) {
            return MediaType.valueOf("image/svg+xml");
        } else if (filename.endsWith(".webp")) {
            return MediaType.valueOf("image/webp");
        } else {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    // Original helper for other file types
    private MediaType getMediaType(String filename) {
        if (filename.endsWith(".js")) {
            return MediaType.valueOf("application/javascript");
        } else if (filename.endsWith(".css")) {
            return MediaType.valueOf("text/css");
        } else if (filename.endsWith(".json")) {
            return MediaType.APPLICATION_JSON;
        } else {
            return getImageMediaType(filename); // Reuse image helper
        }
    }
}