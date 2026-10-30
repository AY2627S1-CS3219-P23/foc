/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-10-28
Scope: issue #267, revised USR-8 (team decision): the one way a user row
       is inserted. It writes user.created in the same transaction, so
       every path that creates a user (sign-up, owner setup, the demo
       seeder) publishes the event and a new path cannot forget it.
Author review: Ryan Ang, pending pull request review.
*/

package foc.user.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import foc.user.entity.User;
import foc.user.repository.UserRepository;

/**
 * Inserts a new user and writes its {@code user.created} to the outbox in
 * the caller's transaction (USR-2, USR-8), so the account exists if and
 * only if the event will be sent and credit-service opens its credit
 * account. Every insert of a user row goes through here; a
 * {@code DataIntegrityViolationException} (a taken email or username)
 * reaches the caller before any event is written.
 */
@Service
public class UserCreation {

    private final UserRepository userRepository;
    private final EventOutbox eventOutbox;

    public UserCreation(UserRepository userRepository, EventOutbox eventOutbox) {
        this.userRepository = userRepository;
        this.eventOutbox = eventOutbox;
    }

    // MANDATORY, as EventOutbox is: the row and its event commit together
    // or not at all
    @Transactional(propagation = Propagation.MANDATORY)
    public User create(User user) {
        // flushed, so a duplicate fails here and the id is assigned
        User created = userRepository.saveAndFlush(user);
        eventOutbox.userCreated(created.getId());
        return created;
    }
}
