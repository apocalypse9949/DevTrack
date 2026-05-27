package com.devtrack.service;

import com.devtrack.dto.AuthDtos.UserResponse;
import com.devtrack.dto.ProjectDtos.*;
import com.devtrack.entity.Project;
import com.devtrack.entity.Role;
import com.devtrack.entity.User;
import com.devtrack.exception.BadRequestException;
import com.devtrack.exception.ResourceNotFoundException;
import com.devtrack.exception.UnauthorizedException;
import com.devtrack.repository.ProjectRepository;
import com.devtrack.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private UserService userService;

    public ProjectResponse createProject(ProjectRequest request, String ownerUsername) {
        User owner = userRepository.findByUsername(ownerUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Owner user not found: " + ownerUsername));

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
        User user = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Current user not found: " + currentUsername));

        Page<Project> projects;
        if (user.getRole() == Role.ROLE_ADMIN) {
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

        User user = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Current user not found: " + currentUsername));

        // Enforce boundary checks
        if (user.getRole() != Role.ROLE_ADMIN && 
            !project.getOwner().equals(user) && 
            !project.getMembers().contains(user)) {
            throw new UnauthorizedException("You are not authorized to view this project");
        }

        return convertToResponse(project);
    }

    public ProjectResponse assignMember(UUID projectId, UUID memberId, String currentUsername) {
        Project project = projectRepository.findByIdWithMembers(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + projectId));

        User currentUser = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Current user not found: " + currentUsername));

        // Only Admin or Project Owner can add members
        if (currentUser.getRole() != Role.ROLE_ADMIN && !project.getOwner().equals(currentUser)) {
            throw new UnauthorizedException("Only admins and project owners can manage members");
        }

        User member = userRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member user not found with id: " + memberId));

        if (member.getRole() != Role.ROLE_DEVELOPER && member.getRole() != Role.ROLE_RELEASE_MANAGER) {
            throw new BadRequestException("Only developers or release managers can be assigned to a project");
        }

        project.getMembers().add(member);
        Project savedProject = projectRepository.save(project);
        return convertToResponse(savedProject);
    }

    public ProjectResponse removeMember(UUID projectId, UUID memberId, String currentUsername) {
        Project project = projectRepository.findByIdWithMembers(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + projectId));

        User currentUser = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Current user not found: " + currentUsername));

        if (currentUser.getRole() != Role.ROLE_ADMIN && !project.getOwner().equals(currentUser)) {
            throw new UnauthorizedException("Only admins and project owners can manage members");
        }

        User member = userRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member user not found with id: " + memberId));

        project.getMembers().remove(member);
        Project savedProject = projectRepository.save(project);
        return convertToResponse(savedProject);
    }

    public void deleteProject(UUID id, String currentUsername) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + id));

        User currentUser = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Current user not found: " + currentUsername));

        if (currentUser.getRole() != Role.ROLE_ADMIN && !project.getOwner().equals(currentUser)) {
            throw new UnauthorizedException("Only admins and project owners can delete projects");
        }

        projectRepository.delete(project);
    }

    public ProjectResponse convertToResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                userService.convertToResponse(project.getOwner()),
                project.getMembers().stream()
                        .map(userService::convertToResponse)
                        .collect(Collectors.toSet()),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }
}
