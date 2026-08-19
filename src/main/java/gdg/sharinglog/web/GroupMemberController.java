package gdg.sharinglog.web;

import gdg.sharinglog.service.group.GroupMemberQueryService;
import gdg.sharinglog.web.dto.GroupMembersResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/groups/{groupId}/members")
@RestController
@RequiredArgsConstructor
public class GroupMemberController {

    private final GroupMemberQueryService groupMemberQueryService;

    @GetMapping
    public ResponseEntity<GroupMembersResponse> members(
            @PathVariable Long groupId,
            OAuth2AuthenticationToken authentication) {
        GroupMembersResponse response = GroupMembersResponse.from(
                groupMemberQueryService.findMembers(
                        groupId,
                        authentication.getAuthorizedClientRegistrationId(),
                        authentication.getPrincipal()
                )
        );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }
}
