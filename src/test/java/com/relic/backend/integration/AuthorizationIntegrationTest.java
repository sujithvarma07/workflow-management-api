package com.relic.backend.integration;

import com.relic.backend.dto.*;
import com.relic.backend.entity.ProjectMemberRole;
import com.relic.backend.entity.UserRole;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the real HTTP stack (real JWTs from real logins, not @WithMockUser)
 * through the authorization boundaries this project relies on: role-based
 * access control, the two ownership-based rules (project ownership, task
 * assignment), and the self-approval business rule. Each test asserts both
 * that the forbidden case is actually rejected and that the legitimate case
 * actually succeeds, so a rule that's accidentally too strict would fail
 * here just as loudly as one that's too permissive.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthorizationIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String adminToken;
    private String managerAToken;
    private String managerBToken;
    private String dev1Token;
    private String dev2Token;

    private Long managerAId;
    private Long dev2Id;
    private Long projectAlphaId;
    private Long taskId;
    private Long taskForSelfApprovalId;
    private Long taskForCrossApprovalId;

    @BeforeAll
    void setUpFixtures() {
        adminToken = login("test-admin", "test-admin-pass-1");

        managerAId = createUser("manager.a", "manager.a@example.com", "Manager A", UserRole.MANAGER, "ManagerPass1");
        createUser("manager.b", "manager.b@example.com", "Manager B", UserRole.MANAGER, "ManagerPass1");
        dev2Id = registerAndActivate("dev.two", "dev.two@example.com", "Dev Two", "DevPass123");
        registerAndActivate("dev.one", "dev.one@example.com", "Dev One", "DevPass123");

        managerAToken = login("manager.a", "ManagerPass1");
        managerBToken = login("manager.b", "ManagerPass1");
        dev1Token = login("dev.one", "DevPass123");
        dev2Token = login("dev.two", "DevPass123");

        // Manager A creates the project, which auto-enrolls them as its
        // OWNER member (ProjectServiceImpl.create) - that's the membership
        // the ownership tests below depend on.
        ResponseEntity<ProjectResponse> projectResponse = postAs(managerAToken, "/api/v1/projects",
                new ProjectRequest("Alpha", "Test fixture project", null), ProjectResponse.class);
        assertThat(projectResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        projectAlphaId = projectResponse.getBody().id();

        ResponseEntity<TaskResponse> taskResponse = postAs(managerAToken, "/api/v1/tasks",
                new TaskRequest(projectAlphaId, "Implement feature X", null, null, null, null), TaskResponse.class);
        assertThat(taskResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        taskId = taskResponse.getBody().id();

        ResponseEntity<AssignmentResponse> assignResponse = postAs(managerAToken,
                "/api/v1/tasks/" + taskId + "/assignments", new AssignmentRequest(findDevOneId()), AssignmentResponse.class);
        assertThat(assignResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Separate tasks for the two approval tests below - only one
        // PENDING approval is allowed per task at a time, and the
        // self-approval test's request is never successfully decided
        // (that's the point of the test), so it would otherwise block the
        // second test from creating its own approval request on a shared
        // task regardless of which order the two tests happen to run in.
        taskForSelfApprovalId = createTaskInAlpha("Task for self-approval test");
        taskForCrossApprovalId = createTaskInAlpha("Task for cross-approval test");
    }

    private Long createTaskInAlpha(String title) {
        ResponseEntity<TaskResponse> response = postAs(managerAToken, "/api/v1/tasks",
                new TaskRequest(projectAlphaId, title, null, null, null, null), TaskResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().id();
    }

    @Test
    void anonymousRequestToProtectedEndpointIsRejected() {
        ResponseEntity<String> response = restTemplate.getForEntity(url("/api/v1/projects"), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void developerCannotPerformAdminOnlyAction() {
        ResponseEntity<String> response = exchangeAs(dev1Token, HttpMethod.PATCH,
                "/api/v1/users/" + managerAId + "/status", new UserStatusUpdateRequest(
                        com.relic.backend.entity.UserStatus.SUSPENDED), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void developerCanChangeStatusOfTaskTheyAreAssignedTo() {
        ResponseEntity<TaskResponse> response = exchangeAs(dev1Token, HttpMethod.PATCH,
                "/api/v1/tasks/" + taskId + "/status",
                new TaskStatusUpdateRequest(com.relic.backend.entity.TaskStatus.IN_PROGRESS), TaskResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(com.relic.backend.entity.TaskStatus.IN_PROGRESS);
    }

    @Test
    void developerCannotChangeStatusOfTaskTheyAreNotAssignedTo() {
        ResponseEntity<String> response = exchangeAs(dev2Token, HttpMethod.PATCH,
                "/api/v1/tasks/" + taskId + "/status",
                new TaskStatusUpdateRequest(com.relic.backend.entity.TaskStatus.BLOCKED), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void nonOwnerManagerCannotAddProjectMember() {
        ResponseEntity<String> response = postAs(managerBToken, "/api/v1/projects/" + projectAlphaId + "/members",
                new ProjectMemberRequest(dev2Id, ProjectMemberRole.CONTRIBUTOR), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void ownerManagerCanAddProjectMember() {
        ResponseEntity<ProjectMemberResponse> response = postAs(managerAToken,
                "/api/v1/projects/" + projectAlphaId + "/members",
                new ProjectMemberRequest(dev2Id, ProjectMemberRole.CONTRIBUTOR), ProjectMemberResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void managerCannotApproveTheirOwnApprovalRequest() {
        ResponseEntity<ApprovalResponse> requestResponse = postAs(managerAToken,
                "/api/v1/tasks/" + taskForSelfApprovalId + "/approvals", new ApprovalRequest("Please review"), ApprovalResponse.class);
        assertThat(requestResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long approvalId = requestResponse.getBody().id();

        ResponseEntity<String> decisionResponse = exchangeAs(managerAToken, HttpMethod.PATCH,
                "/api/v1/approvals/" + approvalId + "/decision",
                new ApprovalDecisionRequest(com.relic.backend.entity.ApprovalStatus.APPROVED, null), String.class);
        assertThat(decisionResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void differentManagerCanApproveSomeoneElsesApprovalRequest() {
        ResponseEntity<ApprovalResponse> requestResponse = postAs(managerAToken,
                "/api/v1/tasks/" + taskForCrossApprovalId + "/approvals", new ApprovalRequest("Second review"), ApprovalResponse.class);
        assertThat(requestResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long approvalId = requestResponse.getBody().id();

        ResponseEntity<ApprovalResponse> decisionResponse = exchangeAs(managerBToken, HttpMethod.PATCH,
                "/api/v1/approvals/" + approvalId + "/decision",
                new ApprovalDecisionRequest(com.relic.backend.entity.ApprovalStatus.APPROVED, "Looks good"),
                ApprovalResponse.class);
        assertThat(decisionResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(decisionResponse.getBody().status()).isEqualTo(com.relic.backend.entity.ApprovalStatus.APPROVED);
    }

    // --- fixture helpers ---

    private Long createUser(String username, String email, String fullName, UserRole role, String password) {
        ResponseEntity<UserResponse> response = postAs(adminToken, "/api/v1/users",
                new UserCreateRequest(username, email, fullName, role, password), UserResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().id();
    }

    private Long registerAndActivate(String username, String email, String fullName, String password) {
        ResponseEntity<RegisterResponse> response = restTemplate.postForEntity(
                url("/api/v1/auth/register"), new RegisterRequest(username, email, fullName, password),
                RegisterResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        RegisterResponse body = response.getBody();

        ResponseEntity<Void> activateResponse = restTemplate.postForEntity(
                url("/api/v1/auth/activate"), new ActivateAccountRequest(body.activationToken()), Void.class);
        assertThat(activateResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        return body.id();
    }

    private String login(String username, String password) {
        ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                url("/api/v1/auth/login"), new LoginRequest(username, password), TokenResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().accessToken();
    }

    private Long findDevOneId() {
        ResponseEntity<UserResponse> response = exchangeAs(dev1Token, HttpMethod.GET, "/api/v1/users/me", null, UserResponse.class);
        return response.getBody().id();
    }

    private <T> ResponseEntity<T> postAs(String token, String path, Object body, Class<T> responseType) {
        return restTemplate.exchange(url(path), HttpMethod.POST, new HttpEntity<>(body, authHeaders(token)), responseType);
    }

    private <T> ResponseEntity<T> exchangeAs(String token, HttpMethod method, String path, Object body, Class<T> responseType) {
        return restTemplate.exchange(url(path), method, new HttpEntity<>(body, authHeaders(token)), responseType);
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
