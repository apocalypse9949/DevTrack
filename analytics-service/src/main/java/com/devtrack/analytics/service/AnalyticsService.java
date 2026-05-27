package com.devtrack.analytics.service;

import com.devtrack.analytics.dto.AnalyticsDtos.*;
import com.devtrack.analytics.exception.ResourceNotFoundException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    @Autowired
    private WebClient.Builder webClientBuilder;

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ExternalProjectResponse(UUID id, String name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ExternalUserResponse(UUID id, String username, String email, String role) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ExternalReleaseResponse(UUID id, String version, String stage) {}

    public SuccessRateResponse getDeploymentSuccessRate(UUID projectId, String currentUsername, String role) {
        ExternalProjectResponse project = fetchProject(projectId, currentUsername, role);

        try {
            Long totalDeployments = webClientBuilder.build()
                    .get()
                    .uri("http://release-service/api/v1/releases/analytics/project/" + projectId + "/deployments-count")
                    .header("X-User-Name", currentUsername)
                    .header("X-User-Role", role)
                    .retrieve()
                    .bodyToMono(Long.class)
                    .block();

            Long successfulDeployments = webClientBuilder.build()
                    .get()
                    .uri("http://release-service/api/v1/releases/analytics/project/" + projectId + "/deployments-success-count")
                    .header("X-User-Name", currentUsername)
                    .header("X-User-Role", role)
                    .retrieve()
                    .bodyToMono(Long.class)
                    .block();

            if (totalDeployments == null) totalDeployments = 0L;
            if (successfulDeployments == null) successfulDeployments = 0L;

            double successRate = 0.0;
            if (totalDeployments > 0) {
                successRate = ((double) successfulDeployments / totalDeployments) * 100.0;
                successRate = Math.round(successRate * 100.0) / 100.0;
            }

            return new SuccessRateResponse(
                    project.id(),
                    project.name(),
                    totalDeployments,
                    successfulDeployments,
                    successRate
            );
        } catch (Exception e) {
            throw new ResourceNotFoundException("Failed to fetch deployment success rates from release service");
        }
    }

    public ReleaseFrequencyResponse getReleaseFrequency(UUID projectId, String currentUsername, String role) {
        ExternalProjectResponse project = fetchProject(projectId, currentUsername, role);

        try {
            ExternalReleaseResponse[] releasesArr = webClientBuilder.build()
                    .get()
                    .uri("http://release-service/api/v1/releases/analytics/project/" + projectId + "/list")
                    .header("X-User-Name", currentUsername)
                    .header("X-User-Role", role)
                    .retrieve()
                    .bodyToMono(ExternalReleaseResponse[].class)
                    .block();

            List<ExternalReleaseResponse> releases = releasesArr == null ? List.of() : Arrays.asList(releasesArr);

            Map<String, Long> stageBreakdown = releases.stream()
                    .collect(Collectors.groupingBy(
                            ExternalReleaseResponse::stage,
                            Collectors.counting()
                    ));

            String[] allStages = {"CODE_REVIEW", "TESTING", "APPROVED", "DEPLOYED", "FAILED"};
            for (String stage : allStages) {
                stageBreakdown.putIfAbsent(stage, 0L);
            }

            return new ReleaseFrequencyResponse(
                    project.id(),
                    project.name(),
                    releases.size(),
                    stageBreakdown
            );
        } catch (Exception e) {
            throw new ResourceNotFoundException("Failed to fetch release frequency data from release service");
        }
    }

    public List<DeveloperActivityResponse> getDeveloperActivityTrends(String currentUsername, String role) {
        try {
            ExternalUserResponse[] devArr = webClientBuilder.build()
                    .get()
                    .uri("http://auth-service/api/v1/auth/users/role/ROLE_DEVELOPER")
                    .header("X-User-Name", currentUsername)
                    .header("X-User-Role", role)
                    .retrieve()
                    .bodyToMono(ExternalUserResponse[].class)
                    .block();

            List<ExternalUserResponse> developers = devArr == null ? List.of() : Arrays.asList(devArr);
            List<DeveloperActivityResponse> trends = new ArrayList<>();

            for (ExternalUserResponse dev : developers) {
                Long projectsCount = webClientBuilder.build()
                        .get()
                        .uri("http://project-service/api/v1/projects/analytics/user/" + dev.id() + "/count")
                        .header("X-User-Name", currentUsername)
                        .header("X-User-Role", role)
                        .retrieve()
                        .bodyToMono(Long.class)
                        .block();

                Long releasesCreated = webClientBuilder.build()
                        .get()
                        .uri("http://release-service/api/v1/releases/analytics/developer/" + dev.id() + "/releases-count")
                        .header("X-User-Name", currentUsername)
                        .header("X-User-Role", role)
                        .retrieve()
                        .bodyToMono(Long.class)
                        .block();

                Long deploymentsTriggered = webClientBuilder.build()
                        .get()
                        .uri("http://release-service/api/v1/releases/analytics/developer/" + dev.id() + "/deployments-count")
                        .header("X-User-Name", currentUsername)
                        .header("X-User-Role", role)
                        .retrieve()
                        .bodyToMono(Long.class)
                        .block();

                if (projectsCount == null) projectsCount = 0L;
                if (releasesCreated == null) releasesCreated = 0L;
                if (deploymentsTriggered == null) deploymentsTriggered = 0L;

                trends.add(new DeveloperActivityResponse(
                        dev.id(),
                        dev.username(),
                        dev.email(),
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
        } catch (Exception e) {
            throw new ResourceNotFoundException("Failed to fetch developer activity metrics from identity, project, or release service");
        }
    }

    private ExternalProjectResponse fetchProject(UUID projectId, String currentUsername, String role) {
        try {
            ExternalProjectResponse project = webClientBuilder.build()
                    .get()
                    .uri("http://project-service/api/v1/projects/" + projectId)
                    .header("X-User-Name", currentUsername)
                    .header("X-User-Role", role)
                    .retrieve()
                    .bodyToMono(ExternalProjectResponse.class)
                    .block();

            if (project == null) {
                throw new ResourceNotFoundException("Project not found with id: " + projectId);
            }
            return project;
        } catch (WebClientResponseException.NotFound ex) {
            throw new ResourceNotFoundException("Project not found with id: " + projectId);
        } catch (Exception e) {
            throw new ResourceNotFoundException("Failed to fetch project details from project service: " + projectId);
        }
    }
}
