package com.expygen.admin.config;

import com.expygen.entity.User;
import com.expygen.entity.Role;
import com.expygen.entity.Shop;
import com.expygen.entity.PlanType;
import com.expygen.repository.UserRepository;
import com.expygen.repository.ShopRepository;
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

   
    @Value("${admin.email:admin@expygen.com}")
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
        log.info("Command Line Runner Started");
        
        // Check if admin already exists
        if (userRepository.findByUsername(adminEmail).isEmpty()) {
            
            log.info("Creating admin user with shop...");
            
            //Create a REAL shop for admin 
            Shop adminShop = Shop.builder()
                .name("Expygen Admin Shop")  
                .active(true)
                .address("Mumbai, India")
                .city("Mumbai")
                .state("Maharashtra")
                .pincode("400001")
                .gstNumber("ADMIN00GST001")
                .upiId("admin@expygen")
                .planType(PlanType.ENTERPRISE)  
                .createdAt(LocalDateTime.now())
                .build();
            
            Shop savedShop = shopRepository.save(adminShop);
            log.info("Admin shop created: {} (ID: {})", savedShop.getName(), savedShop.getId());
            
            // Create admin with this shop
            User admin = User.builder()
                .name(adminName)
                .username(adminEmail)
                .phone(adminPhone)
                .password(passwordEncoder.encode(adminPassword))
                .role(Role.ADMIN)
                .approved(true)
                .active(true)
                .shop(savedShop)  // Real shop assign
                .createdAt(LocalDateTime.now())
                .build();
            
            userRepository.save(admin);
            
          
         log.info("Command Line Runner Completed its Work");
        
        } else {
            log.info("Admin user already exists: {}", adminEmail);
            log.info("Command Line Runner Execution Completed");
        }
    }
}
