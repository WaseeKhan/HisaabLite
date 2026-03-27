package com.hisaablite.listener;

import org.springframework.context.ApplicationListener;
import org.springframework.security.core.session.SessionDestroyedEvent;
import org.springframework.stereotype.Component;

@Component
public class SessionExpiredListener implements ApplicationListener<SessionDestroyedEvent> {
    
    @Override
    public void onApplicationEvent(SessionDestroyedEvent event) {
        // Log session expiration
        System.out.println("Session expired: " + event.getId());
    }
}