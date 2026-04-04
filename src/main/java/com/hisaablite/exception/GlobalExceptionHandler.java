package com.hisaablite.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.ui.Model;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Validation errors
    @ExceptionHandler(BindException.class)
    public String handleValidationException(BindException ex, Model model, HttpServletRequest request) {
        log.warn("Validation error on {}", request.getRequestURI(), ex);
        model.addAttribute("error", "Please check the form and try again.");
        return resolveViewForRequest(request);
    }

    // Duplicate custom errors
    @ExceptionHandler(DuplicateResourceException.class)
    public String handleDuplicate(DuplicateResourceException ex, Model model, HttpServletRequest request) {
        log.warn("Duplicate resource on {}", request.getRequestURI(), ex);
        model.addAttribute("error", ex.getMessage());
        return resolveViewForRequest(request);
    }

    // Database constraint
    @ExceptionHandler(DataIntegrityViolationException.class)
    public Object handleDatabaseException(DataIntegrityViolationException ex, Model model,
            HttpServletRequest request, RedirectAttributes redirectAttributes) {
        log.error("Database error on {}", request.getRequestURI(), ex);
        String uri = request.getRequestURI();

        if (uri.startsWith("/products")) {
            redirectAttributes.addFlashAttribute("error",
                    "Could not save product. Please refresh the page and try again.");
            return "redirect:/products";
        }

        request.setAttribute("message", "We could not save your changes right now. Please try again.");
        return "error/500";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Object handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request, Model model) {
        log.warn("Illegal argument on {}", request.getRequestURI(), ex);

        if (isAjaxRequest(request)) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }

        model.addAttribute("error", ex.getMessage());
        return resolveViewForRequest(request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public Object handleResponseStatus(ResponseStatusException ex, HttpServletRequest request, HttpServletResponse response,
            Model model) {
        log.warn("Response status exception on {}", request.getRequestURI(), ex);

        if (isAjaxRequest(request)) {
            return ResponseEntity.status(ex.getStatusCode()).body(ex.getReason() != null ? ex.getReason() : "Request failed");
        }

        if (ex.getStatusCode().value() == HttpServletResponse.SC_NOT_FOUND) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("message", ex.getReason() != null ? ex.getReason() : "Page not found.");
            return "error/404";
        }

        response.setStatus(ex.getStatusCode().value());
        model.addAttribute("message", ex.getReason() != null ? ex.getReason() : "Something went wrong. Please try again.");
        return "error/500";
    }

    @ExceptionHandler(RuntimeException.class)
    public Object handleRuntime(RuntimeException ex, HttpServletRequest request) {
        log.error("Runtime exception on {}", request.getRequestURI(), ex);

        String uri = request.getRequestURI();

        if (uri.equals("/sales/add") || uri.equals("/sales/update-qty") || isAjaxRequest(request)) {
            return ResponseEntity
                    .badRequest()
                    .body(ex.getMessage());
        }

        request.setAttribute("message", "Something went wrong. Please try again.");
        return "error/500";
    }

    // Handle Whitelabel Exception in Porduction.

    @ExceptionHandler(Exception.class)
    public String handleException(Exception ex, Model model, HttpServletRequest request) {
        log.error("Unhandled exception on {}", request.getRequestURI(), ex);
        request.setAttribute("javax.servlet.error.exception", ex);
        model.addAttribute("message", "Something went wrong. Please try again.");
        return "error/500";
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public String handle404(NoHandlerFoundException ex, Model model, HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        model.addAttribute("message", "Page not found.");
        return "error/404";
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public String handleNoResourceFound(NoResourceFoundException ex, Model model, HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        model.addAttribute("message", "Page not found.");
        return "error/404";
    }

    private String resolveViewForRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith("/register")) {
            return "register";
        }
        if (uri != null && uri.startsWith("/forgot-password")) {
            return "forgot-password";
        }
        if (uri != null && uri.startsWith("/reset-password")) {
            return "reset-password";
        }
        return "error/500";
    }

    private boolean isAjaxRequest(HttpServletRequest request) {
        String requestedWith = request.getHeader("X-Requested-With");
        String accept = request.getHeader("Accept");
        String contentType = request.getContentType();

        return "XMLHttpRequest".equalsIgnoreCase(requestedWith)
                || (accept != null && accept.contains("application/json"))
                || (contentType != null && contentType.contains("application/json"));
    }

}
