package gdg.sharinglog.web.booking;

import gdg.sharinglog.service.booking.SpaceService;
import gdg.sharinglog.web.booking.dto.CreateSpaceRequest;
import gdg.sharinglog.web.booking.dto.SpaceListResponse;
import gdg.sharinglog.web.booking.dto.SpaceResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups/{groupId}/spaces")
@RequiredArgsConstructor
public class SpaceController {

    private final SpaceService spaceService;

    @GetMapping
    public SpaceListResponse list(
            @PathVariable String groupId,
            OAuth2AuthenticationToken authentication
    ) {
        return spaceService.listSpaces(
                groupId,
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getPrincipal()
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SpaceResponse create(
            @PathVariable String groupId,
            @Valid @RequestBody CreateSpaceRequest request,
            OAuth2AuthenticationToken authentication
    ) {
        return spaceService.createSpace(
                groupId,
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getPrincipal(),
                request.name()
        );
    }

    @DeleteMapping("/{spaceId}")
    public ResponseEntity<Void> delete(
            @PathVariable String groupId,
            @PathVariable String spaceId,
            OAuth2AuthenticationToken authentication
    ) {
        spaceService.deleteSpace(
                groupId,
                spaceId,
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getPrincipal()
        );
        return ResponseEntity.noContent().build();
    }
}
