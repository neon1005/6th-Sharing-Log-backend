package gdg.sharinglog.web.dto;

import gdg.sharinglog.domain.User;

public record UserProfileResponse(
        String email,
        String nickname
) {

    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(user.getEmail(), user.getNickname());
    }
}
