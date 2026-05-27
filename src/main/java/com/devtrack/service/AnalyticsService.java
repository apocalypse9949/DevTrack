package com.devtrack.service;

import com.devtrack.dto.AnalyticsDtos.*;
import com.devtrack.entity.*;
import com.devtrack.exception.ResourceNotFoundException;
import com.devtrack.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ReleaseRepository releaseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DeploymentHistoryRepository deploymentHistoryRepository;

    public SuccessRateResponse getDeploymentSuccessRate(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + projectId));

        long totalDeployments = deploymentHistoryRepository.countDeploymentsByProjectId(projectId);
        long successfulDeployments = deploymentHistoryRepository.countSuccessfulDeploymentsByProjectId(projectId);

        double successRate = 0.0;
        if (totalDeployments > 0) {
            successRate = ((double) successfulDeployments / totalDeployments) * 100.0;
            successRate = Math.round(successRate * 100.0) / 100.0;
        }

        return new SuccessRateResponse(
                project.getId(),
                project.getName(),
                totalDeployments,
                successfulDeployments,
                successRate
        );
    }

    public ReleaseFrequencyResponse getReleaseFrequency(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + projectId));

        List<Release> releases = releaseRepository.findByProjectId(projectId, Pageable.unpaged()).getContent();

        Map<String, Long> stageBreakdown = releases.stream()
                .collect(Collectors.groupingBy(
                        release -> release.getStage().name(),
                        Collectors.counting()
                ));

        for (ReleaseStage stage : ReleaseStage.values()) {
            stageBreakdown.putIfAbsent(stage.name(), 0L);
        }

        return new ReleaseFrequencyResponse(
                project.getId(),
                project.getName(),
                releases.size(),
                stageBreakdown
        );
    }

    public List<DeveloperActivityResponse> getDeveloperActivityTrends() {
        List<User> developers = userRepository.findByRole(Role.ROLE_DEVELOPER);

        List<DeveloperActivityResponse> trends = new ArrayList<>();

        for (User dev : developers) {
            long projectsCount = projectRepository.countByUserInvolved(dev);

            long releasesCreated = releaseRepository.countByCreator(dev);
            long deploymentsTriggered = deploymentHistoryRepository.countByDeployedBy(dev);

            trends.add(new DeveloperActivityResponse(
                    dev.getId(),
                    dev.getUsername(),
                    dev.getEmail(),
                    projectsCount,
                    releasesCreated,
                    deploymentsTriggered
            ));
        }

        trends.sort((t1, t2) -> Long.compare(
                t2.releasesCreatedCount() + t2.deploymentsTriggeredCount(),
                t1.releasesCreatedCount() + t1.deploymentsTriggeredCount()
        ));

        return trends;
    }
}
