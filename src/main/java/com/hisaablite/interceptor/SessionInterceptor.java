package com.hisaablite.interceptor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;



@Component
public class SessionInterceptor implements HandlerInterceptor {
    
    @Autowired
    private SessionRegistry sessionRegistry;
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication != null && authentication.isAuthenticated() 
            && !authentication.getPrincipal().equals("anonymousUser")) {
            
            String currentSessionId = request.getSession().getId();
            Object principal = authentication.getPrincipal();
            
            java.util.List<SessionInformation> sessions = sessionRegistry.getAllSessions(principal, false);
            
            boolean sessionValid = sessions.stream()
                .anyMatch(s -> s.getSessionId().equals(currentSessionId) && !s.isExpired());
            
            if (!sessionValid) {
                // Invalidate current session
                request.getSession().invalidate();
                SecurityContextHolder.clearContext();
                
                // Redirect to login with expired message
                response.sendRedirect("/login?expired");
                return false;
            }
        }
        
        return true;
    }
    
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        // Add session info to model if view is being rendered
        if (modelAndView != null && !modelAndView.getViewName().startsWith("redirect:")) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() 
                && !authentication.getPrincipal().equals("anonymousUser")) {
                
                String sessionId = request.getSession().getId();
                Object principal = authentication.getPrincipal();
                java.util.List<SessionInformation> sessions = sessionRegistry.getAllSessions(principal, false);
                
                int activeSessions = (int) sessions.stream().filter(s -> !s.isExpired()).count();
                modelAndView.addObject("activeSessionsCount", activeSessions);
                modelAndView.addObject("currentSessionId", sessionId);
            }
        }
    }
}