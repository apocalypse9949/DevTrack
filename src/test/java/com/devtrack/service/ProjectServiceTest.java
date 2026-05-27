package com.devtrack.service;

import com.devtrack.dto.ProjectDtos.*;
import com.devtrack.entity.Project;
import com.devtrack.entity.Role;
import com.devtrack.entity.User;
import com.devtrack.exception.BadRequestException;
import com.devtrack.exception.UnauthorizedException;
import com.devtrack.repository.ProjectRepository;
import com.devtrack.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private ProjectService projectService;

    private User owner;
    private User developer;
    private Project project;

    @BeforeEach
    void setUp() {
        owner = User.builder()
                .id(UUID.randomUUID())
                .username("manager_test")
                .email("manager@test.com")
                .role(Role.ROLE_RELEASE_MANAGER)
                .build();

        developer = User.builder()
                .id(UUID.randomUUID())
                .username("developer_test")
                .email("developer@test.com")
                .role(Role.ROLE_DEVELOPER)
                .build();

        project = Project.builder()
                .id(UUID.randomUUID())
                .name("DevTrack Core")
                .description("Release tracking core application")
                .owner(owner)
                .members(new HashSet<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void testCreateProject_Success() {
        ProjectRequest request = new ProjectRequest("DevTrack Core", "Release tracking core application");
        when(userRepository.findByUsername(owner.getUsername())).thenReturn(Optional.of(owner));
        when(projectRepository.existsByName(request.name())).thenReturn(false);
        when(projectRepository.save(any(Project.class))).thenReturn(project);

        ProjectResponse response = projectService.createProject(request, owner.getUsername());

        assertNotNull(response);
        assertEquals(project.getName(), response.name());
        verify(projectRepository, times(1)).save(any(Project.class));
    }

    @Test
    void testCreateProject_NameAlreadyTaken_ThrowsBadRequestException() {
        ProjectRequest request = new ProjectRequest("DevTrack Core", "Release tracking core application");
        when(userRepository.findByUsername(owner.getUsername())).thenReturn(Optional.of(owner));
        when(projectRepository.existsByName(request.name())).thenReturn(true);

        assertThrows(BadRequestException.class, () -> 
                projectService.createProject(request, owner.getUsername()));
        verify(projectRepository, never()).save(any(Project.class));
    }

    @Test
    void testAssignMember_Success() {
        when(projectRepository.findByIdWithMembers(project.getId())).thenReturn(Optional.of(project));
        when(userRepository.findByUsername(owner.getUsername())).thenReturn(Optional.of(owner));
        when(userRepository.findById(developer.getId())).thenReturn(Optional.of(developer));
        when(projectRepository.save(any(Project.class))).thenReturn(project);

        ProjectResponse response = projectService.assignMember(project.getId(), developer.getId(), owner.getUsername());

        assertNotNull(response);
        verify(projectRepository, times(1)).save(project);
    }

    @Test
    void testAssignMember_Unauthorized_ThrowsException() {
        User randomUser = User.builder()
                .id(UUID.randomUUID())
                .username("stranger")
                .role(Role.ROLE_DEVELOPER)
                .build();

        when(projectRepository.findByIdWithMembers(project.getId())).thenReturn(Optional.of(project));
        when(userRepository.findByUsername(randomUser.getUsername())).thenReturn(Optional.of(randomUser));

        assertThrows(UnauthorizedException.class, () -> 
                projectService.assignMember(project.getId(), developer.getId(), randomUser.getUsername()));
        verify(projectRepository, never()).save(any(Project.class));
    }
}
