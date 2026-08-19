package gdg.sharinglog.service.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.SharingGroup;
import gdg.sharinglog.domain.booking.Space;
import gdg.sharinglog.repository.booking.SpaceRepository;
import gdg.sharinglog.service.booking.exception.BookingConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.user.OAuth2User;

@ExtendWith(MockitoExtension.class)
class SpaceServiceTest {

    @Mock
    BookingAccessService accessService;

    @Mock
    SpaceRepository spaceRepository;

    @InjectMocks
    SpaceService service;

    @Test
    void createSpaceRejectsDuplicateNameCaseInsensitive() {
        String groupPublicId = "grp-1";
        OAuth2User principal = mock(OAuth2User.class);
        SharingGroup group = mock(SharingGroup.class);
        GroupMember membership = mock(GroupMember.class);
        BookingActor actor = new BookingActor(group, membership);

        when(accessService.requireActiveMember(groupPublicId, "google", principal)).thenReturn(actor);
        when(group.getId()).thenReturn(1L);
        when(spaceRepository.existsByGroup_IdAndNameIgnoreCase(1L, "세탁실")).thenReturn(true);

        assertThrows(BookingConflictException.class, () ->
                service.createSpace(groupPublicId, "google", principal, "세탁실"));
        verify(spaceRepository, never()).save(any());
    }

    @Test
    void createSpaceSavesTrimmedName() {
        String groupPublicId = "grp-1";
        OAuth2User principal = mock(OAuth2User.class);
        SharingGroup group = mock(SharingGroup.class);
        GroupMember membership = mock(GroupMember.class);
        BookingActor actor = new BookingActor(group, membership);

        when(accessService.requireActiveMember(groupPublicId, "google", principal)).thenReturn(actor);
        when(group.getId()).thenReturn(1L);
        when(spaceRepository.existsByGroup_IdAndNameIgnoreCase(1L, "세탁실")).thenReturn(false);
        when(spaceRepository.save(any(Space.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createSpace(groupPublicId, "google", principal, "  세탁실  ");

        assertEquals("세탁실", response.name());
    }

    @Test
    void listSpacesMapsToResponse() {
        String groupPublicId = "grp-1";
        OAuth2User principal = mock(OAuth2User.class);
        SharingGroup group = mock(SharingGroup.class);
        GroupMember membership = mock(GroupMember.class);
        BookingActor actor = new BookingActor(group, membership);
        Space space = new Space(group, "공용 주방", java.time.Instant.now());

        when(accessService.requireActiveMember(groupPublicId, "google", principal)).thenReturn(actor);
        when(group.getId()).thenReturn(1L);
        when(group.getPublicId()).thenReturn(groupPublicId);
        when(spaceRepository.findAllByGroup_IdAndActiveTrueOrderByNameAsc(1L))
                .thenReturn(List.of(space));

        var response = service.listSpaces(groupPublicId, "google", principal);

        assertEquals(1, response.items().size());
        assertEquals("공용 주방", response.items().get(0).name());
    }

    @Test
    void deleteSpaceDeactivatesSpace() {
        String groupPublicId = "grp-1";
        String spacePublicId = "space-1";
        OAuth2User principal = mock(OAuth2User.class);
        SharingGroup group = mock(SharingGroup.class);
        GroupMember membership = mock(GroupMember.class);
        BookingActor actor = new BookingActor(group, membership);
        Space space = mock(Space.class);

        when(accessService.requireActiveMember(groupPublicId, "google", principal)).thenReturn(actor);
        when(spaceRepository.findByPublicIdAndGroupPublicIdForUpdate(spacePublicId, groupPublicId))
                .thenReturn(Optional.of(space));

        service.deleteSpace(groupPublicId, spacePublicId, "google", principal);

        verify(space).deactivate();
    }

    @Test
    void deleteSpaceIsNoOpWhenSpaceMissing() {
        String groupPublicId = "grp-1";
        String spacePublicId = "space-1";
        OAuth2User principal = mock(OAuth2User.class);
        SharingGroup group = mock(SharingGroup.class);
        GroupMember membership = mock(GroupMember.class);
        BookingActor actor = new BookingActor(group, membership);

        when(accessService.requireActiveMember(groupPublicId, "google", principal)).thenReturn(actor);
        when(spaceRepository.findByPublicIdAndGroupPublicIdForUpdate(spacePublicId, groupPublicId))
                .thenReturn(Optional.empty());

        service.deleteSpace(groupPublicId, spacePublicId, "google", principal);
    }
}
