package com.stargarden.service;

import com.stargarden.entity.User;
import com.stargarden.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public User register(User user) {
        user.setRole("USER");
        user.setEnabled(true);
        // 注册时加密密码
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        log.info("用户注册成功: username={}", user.getUsername());
        return userRepository.save(user);
    }

    /**
     * 验证用户名和密码（登录用）
     */
    public Optional<User> verifyLogin(String username, String rawPassword) {
        Optional<User> userOpt = findByUsername(username);
        if (userOpt.isPresent()) {
            User u = userOpt.get();
            if (passwordEncoder.matches(rawPassword, u.getPassword())) {
                return Optional.of(u);
            }
        }
        return Optional.empty();
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    public User update(User user) {
        return userRepository.save(user);
    }

    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }
}
