package com.expygen.listener;

import org.springframework.context.ApplicationListener;
import org.springframework.security.core.session.SessionDestroyedEvent;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class SessionExpiredListener implements ApplicationListener<SessionDestroyedEvent> {
    
    @Override
    public void onApplicationEvent(SessionDestroyedEvent event) {
        log.info("Session expired: {}", event.getId());
    }
}
