package com.ticketing.auth;

import java.util.Locale;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.ticketing.user.User;
import com.ticketing.user.UserStatus;
import com.ticketing.user.UserRepository;

@Service
class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        User user = userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .filter(u -> u.getStatus() != UserStatus.DELETED)
                .orElseThrow(() -> new UsernameNotFoundException("No such account"));
        return AppUserDetails.from(user);
    }
}
