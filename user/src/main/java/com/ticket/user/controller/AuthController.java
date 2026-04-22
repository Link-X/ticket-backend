package com.ticket.user.controller;

import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.ErrorCode;
import com.ticket.common.result.Result;
import com.ticket.core.domain.entity.User;
import com.ticket.core.domain.entity.UserRole;
import com.ticket.core.mapper.UserMapper;
import com.ticket.core.mapper.UserRoleMapper;
import com.ticket.user.config.JwtTokenProvider;
import com.ticket.user.config.NoLogin;
import lombok.Data;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@NoLogin
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthController(UserMapper userMapper,
                          UserRoleMapper userRoleMapper,
                          PasswordEncoder passwordEncoder,
                          JwtTokenProvider jwtTokenProvider) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody RegisterRequest req) {
        if (userMapper.selectByUsername(req.getUsername()) != null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户名已存在");
        }
        User user = new User();
        user.setUsername(req.getUsername());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setPhone(req.getPhone());
        user.setEmail(req.getEmail());
        user.setStatus(1);
        userMapper.insert(user);

        UserRole role = new UserRole();
        role.setUserId(user.getId());
        role.setRole("USER");
        userRoleMapper.insert(role);

        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername());
        return Result.success(Map.of("token", token, "userId", user.getId()));
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginRequest req) {
        User user = userMapper.selectByUsername(req.getUsername());
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户名或密码错误");
        }
        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername());
        return Result.success(Map.of("token", token, "userId", user.getId()));
    }

    @Data
    public static class RegisterRequest {
        private String username;
        private String password;
        private String phone;
        private String email;
    }

    @Data
    public static class LoginRequest {
        private String username;
        private String password;
    }
}
