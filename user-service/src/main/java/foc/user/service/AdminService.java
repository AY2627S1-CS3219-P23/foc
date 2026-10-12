/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-26
Scope: Generated the admin user management logic for issue #96: user list
       (partial case-insensitive search on id/username/email, role filter,
       sorting, 20/50/100 page sizes), role change (promote/demote) and
       account removal (soft delete). Rules follow the team's decisions:
       OWNER accounts cannot be changed or removed by anyone, OWNER is never
       granted here, admins may act on other admins and demote themselves,
       and admins remove themselves only through DELETE /users/me.
Author review: Ryan reviewed to ensure it follows the team's decisions.
2026-09-27 (Claude Code, Opus 5.5): removal switched to the shared
User.softDelete from #93 after PR #136 merged.
2026-09-27 (Claude Code, Opus 5.5), PR #135 review: lookups use the shared
UserRepository.getActiveUser; role rank taken from the Role enum order.
2026-09-29 (Claude Code, Opus 5.5), PR #140 review: GET /users leaves out
soft-deleted accounts (team decision, replacing "soft-deleted included"):
removed users no longer come back on the admin page's next reload.
2026-09-29 (Claude Code, Opus 5.5), issue #147: includeDeleted lists
soft-deleted accounts too, so admins can see removed accounts before the
purge (team decision: a filter on GET /users, not a separate table).
2026-10-12 (Claude Code, Fable 5), PR #179 review (@Sinnez1): removeUser's
comment now records the owner decision (via options Q&A) that admin
removal stays self-restorable — same soft-delete, no deleted-by record.
2026-10-12 (Claude Code, Fable 5), PR #179 approval follow-up (@Sinnez1):
removal demotes the target to USER (owner decision via options Q&A — the
reviewer showed a removed admin keeping the admin endpoints while deleted
and restoring itself as an admin; the alternatives were an active-check
in the admin routes or documenting the behaviour as accepted).
2026-10-12 (Claude Code, Fable 5), issue #178: changeRole and removeUser
now evict the target's outstanding sessions via TokenDenylist's per-user
stamp (G11/G12 — owner decision via options Q&A: the epoch mechanism from
PR #179, scoped to these two paths), so a demoted admin or removed user
loses their token on the next request instead of keeping it until exp;
that also retires the follow-up comment's "keeps ADMIN until exp" caveat.
*/

package foc.user.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import foc.user.dto.UserResponse;
import foc.user.entity.Role;
import foc.user.entity.User;
import foc.user.repository.UserRepository;
import foc.user.security.TokenDenylist;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

@Service
public class AdminService {

    public static final int DEFAULT_PAGE_SIZE = 100;
    private static final Set<Integer> PAGE_SIZES = Set.of(20, 50, DEFAULT_PAGE_SIZE);

    private static final Set<String> SORT_FIELDS = Set.of("id", "email", "username", "role", "createdAt");

    // sorting by role follows rank, lowest first: the Role enum's declaration order
    private static final List<Role> ROLE_RANK = List.of(Role.values());

    private final UserRepository userRepository;
    private final TokenDenylist tokenDenylist;

    public AdminService(UserRepository userRepository, TokenDenylist tokenDenylist) {
        this.userRepository = userRepository;
        this.tokenDenylist = tokenDenylist;
    }

    // active accounts only, unless includeDeleted: soft-deleted ones are
    // otherwise hidden until they recover or are purged. sort is "field" or
    // "field,asc|desc"; null sorts by id ascending
    @Transactional(readOnly = true)
    public Page<UserResponse> listUsers(
            String search, Role role, String sort, int page, int size, boolean includeDeleted) {
        if (page < 0) {
            throw badRequest("page must not be negative");
        }
        if (!PAGE_SIZES.contains(size)) {
            throw badRequest("size must be one of 20, 50 or 100");
        }

        SortOrder order = parseSort(sort);
        // ordering is applied inside the specification (role sorts by rank, which a
        // plain Sort can't express), so the page request itself stays unsorted
        return userRepository
            .findAll(matching(search, role, includeDeleted, order), PageRequest.of(page, size))
            .map(UserResponse::from);
    }

    // promote to ADMIN or demote to USER. Returns the user unchanged if they already hold the role
    @Transactional
    public UserResponse changeRole(Long targetId, Role newRole) {
        if (newRole == Role.OWNER) {
            throw forbidden("OWNER can only be granted through owner setup");
        }

        User target = userRepository.getActiveUser(targetId);
        ensureNotOwner(target);

        if (target.getRole() == newRole) {
            return UserResponse.from(target);
        }

        target.setRole(newRole);
        UserResponse response = UserResponse.from(userRepository.save(target));
        // issue #178: the role lives in the JWT claim, so without this a
        // demoted admin keeps the admin API until their token expires (≤1 h,
        // from any client still holding it). The per-user stamp revokes every
        // outstanding token at once — the target signs in again and the fresh
        // token carries the new role. Any actual change stamps, promotion
        // included (the old token's claim is wrong either way); the same-role
        // early return above does not. Best-effort like the writes in
        // PasswordResetService (G9): if Redis is down the gateway is failing
        // open anyway (G6), and the token still dies at its own exp.
        tokenDenylist.revokeUserSessions(targetId);
        return response;
    }

    // soft delete, same as self-deletion: the account stays recoverable
    // for 30 days — by the removed user themselves, via login + POST
    // /users/me/recover. Owner decisions (PR #179 review, @Sinnez1):
    // admin removal is a deletion on the user's behalf, not a ban, so
    // nothing records who deleted and nothing blocks the self-restore —
    // but removal demotes to USER, so a removed admin's later logins
    // (deleted accounts can still log in) and any self-restored account
    // carry no admin powers; only re-promotion gives them back. A token
    // minted before the removal no longer outlives it either (issue
    // #178): the stamp below makes the gateway refuse it on the next
    // request, rather than leaving it ADMIN until its exp (≤ 1 h). The
    // demotion stays as the belt for what the stamp cannot cover — a
    // Redis-down write (best-effort, G9) against a gateway that fails
    // open (G6), and the account's own later logins after a restore.
    @Transactional
    public void removeUser(Long callerId, Long targetId) {
        if (targetId.equals(callerId)) {
            throw forbidden("Use DELETE /users/me to delete your own account");
        }

        User target = userRepository.getActiveUser(targetId);
        ensureNotOwner(target);

        target.setRole(Role.USER);
        target.softDelete(Instant.now());
        userRepository.save(target);
        // issue #178: same stamp as changeRole — without it the removed
        // account's token keeps authenticating until exp (only getActiveUser
        // paths 404 it; the demotion above closes the admin routes alone).
        // Self-restore is unaffected: login works for soft-deleted accounts,
        // and the fresh token postdates the stamp.
        tokenDenylist.revokeUserSessions(targetId);
    }

    private static void ensureNotOwner(User target) {
        if (target.getRole() == Role.OWNER) {
            throw forbidden("OWNER accounts cannot be changed or removed");
        }
    }

    private static SortOrder parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return new SortOrder("id", true);
        }

        String[] parts = sort.split(",", -1);
        String field = parts[0].trim();
        if (parts.length > 2 || !SORT_FIELDS.contains(field)) {
            throw badRequest("sort must be one of id, email, username, role, createdAt, "
                + "optionally followed by ,asc or ,desc");
        }
        if (parts.length == 1) {
            return new SortOrder(field, true);
        }

        String direction = parts[1].trim();
        if (direction.equalsIgnoreCase("asc")) {
            return new SortOrder(field, true);
        }
        if (direction.equalsIgnoreCase("desc")) {
            return new SortOrder(field, false);
        }
        throw badRequest("sort direction must be asc or desc");
    }

    private static Specification<User> matching(
            String search, Role role, boolean includeDeleted, SortOrder order) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (!includeDeleted) {
                predicates.add(cb.isNull(root.get("deletedAt")));
            }

            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }

            if (search != null && !search.isBlank()) {
                String pattern = "%" + escapeLike(search.trim().toLowerCase()) + "%";
                predicates.add(cb.or(
                    cb.like(root.<Long>get("id").cast(String.class), pattern, '\\'),
                    cb.like(cb.lower(root.get("username")), pattern, '\\'),
                    cb.like(cb.lower(root.get("email")), pattern, '\\')
                ));
            }

            Expression<?> key = order.field().equals("role") ? roleRank(root, cb) : root.get(order.field());
            if (order.field().equals("id")) {
                query.orderBy(order.ascending() ? cb.asc(key) : cb.desc(key));
            } else {
                // id breaks ties so rows don't shift between pages
                query.orderBy(order.ascending() ? cb.asc(key) : cb.desc(key), cb.asc(root.get("id")));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Expression<Integer> roleRank(Root<User> root, CriteriaBuilder cb) {
        CriteriaBuilder.SimpleCase<Role, Integer> rank = cb.selectCase(root.get("role"));
        for (int i = 0; i < ROLE_RANK.size(); i++) {
            rank.when(ROLE_RANK.get(i), i);
        }
        return rank.otherwise(ROLE_RANK.size());
    }

    // search text is matched literally: LIKE wildcards in it are escaped
    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    private static ResponseStatusException forbidden(String reason) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, reason);
    }

    private record SortOrder(String field, boolean ascending) {
    }
}
