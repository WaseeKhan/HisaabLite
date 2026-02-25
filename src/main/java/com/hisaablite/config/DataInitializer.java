package com.hisaablite.config;

import com.hisaablite.entity.*;
import com.hisaablite.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    @Bean
    CommandLineRunner initData(
            ShopRepository shopRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {

        return args -> {

            if (shopRepository.count() == 0) {

                Shop shop = Shop.builder()
                        .name("Demo Kirana")
                        .ownerName("Waseem")
                        .phone("9999999999")
                        .address("Mumbai")
                        .active(true)
                        .build();

                shopRepository.save(shop);

                User owner = User.builder()
                        .name("Owner")
                        .username("owner")
                        .password(passwordEncoder.encode("1234"))
                        .role(Role.OWNER)
                        .shop(shop)
                        .active(true)
                        .build();
                userRepository.save(owner);

                User staff = User.builder()
                        .name("Staff")
                        .username("staff")
                        .password(passwordEncoder.encode("1234"))
                        .role(Role.STAFF)
                        .shop(shop)
                        .active(true)
                        .build();

                userRepository.save(staff);
            }
        };
    }
}