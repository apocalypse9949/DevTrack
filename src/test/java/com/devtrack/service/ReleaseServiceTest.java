package com.devtrack.service;

import com.devtrack.dto.ReleaseDtos.*;
import com.devtrack.entity.*;
import com.devtrack.exception.BadRequestException;
import com.devtrack.repository.DeploymentHistoryRepository;
import com.devtrack.repository.ProjectRepository;
import com.devtrack.repository.ReleaseRepository;
import com.devtrack.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ReleaseServiceTest {

    @Mock
    private ReleaseRepository releaseRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DeploymentHistoryRepository deploymentHistoryRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private ReleaseService releaseService;

    private User manager;
    private Project project;
    private Release release;

    @BeforeEach
    void setUp() {
        manager = User.builder()
                .id(UUID.randomUUID())
                .username("manager_test")
                .role(Role.ROLE_RELEASE_MANAGER)
                .build();

        project = Project.builder()
                .id(UUID.randomUUID())
                .name("DevTrack Core")
                .owner(manager)
                .build();

        release = Release.builder()
                .id(UUID.randomUUID())
                .version("v1.0.0")
                .project(project)
                .stage(ReleaseStage.CODE_REVIEW)
                .status("IN_PROGRESS")
                .creator(manager)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void testCreateRelease_Success() {
        ReleaseRequest request = new ReleaseRequest(project.getId(), "v1.0.0", "Initial stable release");
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(userRepository.findByUsername(manager.getUsername())).thenReturn(Optional.of(manager));
        when(releaseRepository.existsByProjectIdAndVersion(project.getId(), "v1.0.0")).thenReturn(false);
        when(releaseRepository.save(any(Release.class))).thenReturn(release);

        ReleaseResponse response = releaseService.createRelease(request, manager.getUsername());

        assertNotNull(response);
        assertEquals("v1.0.0", response.version());
        verify(releaseRepository, times(1)).save(any(Release.class));
    }

    @Test
    void testTransitionStage_Success() {
        TransitionStageRequest request = new TransitionStageRequest(ReleaseStage.TESTING);
        when(releaseRepository.findById(release.getId())).thenReturn(Optional.of(release));
        when(userRepository.findByUsername(manager.getUsername())).thenReturn(Optional.of(manager));
        when(releaseRepository.save(any(Release.class))).thenReturn(release);

        ReleaseResponse response = releaseService.transitionStage(release.getId(), request, manager.getUsername());

        assertNotNull(response);
        verify(releaseRepository, times(1)).save(any(Release.class));
    }

    @Test
    void testTransitionStage_FinalStage_ThrowsException() {
        release.setStage(ReleaseStage.DEPLOYED);
        TransitionStageRequest request = new TransitionStageRequest(ReleaseStage.TESTING);
        when(releaseRepository.findById(release.getId())).thenReturn(Optional.of(release));
        when(userRepository.findByUsername(manager.getUsername())).thenReturn(Optional.of(manager));

        assertThrows(BadRequestException.class, () -> 
                releaseService.transitionStage(release.getId(), request, manager.getUsername()));
        verify(releaseRepository, never()).save(any(Release.class));
    }

    @Test
    void testDeployRelease_Success() {
        DeploymentRequest request = new DeploymentRequest("PRODUCTION", DeploymentOutcome.SUCCESS, "Flawless deployment run");
        when(releaseRepository.findById(release.getId())).thenReturn(Optional.of(release));
        when(userRepository.findByUsername(manager.getUsername())).thenReturn(Optional.of(manager));
        when(deploymentHistoryRepository.save(any(DeploymentHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DeploymentHistoryResponse response = releaseService.deployRelease(release.getId(), request, manager.getUsername());

        assertNotNull(response);
        assertEquals(ReleaseStage.DEPLOYED, release.getStage());
        assertEquals("COMPLETED", release.getStatus());
        verify(deploymentHistoryRepository, times(1)).save(any(DeploymentHistory.class));
    }
}
