package gdg.sharinglog.web.rotation.http;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import gdg.sharinglog.web.rotation.error.RotationBadRequestException;
import gdg.sharinglog.web.rotation.error.RotationFieldError;
import gdg.sharinglog.web.rotation.error.RotationProblemCode;

public record IdempotencyKey(String value) {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 128;

    private static final Pattern VALID_VALUE = Pattern.compile("[\\x21-\\x7E]{8,128}");

    public IdempotencyKey {
        if (value == null || value.isBlank()) {
            throw invalid("상태 변경 요청에는 Idempotency-Key 헤더가 필요합니다.");
        }
        if (!VALID_VALUE.matcher(value).matches()) {
            throw invalid(
                    "Idempotency-Key는 공백과 제어문자가 없는 8~128자의 가시 ASCII 문자열이어야 합니다."
            );
        }
    }

    public static IdempotencyKey parse(String headerValue) {
        return new IdempotencyKey(headerValue);
    }

    private static RotationBadRequestException invalid(String detail) {
        return new RotationBadRequestException(
                RotationProblemCode.VALIDATION_FAILED,
                detail,
                Map.of(
                        "errors",
                        List.of(new RotationFieldError(
                                "Idempotency-Key",
                                "8~128자의 가시 ASCII 문자열을 사용해 주세요."
                        ))
                )
        );
    }
}
