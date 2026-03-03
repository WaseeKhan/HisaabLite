package com.hisaablite.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.ui.Model;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.NoHandlerFoundException;

import jakarta.servlet.http.HttpServletRequest;

@ControllerAdvice
public class GlobalExceptionHandler {

    // Validation errors
    @ExceptionHandler(BindException.class)
    public String handleValidationException(BindException ex, Model model) {
        model.addAttribute("error", "Invalid input. Please check the form.");
        return "register";
    }

    // Duplicate custom errors
    @ExceptionHandler(DuplicateResourceException.class)
    public String handleDuplicate(DuplicateResourceException ex, Model model) {
        model.addAttribute("error", ex.getMessage());
        return "register";
    }

    

    // Database constraint fallback
    @ExceptionHandler(DataIntegrityViolationException.class)
    public String handleDatabaseException(DataIntegrityViolationException ex, Model model) {
        model.addAttribute("error", "Database constraint violation.");
        return "register";
    }

    // Generic fallback
    // @ExceptionHandler(Exception.class)
    // public String handleGeneric(Exception ex, Model model) {
    //     model.addAttribute("error", "Something went wrong. Please try again.");
    //     return "error";
    // }

@ExceptionHandler(RuntimeException.class)
public Object handleRuntime(RuntimeException ex, HttpServletRequest request) {

    String uri = request.getRequestURI();

    // ONLY handle /sales/add as JSON
    if (uri.equals("/sales/add")) {
        return ResponseEntity
                .badRequest()
                .body(ex.getMessage());
    }

    // Baaki sab normal HTML flow
    request.setAttribute("error", ex.getMessage());
    return "error";
}
// Handle Whitelabel Exception in Porduction. 

    @ExceptionHandler(Exception.class)
    public String handleException(Exception ex, Model model) {
        model.addAttribute("message", "Something went wrong.");
        return "error/500";
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public String handle404(NoHandlerFoundException ex, Model model) {
        model.addAttribute("message", "Page not found.");
        return "error/404";
    }

}