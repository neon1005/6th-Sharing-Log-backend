package gdg.sharinglog;

import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.GroupRole;
import gdg.sharinglog.domain.MemberStatus;
import gdg.sharinglog.domain.OAuthProvider;
import gdg.sharinglog.domain.SharingGroup;
import gdg.sharinglog.domain.User;
import gdg.sharinglog.repository.GroupMemberRepository;
import gdg.sharinglog.repository.SharingGroupRepository;
import gdg.sharinglog.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GroupCreationApiTest {

    private static final String GOOGLE_USER_ID = "group-creator-google-id";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    SharingGroupRepository groupRepository;

    @Autowired
    GroupMemberRepository groupMemberRepository;

    private User creator;

    @BeforeEach
    void saveCreator() {
        creator = userRepository.save(User.builder()
                .provider(OAuthProvider.GOOGLE)
                .providerUserId(GOOGLE_USER_ID)
                .email("creator@example.com")
                .build());
    }

    @Test
    void createsGroupAndRegistersCreatorAsOwner() throws Exception {
        mockMvc.perform(post("/api/groups")
                        .with(csrf())
                        .with(oauth2Login()
                                .clientRegistration(googleClientRegistration())
                                .attributes(attributes -> {
                                    attributes.put("sub", GOOGLE_USER_ID);
                                    attributes.put("email", "creator@example.com");
                                }))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  우리 집  "}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/groups/")))
                .andExpect(jsonPath("$.groupId", notNullValue()))
                .andExpect(jsonPath("$.name").value("우리 집"))
                .andExpect(jsonPath("$.membershipId", notNullValue()))
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.createdAt", notNullValue()));

        assertEquals(1, groupRepository.count());
        assertEquals(1, groupMemberRepository.count());

        SharingGroup group = groupRepository.findAll().getFirst();
        GroupMember membership = groupMemberRepository
                .findByGroup_IdAndUser_Id(group.getId(), creator.getId())
                .orElseThrow();

        assertEquals(GroupRole.OWNER, membership.getRole());
        assertEquals(creator.getId(), group.getCreatedBy().getId());
    }

    @Test
    void createsGroupWithAddress() throws Exception {
        mockMvc.perform(post("/api/groups")
                        .with(csrf())
                        .with(oauth2Login()
                                .clientRegistration(googleClientRegistration())
                                .attributes(attributes -> attributes.put("sub", GOOGLE_USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"우리 집","address":"  서울시 강남구 역삼동  "}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.address").value("서울시 강남구 역삼동"));

        SharingGroup group = groupRepository.findAll().getFirst();
        assertEquals("서울시 강남구 역삼동", group.getAddress());
    }

    @Test
    void createsGroupWithoutAddress() throws Exception {
        mockMvc.perform(post("/api/groups")
                        .with(csrf())
                        .with(oauth2Login()
                                .clientRegistration(googleClientRegistration())
                                .attributes(attributes -> attributes.put("sub", GOOGLE_USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"우리 집"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.address").doesNotExist());

        SharingGroup group = groupRepository.findAll().getFirst();
        assertNull(group.getAddress());
    }

    @Test
    void rejectsBlankGroupNameWithoutCreatingData() throws Exception {
        mockMvc.perform(post("/api/groups")
                        .with(csrf())
                        .with(oauth2Login()
                                .clientRegistration(googleClientRegistration())
                                .attributes(attributes -> attributes.put("sub", GOOGLE_USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"   "}
                                """))
                .andExpect(status().isBadRequest());

        assertEquals(0, groupRepository.count());
        assertEquals(0, groupMemberRepository.count());
    }

    @Test
    void requiresCsrfTokenForGroupCreation() throws Exception {
        mockMvc.perform(post("/api/groups")
                        .with(oauth2Login()
                                .clientRegistration(googleClientRegistration())
                                .attributes(attributes -> attributes.put("sub", GOOGLE_USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"우리 집"}
                                """))
                .andExpect(status().isForbidden());

        assertEquals(0, groupRepository.count());
        assertEquals(0, groupMemberRepository.count());
    }

    @Test
    void rejectsAuthenticatedUserMissingFromDatabase() throws Exception {
        mockMvc.perform(post("/api/groups")
                        .with(csrf())
                        .with(oauth2Login()
                                .clientRegistration(googleClientRegistration())
                                .attributes(attributes -> attributes.put("sub", "missing-user-id")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"우리 집"}
                                """))
                .andExpect(status().isUnauthorized());

        assertTrue(groupRepository.findAll().isEmpty());
        assertTrue(groupMemberRepository.findAll().isEmpty());
    }

    @Test
    void allowsCreatingASecondGroupWhileActiveInAnother() throws Exception {
        mockMvc.perform(post("/api/groups")
                        .with(csrf())
                        .with(oauth2Login()
                                .clientRegistration(googleClientRegistration())
                                .attributes(attributes -> attributes.put("sub", GOOGLE_USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"첫 번째 집"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/groups")
                        .with(csrf())
                        .with(oauth2Login()
                                .clientRegistration(googleClientRegistration())
                                .attributes(attributes -> attributes.put("sub", GOOGLE_USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"두 번째 집"}
                                """))
                .andExpect(status().isCreated());

        assertEquals(2, groupRepository.count());
        assertEquals(2, groupMemberRepository.count());
    }

    @Test
    void ownerUpdatesGroupNameAndAddress() throws Exception {
        SharingGroup group = groupRepository.save(new SharingGroup("기존 집", creator));
        groupMemberRepository.save(GroupMember.owner(group, creator));

        mockMvc.perform(patch("/api/groups/{groupId}", group.getPublicId())
                        .with(csrf())
                        .with(oauth2Login()
                                .clientRegistration(googleClientRegistration())
                                .attributes(attributes -> attributes.put("sub", GOOGLE_USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  새로운 집  ","address":"  부산시 해운대구  "}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupPublicId").value(group.getPublicId()))
                .andExpect(jsonPath("$.name").value("새로운 집"))
                .andExpect(jsonPath("$.address").value("부산시 해운대구"));

        SharingGroup updated = groupRepository.findById(group.getId()).orElseThrow();
        assertEquals("새로운 집", updated.getName());
        assertEquals("부산시 해운대구", updated.getAddress());
    }

    @Test
    void regularMemberCannotUpdateGroup() throws Exception {
        User owner = userRepository.save(User.builder()
                .provider(OAuthProvider.GOOGLE)
                .providerUserId("group-update-owner-id")
                .build());
        SharingGroup group = groupRepository.save(new SharingGroup("기존 집", owner));
        groupMemberRepository.save(GroupMember.owner(group, owner));
        groupMemberRepository.save(GroupMember.member(group, creator));

        mockMvc.perform(patch("/api/groups/{groupId}", group.getPublicId())
                        .with(csrf())
                        .with(oauth2Login()
                                .clientRegistration(googleClientRegistration())
                                .attributes(attributes -> attributes.put("sub", GOOGLE_USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"권한 없는 변경"}
                                """))
                .andExpect(status().isForbidden());

        assertEquals("기존 집", groupRepository.findById(group.getId()).orElseThrow().getName());
    }

    @Test
    void soleOwnerDeletesGroup() throws Exception {
        SharingGroup group = groupRepository.save(new SharingGroup("삭제할 집", creator));
        GroupMember ownerMembership = groupMemberRepository.save(GroupMember.owner(group, creator));

        mockMvc.perform(delete("/api/groups/{groupId}", group.getPublicId())
                        .with(csrf())
                        .with(oauth2Login()
                                .clientRegistration(googleClientRegistration())
                                .attributes(attributes -> attributes.put("sub", GOOGLE_USER_ID))))
                .andExpect(status().isNoContent());

        assertNotNull(group.getDeletedAt());
        assertEquals(MemberStatus.LEFT, ownerMembership.getStatus());
        assertTrue(groupRepository.findByPublicId(group.getPublicId()).isEmpty());
        assertEquals(1, groupRepository.count());
    }

    @Test
    void ownerDeletesGroupAndRemainingMembersAreMarkedLeft() throws Exception {
        SharingGroup group = groupRepository.save(new SharingGroup("함께 사는 집", creator));
        groupMemberRepository.save(GroupMember.owner(group, creator));
        User member = userRepository.save(User.builder()
                .provider(OAuthProvider.GOOGLE)
                .providerUserId("group-delete-member-id")
                .build());
        GroupMember remainingMembership = groupMemberRepository.save(GroupMember.member(group, member));

        mockMvc.perform(delete("/api/groups/{groupId}", group.getPublicId())
                        .with(csrf())
                        .with(oauth2Login()
                                .clientRegistration(googleClientRegistration())
                                .attributes(attributes -> attributes.put("sub", GOOGLE_USER_ID))))
                .andExpect(status().isNoContent());

        assertTrue(groupRepository.findByPublicId(group.getPublicId()).isEmpty());
        GroupMember reloaded = groupMemberRepository.findById(remainingMembership.getId()).orElseThrow();
        assertEquals(MemberStatus.LEFT, reloaded.getStatus());
    }

    @Test
    void regularMemberCannotDeleteGroup() throws Exception {
        User owner = userRepository.save(User.builder()
                .provider(OAuthProvider.GOOGLE)
                .providerUserId("group-delete-owner-id")
                .build());
        SharingGroup group = groupRepository.save(new SharingGroup("멤버가 있는 집", owner));
        groupMemberRepository.save(GroupMember.owner(group, owner));
        groupMemberRepository.save(GroupMember.member(group, creator));

        mockMvc.perform(delete("/api/groups/{groupId}", group.getPublicId())
                        .with(csrf())
                        .with(oauth2Login()
                                .clientRegistration(googleClientRegistration())
                                .attributes(attributes -> attributes.put("sub", GOOGLE_USER_ID))))
                .andExpect(status().isForbidden());

        assertNull(group.getDeletedAt());
    }

    @Test
    void ownerPromotesMemberToOwner() throws Exception {
        SharingGroup group = groupRepository.save(new SharingGroup("함께 사는 집", creator));
        groupMemberRepository.save(GroupMember.owner(group, creator));
        User member = userRepository.save(User.builder()
                .provider(OAuthProvider.GOOGLE)
                .providerUserId("group-promote-member-id")
                .build());
        GroupMember membership = groupMemberRepository.save(GroupMember.member(group, member));

        mockMvc.perform(post(
                        "/api/groups/{groupId}/members/{membershipId}/promote",
                        group.getPublicId(),
                        membership.getPublicId()
                )
                        .with(csrf())
                        .with(oauth2Login()
                                .clientRegistration(googleClientRegistration())
                                .attributes(attributes -> attributes.put("sub", GOOGLE_USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membershipPublicId").value(membership.getPublicId()))
                .andExpect(jsonPath("$.role").value("OWNER"));

        assertEquals(GroupRole.OWNER, groupMemberRepository.findById(membership.getId()).orElseThrow().getRole());
    }

    @Test
    void regularMemberCannotPromoteAnotherMember() throws Exception {
        User owner = userRepository.save(User.builder()
                .provider(OAuthProvider.GOOGLE)
                .providerUserId("group-promote-owner-id")
                .build());
        SharingGroup group = groupRepository.save(new SharingGroup("함께 사는 집", owner));
        groupMemberRepository.save(GroupMember.owner(group, owner));
        GroupMember requesterMembership = groupMemberRepository.save(GroupMember.member(group, creator));

        mockMvc.perform(post(
                        "/api/groups/{groupId}/members/{membershipId}/promote",
                        group.getPublicId(),
                        requesterMembership.getPublicId()
                )
                        .with(csrf())
                        .with(oauth2Login()
                                .clientRegistration(googleClientRegistration())
                                .attributes(attributes -> attributes.put("sub", GOOGLE_USER_ID))))
                .andExpect(status().isForbidden());

        assertEquals(
                GroupRole.MEMBER,
                groupMemberRepository.findById(requesterMembership.getId()).orElseThrow().getRole()
        );
    }

    @Test
    void promotingUnknownMemberReturnsNotFound() throws Exception {
        SharingGroup group = groupRepository.save(new SharingGroup("함께 사는 집", creator));
        groupMemberRepository.save(GroupMember.owner(group, creator));

        mockMvc.perform(post(
                        "/api/groups/{groupId}/members/{membershipId}/promote",
                        group.getPublicId(),
                        "unknown-membership-id"
                )
                        .with(csrf())
                        .with(oauth2Login()
                                .clientRegistration(googleClientRegistration())
                                .attributes(attributes -> attributes.put("sub", GOOGLE_USER_ID))))
                .andExpect(status().isNotFound());
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
