package gdg.sharinglog.web;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import gdg.sharinglog.service.group.GroupMemberQueryService;
import gdg.sharinglog.service.group.GroupService;
import gdg.sharinglog.web.dto.CreateGroupRequest;
import gdg.sharinglog.web.dto.GroupResponse;
import gdg.sharinglog.web.dto.MyGroupResponse;
import gdg.sharinglog.web.dto.PromoteMemberResponse;
import gdg.sharinglog.web.dto.UpdateGroupRequest;
import gdg.sharinglog.web.dto.UpdateGroupResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/groups")
@RestController
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;
    private final GroupMemberQueryService groupMemberQueryService;

    @PostMapping
    public ResponseEntity<GroupResponse> createGroup(@Valid @RequestBody CreateGroupRequest request,
                                                     OAuth2AuthenticationToken authentication) {
        GroupResponse response = GroupResponse.from(groupService.createGroup(
                request.name(),
                request.address(),
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getPrincipal()
        ));

        return ResponseEntity
                .created(URI.create("/api/groups/" + response.groupId()))
                .body(response);
    }

    @GetMapping("/me")
    public List<MyGroupResponse> myGroups(OAuth2AuthenticationToken authentication) {
        return groupMemberQueryService.findMyGroups(
                        authentication.getAuthorizedClientRegistrationId(),
                        authentication.getPrincipal()
                )
                .stream()
                .map(MyGroupResponse::from)
                .toList();
    }

    @PatchMapping("/{groupId}")
    public ResponseEntity<UpdateGroupResponse> updateGroup(
            @PathVariable String groupId,
            @Valid @RequestBody UpdateGroupRequest request,
            OAuth2AuthenticationToken authentication
    ) {
        return ResponseEntity.ok(UpdateGroupResponse.from(groupService.updateGroup(
                groupId,
                request.name(),
                request.address(),
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getPrincipal()
        )));
    }

    @PostMapping("/{groupId}/members/{membershipId}/promote")
    public ResponseEntity<PromoteMemberResponse> promoteMember(
            @PathVariable String groupId,
            @PathVariable String membershipId,
            OAuth2AuthenticationToken authentication
    ) {
        return ResponseEntity.ok(PromoteMemberResponse.from(groupService.promoteMember(
                groupId,
                membershipId,
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getPrincipal()
        )));
    }

    @DeleteMapping("/{groupId}")
    public ResponseEntity<Void> deleteGroup(
            @PathVariable String groupId,
            OAuth2AuthenticationToken authentication
    ) {
        groupService.deleteGroup(
                groupId,
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getPrincipal(),
                Instant.now()
        );
        return ResponseEntity.noContent().build();
    }
}
