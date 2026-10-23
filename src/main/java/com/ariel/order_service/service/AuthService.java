package com.ariel.order_service.service;

import com.ariel.order_service.dto.AuthResponse;
import com.ariel.order_service.dto.LoginRequest;
import com.ariel.order_service.dto.RegisterRequest;
import com.ariel.order_service.entity.AppUser;
import com.ariel.order_service.enums.Role;
import com.ariel.order_service.exception.InvalidCredentialsException;
import com.ariel.order_service.exception.UserAlreadyExistsException;
import com.ariel.order_service.repository.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {

        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public void register(RegisterRequest request) {

        if (appUserRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException(
                    request.getEmail()
            );
        }

        AppUser user = new AppUser();

        user.setEmail(request.getEmail());

        user.setPassword(
                passwordEncoder.encode(
                        request.getPassword()
                )
        );

        user.setRole(Role.USER);

        appUserRepository.save(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {

        AppUser user = appUserRepository
                .findByEmail(request.getEmail())
                .orElseThrow(
                        InvalidCredentialsException::new
                );

        boolean passwordMatches =
                passwordEncoder.matches(
                        request.getPassword(),
                        user.getPassword()
                );

        if (!passwordMatches) {
            throw new InvalidCredentialsException();
        }

        String token =
                jwtService.generateToken(user);

        return new AuthResponse(token);
    }
}