package gdg.sharinglog.web.rotation.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;

import gdg.sharinglog.web.rotation.error.RotationBadRequestException;
import gdg.sharinglog.web.rotation.error.RotationProblemCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class IdempotencyKeyTest {

    @Test
    void acceptsVisibleAsciiBetweenEightAndOneHundredTwentyEightCharacters() {
        assertEquals("abcdefgh", IdempotencyKey.parse("abcdefgh").value());
        assertEquals(
                "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                IdempotencyKey.parse("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa").value()
        );
        assertEquals(128, IdempotencyKey.parse("x".repeat(128)).value().length());
    }

    @ParameterizedTest
    @MethodSource("invalidKeys")
    void rejectsMissingAmbiguousOrOutOfRangeValues(String value) {
        RotationBadRequestException exception = assertThrows(
                RotationBadRequestException.class,
                () -> IdempotencyKey.parse(value)
        );

        assertEquals(RotationProblemCode.VALIDATION_FAILED, exception.problem());
    }

    private static Stream<String> invalidKeys() {
        return Stream.of(
                null,
                "",
                "       ",
                "1234567",
                "x".repeat(129),
                " abcdefgh",
                "abcdefgh ",
                "abcd efgh",
                "abcd\tefgh",
                "abcd\nefgh",
                "가나다라마바사아"
        );
    }
}
