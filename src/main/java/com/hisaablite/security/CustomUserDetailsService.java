package com.hisaablite.security;

import com.hisaablite.entity.User;
import com.hisaablite.repository.UserRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

   @Override
public UserDetails loadUserByUsername(String username)
        throws UsernameNotFoundException {

    User user = userRepository.findByUsername(username)
            .orElseThrow(() ->
                    new UsernameNotFoundException("User not found"));

    //  CHECK ACTIVE STATUS ---important hai 
//     if (!user.isActive()) {
//     throw new DisabledException(
//         "Account is locked. Please contact shop owner.");
// }

    return org.springframework.security.core.userdetails.User
            .withUsername(user.getUsername())
            .password(user.getPassword())
            .roles(user.getRole().name())
            .disabled(!user.isActive()) 
            .build();

    
}
}