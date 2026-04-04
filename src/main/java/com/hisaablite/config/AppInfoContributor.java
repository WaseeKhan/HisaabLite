package com.hisaablite.config;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AppInfoContributor implements InfoContributor {

    private final AppConfig appConfig;

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("app", java.util.Map.of(
                "name", appConfig.getAppName(),
                "version", appConfig.getAppVersion(),
                "environment", appConfig.getEnvironment()))
                .withDetail("features", java.util.Map.of(
                        "whatsapp", appConfig.isWhatsappEnabled(),
                        "emailVerification", appConfig.isEmailVerificationRequired(),
                        "adminApproval", appConfig.isAdminApprovalRequired()));
    }
}
