package com.expygen.controller;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Controller
public class ErrorPageController implements ErrorController {

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, HttpServletResponse response, Model model) {
        Object statusCode = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = statusCode != null ? Integer.parseInt(statusCode.toString()) : HttpStatus.INTERNAL_SERVER_ERROR.value();
        String originalUri = resolveOriginalUri(request);
        boolean adminRequest = originalUri != null && originalUri.startsWith("/admin");

        if (status == HttpStatus.NOT_FOUND.value()) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            model.addAttribute("message", "Page not found.");
            return adminRequest ? "admin/error/404" : "error/404";
        }

        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        model.addAttribute("message", "Something went wrong. Please try again.");
        return adminRequest ? "admin/error/500" : "error/500";
    }

    private String resolveOriginalUri(HttpServletRequest request) {
        Object errorUri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        if (errorUri != null) {
            return errorUri.toString();
        }
        return request.getRequestURI();
    }
}
