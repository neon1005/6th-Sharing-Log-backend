package gdg.sharinglog.config;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class WebOAuthSecurityConfigTest {

    @Test
    void normalizesConfiguredFrontendOrigin() {
        assertEquals(
                "https://frontend.example:8443",
                WebOAuthSecurityConfig.normalizeFrontendOrigin(
                        "  HTTPS://Frontend.Example:8443///  "
                )
        );
    }

    @Test
    void rejectsValuesThatAreNotHttpOrigins() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> WebOAuthSecurityConfig.normalizeFrontendOrigin(" ")
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> WebOAuthSecurityConfig.normalizeFrontendOrigin(
                                "ftp://frontend.example"
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> WebOAuthSecurityConfig.normalizeFrontendOrigin(
                                "https://frontend.example/path"
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> WebOAuthSecurityConfig.normalizeFrontendOrigin(
                                "https://frontend.example?preview=true"
                        )
                )
        );
    }
}
