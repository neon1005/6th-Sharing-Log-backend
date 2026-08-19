package gdg.sharinglog;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import gdg.sharinglog.domain.GroupMember;
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
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GroupMemberListApiTest {

    private static final String OWNER_PROVIDER_ID = "private-owner-provider-id";
    private static final String MEMBER_PROVIDER_ID = "private-member-provider-id";
    private static final String OUTSIDER_PROVIDER_ID = "private-outsider-provider-id";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    SharingGroupRepository groupRepository;

    @Autowired
    GroupMemberRepository groupMemberRepository;

    private User owner;
    private User member;
    private SharingGroup group;

    @BeforeEach
    void setUpGroupMembers() {
        owner = saveUser(OWNER_PROVIDER_ID, "owner@example.com");
        member = saveUser(MEMBER_PROVIDER_ID, null);
        saveUser(OUTSIDER_PROVIDER_ID, "outsider@example.com");
        group = groupRepository.save(new SharingGroup("멤버 조회 테스트", owner));
        groupMemberRepository.save(GroupMember.owner(group, owner));
        groupMemberRepository.save(GroupMember.member(group, member));
    }

    @Test
    void ownerCanListMembersWithMinimalPersonalInformation() throws Exception {
        mockMvc.perform(get("/api/groups/{groupId}/members", group.getId())
                        .with(oauthUser(OWNER_PROVIDER_ID)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.groupId").value(group.getId()))
                .andExpect(jsonPath("$.groupName").value("멤버 조회 테스트"))
                .andExpect(jsonPath("$.myRole").value("OWNER"))
                .andExpect(jsonPath("$.members", hasSize(2)))
                .andExpect(jsonPath("$.members[0].email").value("owner@example.com"))
                .andExpect(jsonPath("$.members[0].role").value("OWNER"))
                .andExpect(jsonPath("$.members[0].joinedAt", notNullValue()))
                .andExpect(jsonPath("$.members[0].me").value(true))
                .andExpect(jsonPath("$.members[1].email").value(nullValue()))
                .andExpect(jsonPath("$.members[1].role").value("MEMBER"))
                .andExpect(jsonPath("$.members[1].me").value(false))
                .andExpect(jsonPath("$.members[0].provider").doesNotExist())
                .andExpect(jsonPath("$.members[0].providerUserId").doesNotExist())
                .andExpect(jsonPath("$.members[0].userId").doesNotExist())
                .andExpect(jsonPath("$.members[0].membershipId").doesNotExist())
                .andExpect(jsonPath("$.members[0].password").doesNotExist())
                .andExpect(content().string(not(containsString(OWNER_PROVIDER_ID))))
                .andExpect(content().string(not(containsString(MEMBER_PROVIDER_ID))));

        assertEquals(2, groupMemberRepository.count());
    }

    @Test
    void regularMemberCanListMembersAndSeesOwnRole() throws Exception {
        mockMvc.perform(get("/api/groups/{groupId}/members", group.getId())
                        .with(oauthUser(MEMBER_PROVIDER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").value("MEMBER"))
                .andExpect(jsonPath("$.members[0].role").value("OWNER"))
                .andExpect(jsonPath("$.members[0].me").value(false))
                .andExpect(jsonPath("$.members[1].role").value("MEMBER"))
                .andExpect(jsonPath("$.members[1].me").value(true));
    }

    @Test
    void leftMembersAreExcludedFromMemberList() throws Exception {
        leaveMember();

        mockMvc.perform(get("/api/groups/{groupId}/members", group.getId())
                        .with(oauthUser(OWNER_PROVIDER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members", hasSize(1)))
                .andExpect(jsonPath("$.members[0].role").value("OWNER"))
                .andExpect(jsonPath("$.members[0].me").value(true));
    }

    @Test
    void leftMemberCannotListGroupMembers() throws Exception {
        leaveMember();

        mockMvc.perform(get("/api/groups/{groupId}/members", group.getId())
                        .with(oauthUser(MEMBER_PROVIDER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(content().string(not(containsString("owner@example.com"))));
    }

    @Test
    void nonMemberCannotListMembers() throws Exception {
        mockMvc.perform(get("/api/groups/{groupId}/members", group.getId())
                        .with(oauthUser(OUTSIDER_PROVIDER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(content().string(not(containsString("owner@example.com"))));
    }

    @Test
    void missingGroupReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/groups/{groupId}/members", Long.MAX_VALUE)
                        .with(oauthUser(OWNER_PROVIDER_ID)))
                .andExpect(status().isNotFound());
    }

    @Test
    void authenticatedUserMissingFromDatabaseCannotListMembers() throws Exception {
        mockMvc.perform(get("/api/groups/{groupId}/members", group.getId())
                        .with(oauthUser("missing-member-list-user")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousUserReceivesUnauthorized() throws Exception {
        mockMvc.perform(get("/api/groups/{groupId}/members", group.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"));
    }

    private void leaveMember() {
        GroupMember membership = groupMemberRepository
                .findByGroup_IdAndUser_Id(group.getId(), member.getId())
                .orElseThrow();
        membership.leave(Instant.now());
        groupMemberRepository.saveAndFlush(membership);
    }

    private User saveUser(String providerUserId, String email) {
        return userRepository.save(User.builder()
                .provider(OAuthProvider.GOOGLE)
                .providerUserId(providerUserId)
                .email(email)
                .build());
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
