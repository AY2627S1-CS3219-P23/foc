/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-26
Scope: Generated request body for the admin role change, PATCH /users/{id}
       (issue #96), following the allow-list DTO pattern: role is the only
       field it binds.
Author review: Ryan reviewed to ensure it follows the team's decisions.
2026-09-29 (Claude Code, Opus 5.5), issue #147: message on the role rule, now
that validation errors report each rule's message.
*/

package foc.user.dto;

import foc.user.entity.Role;
import jakarta.validation.constraints.NotNull;

public record UpdateRoleRequest(
    @NotNull(message = "Role is required") Role role
) {
}
