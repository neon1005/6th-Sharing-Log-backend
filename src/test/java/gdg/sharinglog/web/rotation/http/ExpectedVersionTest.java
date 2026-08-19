package gdg.sharinglog.web.rotation.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import gdg.sharinglog.web.rotation.error.RotationBadRequestException;
import gdg.sharinglog.web.rotation.error.RotationPreconditionRequiredException;
import gdg.sharinglog.web.rotation.error.RotationProblemCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ExpectedVersionTest {

    @Test
    void parsesAndFormatsCanonicalStrongVersionEtags() {
        assertEquals(0L, ExpectedVersion.parse("\"0\"").value());
        assertEquals(42L, ExpectedVersion.parse("\"42\"").value());
        assertEquals("\"42\"", new ExpectedVersion(42).toStrongEtag());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void requiresIfMatchHeader(String headerValue) {
        RotationPreconditionRequiredException exception = assertThrows(
                RotationPreconditionRequiredException.class,
                () -> ExpectedVersion.parse(headerValue)
        );

        assertEquals(RotationProblemCode.PRECONDITION_REQUIRED, exception.problem());
        assertEquals(428, exception.problem().status().value());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "7",
            "\"07\"",
            "\"-1\"",
            "\"+1\"",
            "W/\"7\"",
            "*",
            "\"7\", \"8\"",
            " \"7\"",
            "\"7\" ",
            "\"9223372036854775808\""
    })
    void rejectsAnythingExceptOneCanonicalStrongVersionEtag(String headerValue) {
        RotationBadRequestException exception = assertThrows(
                RotationBadRequestException.class,
                () -> ExpectedVersion.parse(headerValue)
        );

        assertEquals(RotationProblemCode.VALIDATION_FAILED, exception.problem());
    }

    @Test
    void rejectsNegativeProgrammaticVersion() {
        assertThrows(IllegalArgumentException.class, () -> new ExpectedVersion(-1));
    }
}
