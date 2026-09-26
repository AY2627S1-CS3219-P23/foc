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
import foc.user.exception.UserNotFoundException;
import foc.user.repository.UserRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

@Service
public class AdminService {

    public static final int DEFAULT_PAGE_SIZE = 100;
    private static final Set<Integer> PAGE_SIZES = Set.of(20, 50, DEFAULT_PAGE_SIZE);

    private static final Set<String> SORT_FIELDS = Set.of("id", "email", "username", "role", "createdAt");

    // sorting by role follows rank, lowest first
    private static final List<Role> ROLE_RANK = List.of(Role.USER, Role.ADMIN, Role.OWNER);

    private final UserRepository userRepository;

    public AdminService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // all accounts, soft-deleted ones included. sort is "field" or "field,asc|desc";
    // null sorts by id ascending
    @Transactional(readOnly = true)
    public Page<UserResponse> listUsers(String search, Role role, String sort, int page, int size) {
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
            .findAll(matching(search, role, order), PageRequest.of(page, size))
            .map(UserResponse::from);
    }

    // promote to ADMIN or demote to USER. Returns the user unchanged if they already hold the role
    @Transactional
    public UserResponse changeRole(Long targetId, Role newRole) {
        if (newRole == Role.OWNER) {
            throw forbidden("OWNER can only be granted through owner setup");
        }

        User target = findActiveUser(targetId);
        ensureNotOwner(target);

        if (target.getRole() == newRole) {
            return UserResponse.from(target);
        }

        target.setRole(newRole);
        return UserResponse.from(userRepository.save(target));
    }

    // soft delete, same as self-deletion: the account stays recoverable for 30 days.
    // Sets deleted_at directly until the soft-delete path from #93 lands
    @Transactional
    public void removeUser(Long callerId, Long targetId) {
        if (targetId.equals(callerId)) {
            throw forbidden("Use DELETE /users/me to delete your own account");
        }

        User target = findActiveUser(targetId);
        ensureNotOwner(target);

        target.setDeletedAt(Instant.now());
        userRepository.save(target);
    }

    private User findActiveUser(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
            .orElseThrow(UserNotFoundException::new);
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

    private static Specification<User> matching(String search, Role role, SortOrder order) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

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
