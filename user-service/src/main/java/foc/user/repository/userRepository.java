package foc.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import foc.user.entity.User;

@Repository 
public interface UserRepository extends JpaRepository<User, Long> {
    
    // checks whether there are any owners yet
    long countByRole(String role);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    Optional<User> findByEmail(String email);

    // meant to guard against concurrent calls to prevent double owner creation
    @Query(value = "SELECT pg_advisory_xact_lock(1000)", nativeQuery = true)
    void acquireSetupLock();
}
