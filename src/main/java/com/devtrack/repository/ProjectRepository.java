package com.devtrack.repository;

import com.devtrack.entity.Project;
import com.devtrack.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    boolean existsByName(String name);
    Page<Project> findByMembersContaining(User user, Pageable pageable);
    Page<Project> findByOwner(User owner, Pageable pageable);

    @Query("SELECT DISTINCT p FROM Project p LEFT JOIN p.members m WHERE p.owner = :user OR m = :user")
    Page<Project> findByUserInvolved(@Param("user") User user, Pageable pageable);

    @Query("SELECT COUNT(DISTINCT p) FROM Project p LEFT JOIN p.members m WHERE p.owner = :user OR m = :user")
    long countByUserInvolved(@Param("user") User user);

    @Query("SELECT p FROM Project p LEFT JOIN FETCH p.members WHERE p.id = :id")
    Optional<Project> findByIdWithMembers(@Param("id") UUID id);
}
