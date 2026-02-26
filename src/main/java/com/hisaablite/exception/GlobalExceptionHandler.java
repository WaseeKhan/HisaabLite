package com.hisaablite.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.ui.Model;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

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
    @ExceptionHandler(Exception.class)
    public String handleGeneric(Exception ex, Model model) {
        model.addAttribute("error", "Something went wrong. Please try again.");
        return "error";
    }
}