package com.expygen.service;

import com.expygen.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WorkspaceAccessService {

    private final PlanLimitService planLimitService;

    public WorkspaceAccessState getAccessState(User user) {
        if (user == null) {
            return WorkspaceAccessState.EMAIL_VERIFICATION_REQUIRED;
        }

        if (!user.isActive()) {
            return WorkspaceAccessState.EMAIL_VERIFICATION_REQUIRED;
        }

        if (user.getShop() != null && !user.getShop().isActive()) {
            return WorkspaceAccessState.SHOP_INACTIVE;
        }

        if (!user.isApproved()) {
            return WorkspaceAccessState.APPROVAL_PENDING;
        }

        if (user.getShop() != null && !planLimitService.isSubscriptionActive(user.getShop())) {
            return WorkspaceAccessState.SUBSCRIPTION_EXPIRED;
        }

        return WorkspaceAccessState.ACTIVE;
    }

    public boolean canAccessWorkspace(User user) {
        return getAccessState(user) == WorkspaceAccessState.ACTIVE;
    }

    public boolean shouldAllowAuthenticatedSession(User user) {
        WorkspaceAccessState state = getAccessState(user);
        return state != WorkspaceAccessState.EMAIL_VERIFICATION_REQUIRED
                && state != WorkspaceAccessState.SHOP_INACTIVE;
    }
}
