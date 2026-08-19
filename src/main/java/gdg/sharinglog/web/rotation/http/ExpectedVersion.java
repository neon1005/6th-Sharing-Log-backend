package gdg.sharinglog.web.rotation.http;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import gdg.sharinglog.web.rotation.error.RotationBadRequestException;
import gdg.sharinglog.web.rotation.error.RotationFieldError;
import gdg.sharinglog.web.rotation.error.RotationPreconditionRequiredException;
import gdg.sharinglog.web.rotation.error.RotationProblemCode;

public record ExpectedVersion(long value) {

    private static final Pattern STRONG_VERSION_ETAG =
            Pattern.compile("\"(0|[1-9][0-9]*)\"");

    public ExpectedVersion {
        if (value < 0) {
            throw new IllegalArgumentException("Expected version must not be negative");
        }
    }

    public static ExpectedVersion parse(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new RotationPreconditionRequiredException(
                    "기존 리소스를 변경하려면 If-Match 헤더가 필요합니다."
            );
        }

        Matcher matcher = STRONG_VERSION_ETAG.matcher(ifMatch);
        if (!matcher.matches()) {
            throw malformed();
        }

        try {
            return new ExpectedVersion(Long.parseLong(matcher.group(1)));
        } catch (NumberFormatException exception) {
            throw malformed();
        }
    }

    public String toStrongEtag() {
        return "\"" + value + "\"";
    }

    private static RotationBadRequestException malformed() {
        return new RotationBadRequestException(
                RotationProblemCode.VALIDATION_FAILED,
                "If-Match는 음이 아닌 10진 버전 하나를 담은 강한 ETag여야 합니다.",
                Map.of(
                        "errors",
                        List.of(new RotationFieldError(
                                "If-Match",
                                "예: \"7\". 약한 ETag, 와일드카드, 복수 값은 허용되지 않습니다."
                        ))
                )
        );
    }
}
