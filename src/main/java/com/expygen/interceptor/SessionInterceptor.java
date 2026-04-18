package com.expygen.interceptor;

import com.expygen.entity.User;
import com.expygen.repository.UserRepository;
import com.expygen.service.WorkspaceAccessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceAccessService workspaceAccessService;

    @Value("${app.security.enforce-single-session:true}")
    private boolean enforceSingleSession;
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication != null && authentication.isAuthenticated() 
            && !authentication.getPrincipal().equals("anonymousUser")) {

            User user = userRepository.findByUsername(authentication.getName()).orElse(null);
            if (user != null && !workspaceAccessService.canAccessWorkspace(user)) {
                String path = request.getRequestURI();
                if (!path.startsWith("/workspace-status")
                        && !path.startsWith("/logout")
                        && !path.startsWith("/subscription")
                        && !path.startsWith("/upgrade")
                        && !path.startsWith("/support")) {
                    response.sendRedirect("/workspace-status");
                    return false;
                }
            }
            
            if (enforceSingleSession) {
                String currentSessionId = request.getSession().getId();
                Object principal = authentication.getPrincipal();

                java.util.List<SessionInformation> sessions = sessionRegistry.getAllSessions(principal, false);
                if (sessions.isEmpty()) {
                    return true;
                }

                boolean sessionValid = sessions.stream()
                    .anyMatch(s -> s.getSessionId().equals(currentSessionId) && !s.isExpired());

                if (!sessionValid) {
                    request.getSession().invalidate();
                    SecurityContextHolder.clearContext();
                    response.sendRedirect("/login?expired");
                    return false;
                }
            }
        }
        
        return true;
    }
    
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        if (!enforceSingleSession) {
            return;
        }

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
