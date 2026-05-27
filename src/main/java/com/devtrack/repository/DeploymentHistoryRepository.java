package com.devtrack.repository;

import com.devtrack.entity.DeploymentHistory;
import com.devtrack.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DeploymentHistoryRepository extends JpaRepository<DeploymentHistory, UUID> {
    List<DeploymentHistory> findByReleaseIdOrderByDeployedAtDesc(UUID releaseId);
    Page<DeploymentHistory> findByReleaseProjectId(UUID projectId, Pageable pageable);
    long countByDeployedBy(User deployedBy);

    @Query("SELECT COUNT(d) FROM DeploymentHistory d WHERE d.release.project.id = :projectId")
    long countDeploymentsByProjectId(@Param("projectId") UUID projectId);

    @Query("SELECT COUNT(d) FROM DeploymentHistory d WHERE d.release.project.id = :projectId AND d.outcome = com.devtrack.entity.DeploymentOutcome.SUCCESS")
    long countSuccessfulDeploymentsByProjectId(@Param("projectId") UUID projectId);
}
