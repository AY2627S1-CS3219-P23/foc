package foc.user.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import foc.user.dto.SetupOwnerRequest;
import foc.user.dto.UserResponse;
import foc.user.entity.User;
import foc.user.exception.OwnerAlreadySetException;
import foc.user.repository.UserRepository;

@Service 
public class OwnerSetupService {

    private static final String ROLE_OWNER = "OWNER";
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public OwnerSetupService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // bootstraps first owner account, only when owners = 0
    @Transactional 
    public UserResponse setupFirstOwner(SetupOwnerRequest request) {
        userRepository.acquireSetupLock();

        ensureSetupAvailable();

        String email = normalizeEmail(request.email());
        String username = normalizeUsername(request.username());

        ensureUserDetailsAreUnique(email, username);

        User owner = createOwner(email, username, request.password());

        User saved = userRepository.save(owner);

        return toUserResponse(saved);
    }

    private void ensureSetupAvailable() {
        if (userRepository.countByRole(ROLE_OWNER) > 0) {
            throw new OwnerAlreadySetException();
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }    


    private String normalizeUsername(String username) {
        return username.trim();
    }

    private void ensureUserDetailsAreUnique(String email, String username) {

        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Email is already registered"
            );
        }

        if (userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Username is already taken"
            );
        }
    }  
    
    
    private User createOwner(String email, String username, String password) {
        User owner = new User();

        owner.setEmail(email);
        owner.setUsername(username);
        owner.setPasswordHash(passwordEncoder.encode(password));
        owner.setRole(ROLE_OWNER);
        owner.setFailedLoginAttempts(0);
        owner.setLockedUntil(null);
        owner.setDeletedAt(null);

        return owner;
    }
    private UserResponse toUserResponse(User user) {
        return new UserResponse(
            user.getId(),
            user.getEmail(),
            user.getUsername(),
            user.getRole(),
            user.getCreatedAt()
        );
    }
}
