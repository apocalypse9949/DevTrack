package com.devtrack.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "deployment_histories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeploymentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "release_id", nullable = false)
    private Release release;

    @Column(nullable = false, length = 50)
    private String environment; // e.g., "DEV", "STAGING", "PRODUCTION"

    @Column(name = "deployed_at", nullable = false)
    private LocalDateTime deployedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DeploymentOutcome outcome;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deployed_by")
    private User deployedBy;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @PrePersist
    protected void onCreate() {
        if (this.deployedAt == null) {
            this.deployedAt = LocalDateTime.now();
        }
    }
}
