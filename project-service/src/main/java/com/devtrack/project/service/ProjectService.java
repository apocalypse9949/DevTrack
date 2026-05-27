package com.devtrack.project.service;

import com.devtrack.project.dto.ProjectDtos.*;
import com.devtrack.project.entity.Project;
import com.devtrack.project.entity.User;
import com.devtrack.project.exception.BadRequestException;
import com.devtrack.project.exception.ResourceNotFoundException;
import com.devtrack.project.exception.UnauthorizedException;
import com.devtrack.project.repository.ProjectRepository;
import com.devtrack.project.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class ProjectService {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WebClient.Builder webClientBuilder;

    private User getOrCreateUserReplicaByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseGet(() -> {
                    try {
                        UserResponse response = webClientBuilder.build()
                                .get()
                                .uri("http://auth-service/api/v1/auth/users/username/" + username)
                                .retrieve()
                                .bodyToMono(UserResponse.class)
                                .block();

                        if (response == null) {
                            throw new ResourceNotFoundException("User not found in auth service: " + username);
                        }

                        User replica = User.builder()
                                .id(response.id())
                                .username(response.username())
                                .email(response.email())
                                .role(response.role())
                                .build();

                        return userRepository.save(replica);
                    } catch (Exception e) {
                        throw new ResourceNotFoundException("Failed to fetch user profile from identity service: " + username);
                    }
                });
    }

    private User getOrCreateUserReplicaById(UUID userId) {
        return userRepository.findById(userId)
                .orElseGet(() -> {
                    try {
                        UserResponse response = webClientBuilder.build()
                                .get()
                                .uri("http://auth-service/api/v1/auth/users/" + userId)
                                .retrieve()
                                .bodyToMono(UserResponse.class)
                                .block();

                        if (response == null) {
                            throw new ResourceNotFoundException("User not found in auth service with id: " + userId);
                        }

                        User replica = User.builder()
                                .id(response.id())
                                .username(response.username())
                                .email(response.email())
                                .role(response.role())
                                .build();

                        return userRepository.save(replica);
                    } catch (Exception e) {
                        throw new ResourceNotFoundException("Failed to fetch user profile from identity service with id: " + userId);
                    }
                });
    }

    public ProjectResponse createProject(ProjectRequest request, String ownerUsername) {
        User owner = getOrCreateUserReplicaByUsername(ownerUsername);

        if (projectRepository.existsByName(request.name())) {
            throw new BadRequestException("Project name '" + request.name() + "' is already taken");
        }

        Project project = Project.builder()
                .name(request.name())
                .description(request.description())
                .owner(owner)
                .build();

        Project savedProject = projectRepository.save(project);
        return convertToResponse(savedProject);
    }

    @Transactional(readOnly = true)
    public Page<ProjectResponse> getProjects(Pageable pageable, String currentUsername) {
        User user = getOrCreateUserReplicaByUsername(currentUsername);

        Page<Project> projects;
        if ("ROLE_ADMIN".equals(user.getRole())) {
            projects = projectRepository.findAll(pageable);
        } else {
            projects = projectRepository.findByUserInvolved(user, pageable);
        }

        return projects.map(this::convertToResponse);
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProjectById(UUID id, String currentUsername) {
        Project project = projectRepository.findByIdWithMembers(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + id));

        User user = getOrCreateUserReplicaByUsername(currentUsername);

        if (!"ROLE_ADMIN".equals(user.getRole()) && 
            !project.getOwner().equals(user) && 
            !project.getMembers().contains(user)) {
            throw new UnauthorizedException("You are not authorized to view this project");
        }

        return convertToResponse(project);
    }

    public ProjectResponse assignMember(UUID projectId, UUID memberId, String currentUsername) {
        Project project = projectRepository.findByIdWithMembers(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + projectId));

        User currentUser = getOrCreateUserReplicaByUsername(currentUsername);

        if (!"ROLE_ADMIN".equals(currentUser.getRole()) && !project.getOwner().equals(currentUser)) {
            throw new UnauthorizedException("Only admins and project owners can manage members");
        }

        User member = getOrCreateUserReplicaById(memberId);

        if (!"ROLE_DEVELOPER".equals(member.getRole()) && !"ROLE_RELEASE_MANAGER".equals(member.getRole())) {
            throw new BadRequestException("Only developers or release managers can be assigned to a project");
        }

        project.getMembers().add(member);
        Project savedProject = projectRepository.save(project);
        return convertToResponse(savedProject);
    }

    public ProjectResponse removeMember(UUID projectId, UUID memberId, String currentUsername) {
        Project project = projectRepository.findByIdWithMembers(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + projectId));

        User currentUser = getOrCreateUserReplicaByUsername(currentUsername);

        if (!"ROLE_ADMIN".equals(currentUser.getRole()) && !project.getOwner().equals(currentUser)) {
            throw new UnauthorizedException("Only admins and project owners can manage members");
        }

        User member = getOrCreateUserReplicaById(memberId);

        project.getMembers().remove(member);
        Project savedProject = projectRepository.save(project);
        return convertToResponse(savedProject);
    }

    public void deleteProject(UUID id, String currentUsername) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + id));

        User currentUser = getOrCreateUserReplicaByUsername(currentUsername);

        if (!"ROLE_ADMIN".equals(currentUser.getRole()) && !project.getOwner().equals(currentUser)) {
            throw new UnauthorizedException("Only admins and project owners can delete projects");
        }

        projectRepository.delete(project);
    }

    public ProjectResponse convertToResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                convertToUserResponse(project.getOwner()),
                project.getMembers().stream()
                        .map(this::convertToUserResponse)
                        .collect(Collectors.toSet()),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }

    private UserResponse convertToUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole()
        );
    }

    @Transactional(readOnly = true)
    public long getProjectCountByUser(UUID userId) {
        User user = getOrCreateUserReplicaById(userId);
        return projectRepository.countByUserInvolved(user);
    }
}
