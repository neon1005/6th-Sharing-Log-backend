package gdg.sharinglog.service.booking;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.MemberStatus;
import gdg.sharinglog.domain.SharingGroup;
import gdg.sharinglog.domain.User;
import gdg.sharinglog.repository.GroupMemberRepository;
import gdg.sharinglog.repository.SharingGroupRepository;
import gdg.sharinglog.service.booking.exception.BookingAccessDeniedException;
import gdg.sharinglog.service.booking.exception.BookingGroupNotFoundException;
import gdg.sharinglog.service.user.AuthenticatedUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookingAccessService {

    private final SharingGroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final AuthenticatedUserService authenticatedUserService;

    @Transactional(readOnly = true)
    public BookingActor requireActiveMember(
            String groupPublicId,
            String registrationId,
            OAuth2User oAuth2User
    ) {
        User user = authenticatedUserService.requireUser(registrationId, oAuth2User);
        SharingGroup group = groupRepository.findByPublicId(groupPublicId)
                .orElseThrow(() -> new BookingGroupNotFoundException(groupPublicId));
        GroupMember membership = groupMemberRepository
                .findByGroup_IdAndUser_IdAndStatus(group.getId(), user.getId(), MemberStatus.ACTIVE)
                .orElseThrow(BookingAccessDeniedException::new);
        return new BookingActor(group, membership);
    }
}
