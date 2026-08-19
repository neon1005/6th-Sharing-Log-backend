package gdg.sharinglog;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.Cookie;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;

@SpringBootTest
class SharingLogApplicationTests {

    @Autowired
    private ServerProperties serverProperties;

    @Test
    void contextLoads() {
    }

    @Test
    void sessionCookieSupportsCrossSiteFrontendRequests() {
        Cookie cookie = serverProperties.getServlet().getSession().getCookie();

        assertEquals(Boolean.TRUE, cookie.getHttpOnly());
        assertEquals(Boolean.TRUE, cookie.getSecure());
        assertEquals(Cookie.SameSite.NONE, cookie.getSameSite());
    }

}
