package gdg.sharinglog;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import gdg.sharinglog.domain.GroupInvitation;
import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.GroupRole;
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
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InvitationAcceptanceWebTest {

    private static final String INVITATION_CODE = "AbCdEfGhIjKlMnOpQrStUv";
    private static final String UNKNOWN_CODE = "ZyXwVuTsRqPoNmLkJiHgFe";
    private static final String OWNER_PROVIDER_ID = "acceptance-owner-google-id";
    private static final String INVITEE_PROVIDER_ID = "acceptance-invitee-google-id";
    private static final String SECOND_INVITEE_PROVIDER_ID = "acceptance-second-google-id";

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
    private User invitee;
    private User secondInvitee;
    private SharingGroup group;
    private GroupInvitation invitation;

    @BeforeEach
    void setUpInvitation() {
        owner = saveUser(OWNER_PROVIDER_ID);
        invitee = saveUser(INVITEE_PROVIDER_ID);
        secondInvitee = saveUser(SECOND_INVITEE_PROVIDER_ID);
        group = groupRepository.save(new SharingGroup("초대 수락 테스트", owner));
        groupMemberRepository.save(GroupMember.owner(group, owner));
        invitation = saveInvitation(
                INVITATION_CODE,
                group,
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(3600)
        );
    }

    @Test
    void anonymousInvitationLinkRedirectsToLogin() throws Exception {
        MvcResult result = mockMvc.perform(get("/invite/{code}", INVITATION_CODE))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", endsWith("/login")))
                .andReturn();

        SavedRequest savedRequest = new HttpSessionRequestCache()
                .getRequest(result.getRequest(), result.getResponse());
        assertNotNull(savedRequest);
        assertEquals(
                "http://localhost/invite/" + INVITATION_CODE + "?continue",
                savedRequest.getRedirectUrl()
        );
    }

    @Test
    void invitationHtmlIsNotBlockedByApiCorsPolicy() throws Exception {
        mockMvc.perform(get("/invite/{code}", INVITATION_CODE)
                        .header("Origin", "https://untrusted.example")
                        .with(oauthUser(INVITEE_PROVIDER_ID)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("초대 수락 테스트")))
                .andExpect(content().string(containsString(
                        "id=\"accept-invitation-button\"")));
    }

    @Test
    void invitationPagePreviewsGroupWithoutJoining() throws Exception {
        mockMvc.perform(get("/invite/{code}", INVITATION_CODE)
                        .with(oauthUser(INVITEE_PROVIDER_ID)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("X-Robots-Tag", "noindex, nofollow"))
                .andExpect(content().string(containsString("초대 수락 테스트")))
                .andExpect(content().string(containsString(
                        "action=\"/invite/" + INVITATION_CODE + "/accept\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("id=\"accept-invitation-button\"")));

        assertTrue(groupMemberRepository
                .findByGroup_IdAndUser_Id(group.getId(), invitee.getId())
                .isEmpty());
    }

    @Test
    void invitationApiPreviewsGroupWithoutJoining() throws Exception {
        mockMvc.perform(get("/api/invitations/{code}", INVITATION_CODE)
                        .with(oauthUser(INVITEE_PROVIDER_ID)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.groupId").value(group.getId()))
                .andExpect(jsonPath("$.groupName").value(group.getName()))
                .andExpect(jsonPath("$.alreadyMember").value(false));

        assertTrue(groupMemberRepository
                .findByGroup_IdAndUser_Id(group.getId(), invitee.getId())
                .isEmpty());
    }

    @Test
    void acceptsInvitationAndRegistersMember() throws Exception {
        mockMvc.perform(post("/invite/{code}/accept", INVITATION_CODE)
                        .with(csrf())
                        .with(oauthUser(INVITEE_PROVIDER_ID)))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location",
                        "/invite/" + INVITATION_CODE + "?result=joined"))
                .andExpect(header().string("Cache-Control", "no-store"));

        GroupMember membership = groupMemberRepository
                .findByGroup_IdAndUser_Id(group.getId(), invitee.getId())
                .orElseThrow();
        assertEquals(GroupRole.MEMBER, membership.getRole());
        assertTrue(invitation.isUsableAt(Instant.now()));

        mockMvc.perform(get("/invite/{code}", INVITATION_CODE)
                        .param("result", "joined")
                        .with(oauthUser(INVITEE_PROVIDER_ID)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("그룹 가입이 완료되었습니다.")))
                .andExpect(content().string(containsString("현재 역할: MEMBER")))
                .andExpect(content().string(containsString(
                        "href=\"/?groupId=" + group.getId() + "#group-members\"")))
                .andExpect(content().string(not(containsString("id=\"accept-invitation-button\""))));
    }

    @Test
    void invitationApiAcceptsInvitationAndReturnsMembership() throws Exception {
        mockMvc.perform(post("/api/invitations/{code}/accept", INVITATION_CODE)
                        .with(csrf())
                        .with(oauthUser(INVITEE_PROVIDER_ID)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.groupId").value(group.getId()))
                .andExpect(jsonPath("$.groupName").value(group.getName()))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.joinedNow").value(true));

        GroupMember membership = groupMemberRepository
                .findByGroup_IdAndUser_Id(group.getId(), invitee.getId())
                .orElseThrow();
        assertEquals(GroupRole.MEMBER, membership.getRole());
    }

    @Test
    void acceptingTwiceDoesNotCreateDuplicateMembership() throws Exception {
        acceptAs(INVITEE_PROVIDER_ID, "joined");
        acceptAs(INVITEE_PROVIDER_ID, "already-member");

        long matchingMemberships = groupMemberRepository.findAll().stream()
                .filter(member -> member.getGroup().getId().equals(group.getId()))
                .filter(member -> member.getUser().getId().equals(invitee.getId()))
                .count();
        assertEquals(1, matchingMemberships);
    }

    @Test
    void allowsAcceptingWhileActiveInAnotherGroup() throws Exception {
        SharingGroup otherGroup = groupRepository.save(new SharingGroup("다른 집", secondInvitee));
        groupMemberRepository.save(GroupMember.owner(otherGroup, secondInvitee));

        mockMvc.perform(post("/api/invitations/{code}/accept", INVITATION_CODE)
                        .with(csrf())
                        .with(oauthUser(SECOND_INVITEE_PROVIDER_ID)))
                .andExpect(status().isOk());

        assertTrue(groupMemberRepository
                .findByGroup_IdAndUser_Id(group.getId(), secondInvitee.getId())
                .isPresent());
    }

    @Test
    void leftMembershipIsJoinableAndReactivatedWithoutCreatingDuplicate() throws Exception {
        GroupMember leftMembership = groupMemberRepository.save(
                GroupMember.member(group, invitee)
        );
        Long membershipId = leftMembership.getId();
        String membershipPublicId = leftMembership.getPublicId();
        Instant leftAt = Instant.now().minusSeconds(60);
        leftMembership.leave(leftAt);
        groupMemberRepository.saveAndFlush(leftMembership);

        mockMvc.perform(get("/api/invitations/{code}", INVITATION_CODE)
                        .with(oauthUser(INVITEE_PROVIDER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyMember").value(false));

        mockMvc.perform(get("/invite/{code}", INVITATION_CODE)
                        .with(oauthUser(INVITEE_PROVIDER_ID)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "id=\"accept-invitation-button\"")));

        Instant beforeAcceptance = Instant.now();
        mockMvc.perform(post("/api/invitations/{code}/accept", INVITATION_CODE)
                        .with(csrf())
                        .with(oauthUser(INVITEE_PROVIDER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membershipId").value(membershipId))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.joinedNow").value(true));

        GroupMember reactivated = groupMemberRepository
                .findByGroup_IdAndUser_Id(group.getId(), invitee.getId())
                .orElseThrow();
        assertEquals(membershipId, reactivated.getId());
        assertEquals(membershipPublicId, reactivated.getPublicId());
        assertEquals(MemberStatus.ACTIVE, reactivated.getStatus());
        assertNull(reactivated.getLeftAt());
        assertTrue(!reactivated.getJoinedAt().isBefore(beforeAcceptance));

        long matchingMemberships = groupMemberRepository.findAll().stream()
                .filter(member -> member.getGroup().getId().equals(group.getId()))
                .filter(member -> member.getUser().getId().equals(invitee.getId()))
                .count();
        assertEquals(1, matchingMemberships);
    }

    @Test
    void ownerKeepsOwnerRoleWhenAcceptingOwnInvitation() throws Exception {
        acceptAs(OWNER_PROVIDER_ID, "already-member");

        GroupMember membership = groupMemberRepository
                .findByGroup_IdAndUser_Id(group.getId(), owner.getId())
                .orElseThrow();
        assertEquals(GroupRole.OWNER, membership.getRole());
    }

    @Test
    void reusableInvitationAllowsDifferentUsersToJoin() throws Exception {
        acceptAs(INVITEE_PROVIDER_ID, "joined");
        acceptAs(SECOND_INVITEE_PROVIDER_ID, "joined");

        assertTrue(groupMemberRepository
                .findByGroup_IdAndUser_Id(group.getId(), invitee.getId())
                .isPresent());
        assertTrue(groupMemberRepository
                .findByGroup_IdAndUser_Id(group.getId(), secondInvitee.getId())
                .isPresent());
        assertTrue(invitation.isUsableAt(Instant.now()));
    }

    @Test
    void expiredInvitationCannotBeAccepted() throws Exception {
        String expiredCode = "ExpiredCodeForInvite01";
        saveInvitation(
                expiredCode,
                group,
                Instant.now().minusSeconds(7200),
                Instant.now().minusSeconds(3600)
        );

        mockMvc.perform(post("/invite/{code}/accept", expiredCode)
                        .with(csrf())
                        .with(oauthUser(INVITEE_PROVIDER_ID)))
                .andExpect(status().isGone())
                .andExpect(content().string(containsString("만료되었거나 취소된 초대 링크입니다.")));

        assertTrue(groupMemberRepository
                .findByGroup_IdAndUser_Id(group.getId(), invitee.getId())
                .isEmpty());
    }

    @Test
    void revokedInvitationCannotBePreviewed() throws Exception {
        String revokedCode = "RevokedCodeForInvite01";
        GroupInvitation revokedInvitation = saveInvitation(
                revokedCode,
                group,
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(3600)
        );
        revokedInvitation.revoke(Instant.now());
        invitationRepository.save(revokedInvitation);

        mockMvc.perform(get("/invite/{code}", revokedCode)
                        .with(oauthUser(INVITEE_PROVIDER_ID)))
                .andExpect(status().isGone())
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void unknownOrMalformedInvitationReturnsNotFound() throws Exception {
        mockMvc.perform(get("/invite/{code}", UNKNOWN_CODE)
                        .with(oauthUser(INVITEE_PROVIDER_ID)))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("초대 링크를 찾을 수 없습니다.")));

        mockMvc.perform(get("/invite/{code}", "bad-code")
                        .with(oauthUser(INVITEE_PROVIDER_ID)))
                .andExpect(status().isNotFound());
    }

    @Test
    void acceptanceRequiresCsrfToken() throws Exception {
        mockMvc.perform(post("/invite/{code}/accept", INVITATION_CODE)
                        .with(oauthUser(INVITEE_PROVIDER_ID)))
                .andExpect(status().isForbidden());

        assertTrue(groupMemberRepository
                .findByGroup_IdAndUser_Id(group.getId(), invitee.getId())
                .isEmpty());
    }

    @Test
    void invitationApiAcceptanceRequiresCsrfToken() throws Exception {
        mockMvc.perform(post("/api/invitations/{code}/accept", INVITATION_CODE)
                        .with(oauthUser(INVITEE_PROVIDER_ID)))
                .andExpect(status().isForbidden());

        assertTrue(groupMemberRepository
                .findByGroup_IdAndUser_Id(group.getId(), invitee.getId())
                .isEmpty());
    }

    @Test
    void authenticatedUserMissingFromDatabaseCannotAccept() throws Exception {
        mockMvc.perform(post("/invite/{code}/accept", INVITATION_CODE)
                        .with(csrf())
                        .with(oauthUser("missing-acceptance-user")))
                .andExpect(status().isUnauthorized());

        assertEquals(1, groupMemberRepository.count());
    }

    @Test
    void groupNameIsEscapedOnInvitationPage() throws Exception {
        SharingGroup unsafeGroup = groupRepository.save(
                new SharingGroup("<script>alert('xss')</script>", owner)
        );
        groupMemberRepository.save(GroupMember.owner(unsafeGroup, owner));
        String unsafeGroupCode = "UnsafeGroupInviteCode1";
        saveInvitation(
                unsafeGroupCode,
                unsafeGroup,
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(3600)
        );

        mockMvc.perform(get("/invite/{code}", unsafeGroupCode)
                        .with(oauthUser(INVITEE_PROVIDER_ID)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "&lt;script&gt;alert(&#39;xss&#39;)&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert('xss')</script>"))));
    }

    private void acceptAs(String providerUserId, String expectedResult) throws Exception {
        mockMvc.perform(post("/invite/{code}/accept", INVITATION_CODE)
                        .with(csrf())
                        .with(oauthUser(providerUserId)))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location",
                        "/invite/" + INVITATION_CODE + "?result=" + expectedResult));
    }

    private User saveUser(String providerUserId) {
        return userRepository.save(User.builder()
                .provider(OAuthProvider.GOOGLE)
                .providerUserId(providerUserId)
                .email(providerUserId + "@example.com")
                .build());
    }

    private GroupInvitation saveInvitation(String code, SharingGroup targetGroup,
                                            Instant createdAt, Instant expiresAt) {
        return invitationRepository.save(new GroupInvitation(
                targetGroup,
                owner,
                codeHasher.hash(code),
                createdAt,
                expiresAt
        ));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor oauthUser(
            String providerUserId) {
        return oauth2Login()
                .clientRegistration(googleClientRegistration())
                .attributes(attributes -> {
                    attributes.put("sub", providerUserId);
                    attributes.put("email", providerUserId + "@example.com");
                });
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
