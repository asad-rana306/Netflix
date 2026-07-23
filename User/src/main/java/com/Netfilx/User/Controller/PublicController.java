package com.Netfilx.User.Controller;

import com.Netfilx.User.DTO.Request.LoginRequest;
import com.Netfilx.User.DTO.Request.SignupRequest;
import com.Netfilx.User.DTO.Response.AuthResponse;
import com.Netfilx.User.Entity.User;
import com.Netfilx.User.Service.UserDetailServiceImpl;
import com.Netfilx.User.Service.UserService;
import com.Netfilx.User.Utils.JwtUtil;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/public")
public class PublicController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserDetailServiceImpl userDetailService;
    private final UserService userService;

    @Autowired
    public PublicController(AuthenticationManager authenticationManager,
                            JwtUtil jwtUtil,
                            UserDetailServiceImpl userDetailService,
                            UserService userService) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.userDetailService = userDetailService;
        this.userService = userService;
    }

    @GetMapping
    public String healthCheck() {
        return "Ok";
    }

    @PostMapping("/signup")
    public ResponseEntity<String> signup(@Valid @RequestBody SignupRequest request) {
        // Map DTO to User entity
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPasswordHash(request.getPassword()); // userService.saveNewUser will encode this password

        userService.saveNewUser(user);
        return ResponseEntity.status(HttpStatus.CREATED).body("User Registered Successfully");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            // 1. Authenticate using email and plain password from the DTO
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );

            // 2. Load UserDetails and generate JWT token
            UserDetails userDetails = userDetailService.loadUserByUsername(request.getEmail());
            String jwt = jwtUtil.generateToken(userDetails.getUsername());

            // 3. Return structured AuthResponse
            AuthResponse response = AuthResponse.builder()
                    .token(jwt)
                    .email(request.getEmail())
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Incorrect email or password");
        }
    }
}