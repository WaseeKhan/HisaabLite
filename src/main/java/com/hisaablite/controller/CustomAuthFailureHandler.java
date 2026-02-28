package com.hisaablite.controller;


import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomAuthFailureHandler implements AuthenticationFailureHandler {

   @Override
public void onAuthenticationFailure(HttpServletRequest request,
                                    HttpServletResponse response,
                                    AuthenticationException exception)
        throws IOException {

    String message;

    if (exception instanceof DisabledException) {
        message = "Account is locked. Please contact shop owner.";
    } else {
        message = "bad";  // keyword for wrong credentials
    }

    message = java.net.URLEncoder.encode(message, "UTF-8");
    response.sendRedirect("/login?error=" + message);
}
}