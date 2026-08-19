package gdg.sharinglog.service.booking;

import java.time.Instant;
import java.util.List;

import gdg.sharinglog.domain.booking.Space;
import gdg.sharinglog.repository.booking.SpaceRepository;
import gdg.sharinglog.service.booking.exception.BookingConflictException;
import gdg.sharinglog.web.booking.dto.SpaceListResponse;
import gdg.sharinglog.web.booking.dto.SpaceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SpaceService {

    private final BookingAccessService accessService;
    private final SpaceRepository spaceRepository;

    @Transactional(readOnly = true)
    public SpaceListResponse listSpaces(
            String groupPublicId,
            String registrationId,
            OAuth2User principal
    ) {
        BookingActor actor = accessService.requireActiveMember(groupPublicId, registrationId, principal);
        List<SpaceResponse> items = spaceRepository
                .findAllByGroup_IdAndActiveTrueOrderByNameAsc(actor.group().getId())
                .stream()
                .map(SpaceService::toResponse)
                .toList();
        return new SpaceListResponse(actor.group().getPublicId(), items);
    }

    @Transactional
    public SpaceResponse createSpace(
            String groupPublicId,
            String registrationId,
            OAuth2User principal,
            String name
    ) {
        BookingActor actor = accessService.requireActiveMember(groupPublicId, registrationId, principal);
        String trimmedName = name == null ? "" : name.trim();
        if (trimmedName.isEmpty()) {
            throw new IllegalArgumentException("공간 이름은 필수입니다.");
        }
        if (spaceRepository.existsByGroup_IdAndNameIgnoreCase(actor.group().getId(), trimmedName)) {
            throw new BookingConflictException("이미 같은 이름의 공간이 있습니다: " + trimmedName);
        }
        Space space = spaceRepository.save(new Space(actor.group(), trimmedName, Instant.now()));
        return toResponse(space);
    }

    @Transactional
    public void deleteSpace(
            String groupPublicId,
            String spacePublicId,
            String registrationId,
            OAuth2User principal
    ) {
        accessService.requireActiveMember(groupPublicId, registrationId, principal);
        spaceRepository
                .findByPublicIdAndGroupPublicIdForUpdate(spacePublicId, groupPublicId)
                .ifPresent(Space::deactivate);
    }

    private static SpaceResponse toResponse(Space space) {
        return new SpaceResponse(space.getPublicId(), space.getName());
    }
}
