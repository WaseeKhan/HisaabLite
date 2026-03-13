package com.hisaablite.admin.config;

import com.hisaablite.entity.User;
import com.hisaablite.entity.Role;
import com.hisaablite.entity.Shop;
import com.hisaablite.entity.PlanType;
import com.hisaablite.repository.UserRepository;
import com.hisaablite.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final PasswordEncoder passwordEncoder;

   
    @Value("${admin.email:admin@hisaablite.com}")
    private String adminEmail;

    @Value("${admin.password:admin@123}")
    private String adminPassword;

    @Value("${admin.name:Super Admin}")
    private String adminName;

    @Value("${admin.phone:9999999999}")
    private String adminPhone;


    @Override
    @Transactional
    public void run(String... args) throws Exception {
        
        String adminEmail = "admin@hisaablite.com";
        
        // Check if admin already exists
        if (userRepository.findByUsername(adminEmail).isEmpty()) {
            
            log.info("Creating admin user with shop...");
            
            //Create a REAL shop for admin 
            Shop adminShop = Shop.builder()
                .name("HisaabLite Admin Shop")  
                .active(true)
                .address("Mumbai, India")
                .city("Mumbai")
                .state("Maharashtra")
                .pincode("400001")
                .gstNumber("ADMIN00GST001")
                .panNumber("ADMINPAN001")
                .upiId("admin@hisaablite")
                .planType(PlanType.ENTERPRISE)  
                .createdAt(LocalDateTime.now())
                .build();
            
            Shop savedShop = shopRepository.save(adminShop);
            log.info("Admin shop created: {} (ID: {})", savedShop.getName(), savedShop.getId());
            
            // Create admin with this shop
            User admin = User.builder()
                .name("Super Admin")
                .username(adminEmail)
                .phone("9999999999")
                .password(passwordEncoder.encode("admin@123"))
                .role(Role.ADMIN)
                .approved(true)
                .active(true)
                .shop(savedShop)  // Real shop assign
                .createdAt(LocalDateTime.now())
                .build();
            
            userRepository.save(admin);
            
          
            
        } else {
            log.info("Admin user already exists: {}", adminEmail);
        }
    }
}