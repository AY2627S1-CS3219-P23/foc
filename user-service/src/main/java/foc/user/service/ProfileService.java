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
2026-09-27 (Claude Code, Opus 5.5), PR #135 review: lookups use the shared
UserRepository.getActiveUser.
2026-10-11 (Claude Code, Fable 5), issue #94: recoverOwnAccount — the
explicit restore that replaced login's silent recovery (owner decision
via options Q&A). The one lookup here that deliberately reaches a
soft-deleted account.
2026-10-17 (Claude Code, Opus 5), issue #203: comment only —
deleteOwnAccount's asymmetry with AdminService.removeUser (no
demotion, no denylist stamp) recorded; owner chose option C (accept,
document) via options Q&A.
*/

package foc.user.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import foc.user.dto.PublicProfileResponse;
import foc.user.dto.UserResponse;
import foc.user.entity.User;
import foc.user.exception.UserNotFoundException;
import foc.user.repository.UserRepository;

@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final int retentionDays;

    public ProfileService(UserRepository userRepository,
            @Value("${user.retention.days}") int retentionDays) {
        this.userRepository = userRepository;
        this.retentionDays = retentionDays;
    }

    // own profile: username, email, role (credits are fetched from credit-service by the web app)
    public UserResponse getOwnProfile(Long userId) {
        return UserResponse.from(userRepository.getActiveUser(userId));
    }

    // another user's profile: username only
    public PublicProfileResponse getPublicProfile(Long userId) {
        User user = userRepository.getActiveUser(userId);

        return new PublicProfileResponse(user.getUsername());
    }

    // self-deletion: stamps deleted_at, keeping the row (and its email/
    // username reservation) for the 30-day recovery window (#94);
    // AccountPurgeScheduler hard-deletes it on day 31.
    // Deliberately asymmetric with AdminService.removeUser (issue #203,
    // owner decision, option C): no demotion to USER and no
    // denylist:user:<id> stamp, so a self-deleting ADMIN keeps the role
    // — reaching the admin routes on every fresh login in the window,
    // since they authorize on the token's claim alone — and restores as
    // ADMIN. Accepted because nobody but this caller can open that
    // state and the same caller closes it with POST /users/me/recover;
    // removal needs both because it is done to someone else.
    // Pinned by JwtAuthenticationTest.selfDeletedAdminKeepsAdminRoutes.
    public void deleteOwnAccount(Long userId) {
        User user = userRepository.getActiveUser(userId);
        user.softDelete(Instant.now());
        userRepository.save(user);
    }

    // the explicit restore (issue #94): a soft-deleted caller logged in
    // (login leaves deletedAt standing now) and chose "Restore My
    // Account". Locked like the account-update flows' user reads, so a
    // concurrent failed-login counter write can't race this into a
    // @Version 409; @Transactional because the lock lives and dies with
    // the transaction
    @Transactional
    public UserResponse recoverOwnAccount(Long userId) {
        // the deliberate deleted-bypassing lookup — getActiveUser would
        // 404 exactly the caller this route exists for
        User user = userRepository.findWithLockById(userId)
            .orElseThrow(UserNotFoundException::new);
        // past the window the account answers as gone everywhere (login,
        // reset); the purge is about to take it
        if (user.getDeletedAt() != null
            && user.getDeletedAt().isBefore(Instant.now().minus(retentionDays, ChronoUnit.DAYS))) {
            throw new UserNotFoundException();
        }
        // idempotent for an active caller (double-click, replayed tab):
        // nothing conflicting to report, so answer with the account
        if (user.getDeletedAt() != null) {
            user.setDeletedAt(null);
            userRepository.save(user);
        }
        return UserResponse.from(user);
    }
}
