package gdg.sharinglog.service.rotation.api.member;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.MemberStatus;
import gdg.sharinglog.domain.SharingGroup;
import gdg.sharinglog.service.rotation.access.RotationActor;
import gdg.sharinglog.service.rotation.access.RotationActorAccessService;
import gdg.sharinglog.service.rotation.occurrence.OccurrenceCommandService;
import gdg.sharinglog.service.rotation.occurrence.OccurrencePlanService;
import gdg.sharinglog.web.rotation.RotationViewMapper;
import gdg.sharinglog.web.rotation.dto.MemberRefResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.user.OAuth2User;

@ExtendWith(MockitoExtension.class)
class MemberLeaveApplicationServiceTest {

    @Mock
    RotationActorAccessService accessService;

    @Mock
    OccurrenceCommandService commandService;

    @Mock
    OccurrencePlanService occurrencePlanService;

    @Mock
    RotationViewMapper viewMapper;

    @InjectMocks
    MemberLeaveApplicationService service;

    @Test
    void memberLeaveRegeneratesTheGroupsFuturePlan() {
        String groupPublicId = "group-public-id";
        String membershipPublicId = "membership-public-id";
        Instant leftAt = Instant.parse("2026-08-07T03:00:00Z");
        OAuth2User principal = mock(OAuth2User.class);
        RotationActor actor = mock(RotationActor.class);
        SharingGroup group = mock(SharingGroup.class);
        GroupMember actorMembership = mock(GroupMember.class);
        GroupMember target = mock(GroupMember.class);

        when(accessService.requireActiveMemberForUpdate(groupPublicId, "google", principal))
                .thenReturn(actor);
        when(actor.group()).thenReturn(group);
        when(actor.membership()).thenReturn(actorMembership);
        when(group.getId()).thenReturn(10L);
        when(accessService.requireTargetMemberForUpdate(actor, membershipPublicId))
                .thenReturn(target);
        when(target.isActive()).thenReturn(true);
        when(target.getVersion()).thenReturn(3L);
        when(target.getId()).thenReturn(20L);
        when(actorMembership.getId()).thenReturn(20L);
        when(commandService.leaveMember(membershipPublicId, leftAt)).thenReturn(List.of());
        when(viewMapper.member(target)).thenReturn(new MemberRefResponse(
                membershipPublicId,
                "떠나는 멤버",
                null,
                MemberStatus.LEFT
        ));

        service.leave(
                groupPublicId,
                membershipPublicId,
                "google",
                principal,
                3L,
                leftAt
        );

        verify(occurrencePlanService).regenerateGroupFuture(10L, leftAt);
    }
}
