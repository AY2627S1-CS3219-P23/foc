/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-23
Scope: Generated profile lookups for issue #95 (own profile and public
       profile). Unknown and soft-deleted users both raise
       UserNotFoundException.
Author review: Ryan reviewed to ensure that it follows team's decisions.
*/

package foc.user.service;

import org.springframework.stereotype.Service;

import foc.user.dto.PublicProfileResponse;
import foc.user.dto.UserResponse;
import foc.user.entity.User;
import foc.user.exception.UserNotFoundException;
import foc.user.repository.UserRepository;

@Service
public class ProfileService {

    private final UserRepository userRepository;

    public ProfileService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // own profile: username, email, role (credits are fetched from credit-service by the web app)
    public UserResponse getOwnProfile(Long userId) {
        User user = findActiveUser(userId);

        return new UserResponse(
            user.getId(),
            user.getEmail(),
            user.getUsername(),
            user.getRole().name(),
            user.getCreatedAt()
        );
    }

    // another user's profile: username and joined date only
    public PublicProfileResponse getPublicProfile(Long userId) {
        User user = findActiveUser(userId);

        return new PublicProfileResponse(user.getUsername(), user.getCreatedAt());
    }

    private User findActiveUser(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
            .orElseThrow(UserNotFoundException::new);
    }
}
