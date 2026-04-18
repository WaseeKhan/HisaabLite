package com.expygen.controller;

import com.expygen.dto.ContactRequestForm;
import com.expygen.service.ContactService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class ContactController {

    private final ContactService contactService;

    @GetMapping("/contact")
    public String contactPage(Model model) {
        if (!model.containsAttribute("contactRequestForm")) {
            model.addAttribute("contactRequestForm", new ContactRequestForm());
        }
        return "contact";
    }

    @PostMapping("/contact")
    public String submitContact(@Valid @ModelAttribute("contactRequestForm") ContactRequestForm form,
                                BindingResult bindingResult,
                                RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            return "contact";
        }

        contactService.save(form);

        redirectAttributes.addFlashAttribute("toastMessage",
                "Your request has been submitted successfully. Our team will get in touch with you shortly.");
        redirectAttributes.addFlashAttribute("toastType", "success");

        return "redirect:/contact";
    }
}