package com.devtrack.release.repository;

import com.devtrack.release.entity.Release;
import com.devtrack.release.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ReleaseRepository extends JpaRepository<Release, UUID> {
    @Query("SELECT r FROM Release r JOIN FETCH r.project JOIN FETCH r.creator WHERE r.project.id = :projectId")
    Page<Release> findByProjectId(@Param("projectId") UUID projectId, Pageable pageable);

    boolean existsByProjectIdAndVersion(UUID projectId, String version);
    long countByCreator(User creator);
}
