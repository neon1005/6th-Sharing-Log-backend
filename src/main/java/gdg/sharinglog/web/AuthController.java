package gdg.sharinglog.web;

import gdg.sharinglog.service.user.UserProfileService;
import gdg.sharinglog.web.dto.UpdateNicknameRequest;
import gdg.sharinglog.web.dto.UserProfileResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/auth")
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final UserProfileService userProfileService;

    @GetMapping("/me")
    public UserProfileResponse me(OAuth2AuthenticationToken authentication) {
        return userProfileService.getProfile(
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getPrincipal()
        );
    }

    @PatchMapping("/me")
    public UserProfileResponse updateNickname(
            @Valid @RequestBody UpdateNicknameRequest request,
            OAuth2AuthenticationToken authentication
    ) {
        return userProfileService.updateNickname(
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getPrincipal(),
                request.nickname()
        );
    }
}
