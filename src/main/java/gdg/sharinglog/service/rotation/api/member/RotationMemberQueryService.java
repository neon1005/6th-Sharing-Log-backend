package gdg.sharinglog.service.rotation.api.member;

import gdg.sharinglog.domain.MemberStatus;
import gdg.sharinglog.repository.GroupMemberRepository;
import gdg.sharinglog.service.rotation.access.RotationActorAccessService;
import gdg.sharinglog.web.rotation.RotationViewMapper;
import gdg.sharinglog.web.rotation.dto.RotationMemberListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RotationMemberQueryService {

    private final RotationActorAccessService accessService;
    private final GroupMemberRepository memberRepository;
    private final RotationViewMapper viewMapper;

    @Transactional(readOnly = true)
    public RotationMemberListResponse findActiveMembers(
            String groupPublicId,
            String registrationId,
            OAuth2User principal
    ) {
        var actor = accessService.requireActiveMember(
                groupPublicId,
                registrationId,
                principal
        );
        var items = memberRepository
                .findAllByGroup_IdAndStatusOrderById(
                        actor.group().getId(),
                        MemberStatus.ACTIVE
                )
                .stream()
                .map(member -> {
                    var reference = viewMapper.member(member);
                    return new RotationMemberListResponse.Member(
                            member.getPublicId(),
                            reference.displayName(),
                            member.getRole(),
                            member.getStatus(),
                            member.getVersion()
                    );
                })
                .toList();
        return new RotationMemberListResponse(
                actor.group().getPublicId(),
                actor.membership().getPublicId(),
                actor.isOwner(),
                items
        );
    }
}
