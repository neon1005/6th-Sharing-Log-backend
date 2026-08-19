package gdg.sharinglog;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import gdg.sharinglog.domain.GroupInvitation;
import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.MemberStatus;
import gdg.sharinglog.domain.OAuthProvider;
import gdg.sharinglog.domain.SharingGroup;
import gdg.sharinglog.domain.User;
import gdg.sharinglog.repository.GroupInvitationRepository;
import gdg.sharinglog.repository.GroupMemberRepository;
import gdg.sharinglog.repository.SharingGroupRepository;
import gdg.sharinglog.repository.UserRepository;
import gdg.sharinglog.service.invitation.InvitationCodeHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "app.public-base-url=https://sharing.example/")
@AutoConfigureMockMvc
@Transactional
class GroupInvitationApiTest {

    private static final String OWNER_PROVIDER_ID = "invitation-owner-google-id";
    private static final String MEMBER_PROVIDER_ID = "invitation-member-google-id";
    private static final String NON_MEMBER_PROVIDER_ID = "invitation-non-member-google-id";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    SharingGroupRepository groupRepository;

    @Autowired
    GroupMemberRepository groupMemberRepository;

    @Autowired
    GroupInvitationRepository invitationRepository;

    @Autowired
    InvitationCodeHasher codeHasher;

    private User owner;
    private SharingGroup group;

    @BeforeEach
    void setUpGroup() {
        owner = userRepository.save(User.builder()
                .provider(OAuthProvider.GOOGLE)
                .providerUserId(OWNER_PROVIDER_ID)
                .email("invitation-owner@example.com")
                .build());
        User member = userRepository.save(User.builder()
                .provider(OAuthProvider.GOOGLE)
                .providerUserId(MEMBER_PROVIDER_ID)
                .email("invitation-member@example.com")
                .build());
        userRepository.save(User.builder()
                .provider(OAuthProvider.GOOGLE)
                .providerUserId(NON_MEMBER_PROVIDER_ID)
                .email("invitation-non-member@example.com")
                .build());
        group = groupRepository.save(new SharingGroup("초대 테스트 그룹", owner));
        groupMemberRepository.save(GroupMember.owner(group, owner));
        groupMemberRepository.save(GroupMember.member(group, member));
    }

    @Test
    void ownerIssuesReusableInvitationCodeAndLink() throws Exception {
        Instant beforeRequest = Instant.now();

        MvcResult result = mockMvc.perform(post("/api/groups/{groupId}/invitations", group.getId())
                        .with(csrf())
                        .with(oauthUser(OWNER_PROVIDER_ID)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("https://sharing.example/invite/")))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.invitationId", notNullValue()))
                .andExpect(jsonPath("$.groupId").value(group.getId()))
                .andExpect(jsonPath("$.code", matchesPattern("[A-Za-z0-9_-]{22}")))
                .andExpect(jsonPath("$.invitePath", startsWith("/invite/")))
                .andExpect(jsonPath("$.inviteUrl", startsWith("https://sharing.example/invite/")))
                .andExpect(jsonPath("$.createdAt", notNullValue()))
                .andExpect(jsonPath("$.expiresAt", notNullValue()))
                .andReturn();

        GroupInvitation invitation = invitationRepository.findAll().getFirst();
        String rawCode = responseCode(result);
        assertEquals(group.getId(), invitation.getGroup().getId());
        assertEquals(codeHasher.hash(rawCode), invitation.getCodeHash());
        assertNotEquals(rawCode, invitation.getCodeHash());
        assertTrue(invitation.isUsableAt(Instant.now()));
        assertTrue(!invitation.getExpiresAt().isBefore(beforeRequest.plus(Duration.ofHours(24))));
        assertTrue(invitation.getExpiresAt().isBefore(beforeRequest.plus(Duration.ofHours(25))));
    }

    @Test
    void issuesDifferentCodesForMultipleInvitations() throws Exception {
        mockMvc.perform(post("/api/groups/{groupId}/invitations", group.getId())
                        .with(csrf())
                        .with(oauthUser(OWNER_PROVIDER_ID)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/groups/{groupId}/invitations", group.getId())
                        .with(csrf())
                        .with(oauthUser(OWNER_PROVIDER_ID)))
                .andExpect(status().isCreated());

        List<GroupInvitation> invitations = invitationRepository.findAll();
        assertEquals(2, invitations.size());
        assertNotEquals(invitations.get(0).getCodeHash(), invitations.get(1).getCodeHash());
    }

    @Test
    void ownerReissuesInvitationForExistingGroupAndNewMemberCanJoin() throws Exception {
        String expiredCode = "AbCdEfGhIjKlMnOpQrStUv";
        Instant requestStartedAt = Instant.now();
        invitationRepository.save(new GroupInvitation(
                group,
                owner,
                codeHasher.hash(expiredCode),
                requestStartedAt.minus(Duration.ofHours(25)),
                requestStartedAt.minus(Duration.ofHours(1))
        ));

        MvcResult result = mockMvc.perform(post(
                                "/api/groups/{groupId}/invitations/reissue",
                                group.getPublicId()
                        )
                        .with(csrf())
                        .with(oauthUser(OWNER_PROVIDER_ID)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("https://sharing.example/invite/")))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.groupId").value(group.getId()))
                .andExpect(jsonPath("$.code", matchesPattern("[A-Za-z0-9_-]{22}")))
                .andExpect(jsonPath("$.inviteUrl", startsWith("https://sharing.example/invite/")))
                .andReturn();

        String reissuedCode = responseCode(result);
        List<GroupInvitation> invitations = invitationRepository.findAllByGroup_Id(group.getId());
        assertEquals(2, invitations.size());
        assertTrue(invitations.stream().noneMatch(invitation -> invitation.isUsableAt(requestStartedAt)
                && invitation.getCodeHash().equals(codeHasher.hash(expiredCode))));

        GroupInvitation reissued = invitations.stream()
                .filter(invitation -> invitation.getCodeHash().equals(codeHasher.hash(reissuedCode)))
                .findFirst()
                .orElseThrow();
        assertTrue(reissued.isUsableAt(Instant.now()));
        assertTrue(!reissued.getExpiresAt().isBefore(requestStartedAt.plus(Duration.ofHours(24))));
        assertTrue(reissued.getExpiresAt().isBefore(requestStartedAt.plus(Duration.ofHours(25))));

        mockMvc.perform(post("/api/invitations/{code}/accept", reissuedCode)
                        .with(csrf())
                        .with(oauthUser(NON_MEMBER_PROVIDER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupPublicId").value(group.getPublicId()))
                .andExpect(jsonPath("$.joinedNow").value(true));

        User joinedUser = userRepository
                .findByProviderAndProviderUserId(OAuthProvider.GOOGLE, NON_MEMBER_PROVIDER_ID)
                .orElseThrow();
        assertTrue(groupMemberRepository
                .findByGroup_IdAndUser_IdAndStatus(
                        group.getId(),
                        joinedUser.getId(),
                        MemberStatus.ACTIVE
                )
                .isPresent());
    }

    @Test
    void reissueKeepsExistingActiveInvitationUsableUntilItsOwnExpiry() throws Exception {
        MvcResult initialResult = mockMvc.perform(
                        post("/api/groups/{groupId}/invitations", group.getId())
                                .with(csrf())
                                .with(oauthUser(OWNER_PROVIDER_ID)))
                .andExpect(status().isCreated())
                .andReturn();
        MvcResult reissuedResult = mockMvc.perform(post(
                                "/api/groups/{groupId}/invitations/reissue",
                                group.getPublicId()
                        )
                        .with(csrf())
                        .with(oauthUser(OWNER_PROVIDER_ID)))
                .andExpect(status().isCreated())
                .andReturn();

        String initialCode = responseCode(initialResult);
        String reissuedCode = responseCode(reissuedResult);
        assertNotEquals(initialCode, reissuedCode);

        mockMvc.perform(get("/api/invitations/{code}", initialCode)
                        .with(oauthUser(NON_MEMBER_PROVIDER_ID)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/invitations/{code}", reissuedCode)
                        .with(oauthUser(NON_MEMBER_PROVIDER_ID)))
                .andExpect(status().isOk());
    }

    @Test
    void regularMemberCannotReissueInvitationForExistingGroup() throws Exception {
        mockMvc.perform(post(
                                "/api/groups/{groupId}/invitations/reissue",
                                group.getPublicId()
                        )
                        .with(csrf())
                        .with(oauthUser(MEMBER_PROVIDER_ID)))
                .andExpect(status().isForbidden());

        assertTrue(invitationRepository.findAll().isEmpty());
    }

    @Test
    void nonMemberCannotReissueInvitationForExistingGroup() throws Exception {
        mockMvc.perform(post(
                                "/api/groups/{groupId}/invitations/reissue",
                                group.getPublicId()
                        )
                        .with(csrf())
                        .with(oauthUser(NON_MEMBER_PROVIDER_ID)))
                .andExpect(status().isForbidden());

        assertTrue(invitationRepository.findAll().isEmpty());
    }

    @Test
    void leftOwnerCannotReissueInvitationForExistingGroup() throws Exception {
        GroupMember ownerMembership = groupMemberRepository
                .findByGroup_IdAndUser_Id(group.getId(), owner.getId())
                .orElseThrow();
        ownerMembership.leave(Instant.now());
        groupMemberRepository.saveAndFlush(ownerMembership);

        mockMvc.perform(post(
                                "/api/groups/{groupId}/invitations/reissue",
                                group.getPublicId()
                        )
                        .with(csrf())
                        .with(oauthUser(OWNER_PROVIDER_ID)))
                .andExpect(status().isForbidden());

        assertTrue(invitationRepository.findAll().isEmpty());
    }

    @Test
    void missingExistingGroupCannotReissueInvitation() throws Exception {
        mockMvc.perform(post(
                                "/api/groups/{groupId}/invitations/reissue",
                                "11111111-1111-4111-8111-111111111111"
                        )
                        .with(csrf())
                        .with(oauthUser(OWNER_PROVIDER_ID)))
                .andExpect(status().isNotFound());

        assertTrue(invitationRepository.findAll().isEmpty());
    }

    @Test
    void regularMemberCannotIssueInvitation() throws Exception {
        mockMvc.perform(post("/api/groups/{groupId}/invitations", group.getId())
                        .with(csrf())
                        .with(oauthUser(MEMBER_PROVIDER_ID)))
                .andExpect(status().isForbidden());

        assertTrue(invitationRepository.findAll().isEmpty());
    }

    @Test
    void nonMemberCannotIssueInvitation() throws Exception {
        mockMvc.perform(post("/api/groups/{groupId}/invitations", group.getId())
                        .with(csrf())
                        .with(oauthUser(NON_MEMBER_PROVIDER_ID)))
                .andExpect(status().isForbidden());

        assertTrue(invitationRepository.findAll().isEmpty());
    }

    @Test
    void leftOwnerCannotIssueInvitation() throws Exception {
        GroupMember ownerMembership = groupMemberRepository
                .findByGroup_IdAndUser_Id(group.getId(), owner.getId())
                .orElseThrow();
        ownerMembership.leave(Instant.now());
        groupMemberRepository.saveAndFlush(ownerMembership);

        mockMvc.perform(post("/api/groups/{groupId}/invitations", group.getId())
                        .with(csrf())
                        .with(oauthUser(OWNER_PROVIDER_ID)))
                .andExpect(status().isForbidden());

        assertTrue(invitationRepository.findAll().isEmpty());
    }

    @Test
    void authenticatedUserMissingFromDatabaseCannotIssueInvitation() throws Exception {
        mockMvc.perform(post("/api/groups/{groupId}/invitations", group.getId())
                        .with(csrf())
                        .with(oauthUser("missing-invitation-user")))
                .andExpect(status().isUnauthorized());

        assertTrue(invitationRepository.findAll().isEmpty());
    }

    @Test
    void missingGroupReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/groups/{groupId}/invitations", Long.MAX_VALUE)
                        .with(csrf())
                        .with(oauthUser(OWNER_PROVIDER_ID)))
                .andExpect(status().isNotFound());

        assertTrue(invitationRepository.findAll().isEmpty());
    }

    @Test
    void invitationIssuanceRequiresCsrfToken() throws Exception {
        mockMvc.perform(post("/api/groups/{groupId}/invitations", group.getId())
                        .with(oauthUser(OWNER_PROVIDER_ID)))
                .andExpect(status().isForbidden());

        assertTrue(invitationRepository.findAll().isEmpty());
    }

    @Test
    void authenticatedClientCanRequestCsrfToken() throws Exception {
        mockMvc.perform(get("/api/auth/csrf")
                        .with(oauthUser(OWNER_PROVIDER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.parameterName").value("_csrf"))
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor oauthUser(String providerUserId) {
        return oauth2Login()
                .clientRegistration(googleClientRegistration())
                .attributes(attributes -> {
                    attributes.put("sub", providerUserId);
                    attributes.put("email", providerUserId + "@example.com");
                });
    }

    private String responseCode(MvcResult result) throws Exception {
        String responseBody = result.getResponse().getContentAsString();
        Matcher matcher = Pattern.compile("\\\"code\\\":\\\"([^\\\"]+)\\\"").matcher(responseBody);
        assertTrue(matcher.find());
        return matcher.group(1);
    }

    private ClientRegistration googleClientRegistration() {
        return ClientRegistration.withRegistrationId("google")
                .clientId("test-google-client-id")
                .clientSecret("test-google-client-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                .userNameAttributeName("sub")
                .clientName("Google")
                .build();
    }
}
