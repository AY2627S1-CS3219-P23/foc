/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-23
Scope: Generated profile lookups for issue #95 (own profile and public
       profile). Unknown and soft-deleted users both raise
       UserNotFoundException.
Author review: Ryan reviewed to ensure that it follows team's decisions.
2026-09-25 (Claude Code, Opus 5.5), PR #131 review: own profile uses the
shared UserResponse.from mapping.
2026-09-27 (Claude Code, Fable 5), issue #93: deleteOwnAccount added
(bearer-only self-deletion, no password/OTP re-check — chosen by
Leong Wei Zhi via options Q&A).
*/

package foc.user.service;

import java.time.Instant;

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
        return UserResponse.from(findActiveUser(userId));
    }

    // another user's profile: username only
    public PublicProfileResponse getPublicProfile(Long userId) {
        User user = findActiveUser(userId);

        return new PublicProfileResponse(user.getUsername());
    }

    // self-deletion: stamps deleted_at, keeping the row (and its email/
    // username reservation) for the 30-day recovery window (#94);
    // AccountPurgeScheduler hard-deletes it on day 31
    public void deleteOwnAccount(Long userId) {
        User user = findActiveUser(userId);
        user.softDelete(Instant.now());
        userRepository.save(user);
    }

    private User findActiveUser(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
            .orElseThrow(UserNotFoundException::new);
    }
}
