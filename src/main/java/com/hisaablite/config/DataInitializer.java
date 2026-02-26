// package com.hisaablite.config;

// import com.hisaablite.entity.*;
// import com.hisaablite.repository.*;
// import lombok.RequiredArgsConstructor;
// import org.springframework.boot.CommandLineRunner;
// import org.springframework.context.annotation.Bean;
// import org.springframework.context.annotation.Configuration;
// import org.springframework.security.crypto.password.PasswordEncoder;

// @Configuration
// @RequiredArgsConstructor
// public class DataInitializer {

//     @Bean
// CommandLineRunner initData(
//         ShopRepository shopRepository,
//         UserRepository userRepository,
//         PasswordEncoder passwordEncoder) {

//     return args -> {

//         if (shopRepository.count() == 0) {

//             Shop shop = Shop.builder()
//                     .name("Demo Kirana")
//                     .panNumber("ABCDE1234F")
//                     .address("Mumbai")
//                     .city("Mumbai")
//                     .state("Maharashtra")
//                     .staffLimit(5)
//                     .subscriptionPlan(SubscriptionPlan.FREE)
//                     .active(true)
//                     .build();

//             shop = shopRepository.save(shop);

//             User owner = User.builder()
//                     .name("Waseem")
//                     .username("owner@gmail.com")
//                     .phone("9999999999")
//                     .password(passwordEncoder.encode("123456"))
//                     .role(Role.OWNER)
//                     .shop(shop)
//                     .active(true)
//                     .build();
//             userRepository.save(owner);

//             User staff = User.builder()
//                     .name("Staff")
//                     .username("staff@gmail.com")
//                     .phone("8888888888")
//                     .password(passwordEncoder.encode("123456"))
//                     .role(Role.STAFF)
//                     .shop(shop)
//                     .active(true)
//                     .build();

//             userRepository.save(staff);
//         }
//     };
// }
// }