package gdg.sharinglog.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ElasticBeanstalkHttpsConfigurationTest {

    private static final Path CONFIGURE_CERTBOT = Path.of(
            ".platform",
            "scripts",
            "configure-certbot.sh"
    );
    private static final Path ACME_CHALLENGE = Path.of(
            ".platform",
            "nginx",
            "conf.d",
            "elasticbeanstalk",
            "00-acme-challenge.conf"
    );

    @Test
    void recreatesTheHttpsListenerAfterElasticBeanstalkRewritesNginx() throws IOException {
        String script = Files.readString(CONFIGURE_CERTBOT);

        assertThat(script)
                .contains("certbot certonly")
                .contains("--webroot")
                .contains("/etc/nginx/conf.d/https.conf")
                .contains("listen 443 ssl;")
                .contains("ssl_certificate /etc/letsencrypt/live/$HTTPS_PUBLIC_HOST/fullchain.pem;")
                .contains("ssl_certificate_key /etc/letsencrypt/live/$HTTPS_PUBLIC_HOST/privkey.pem;");
        assertThat(script.indexOf("cat > \"$HTTPS_CONFIG\""))
                .isLessThan(script.indexOf("nginx -t"));
    }

    @Test
    void forwardsTheOriginalHttpsRequestToSpringBoot() throws IOException {
        String script = Files.readString(CONFIGURE_CERTBOT);

        assertThat(script)
                .contains("proxy_pass http://127.0.0.1:$APPLICATION_PORT;")
                .contains("proxy_set_header X-Forwarded-Proto \\$scheme;")
                .contains("proxy_set_header X-Forwarded-Host \\$host;")
                .contains("proxy_set_header X-Forwarded-Port 443;");
    }

    @Test
    void keepsTheHttpChallengeReachableForIssuanceAndRenewal() throws IOException {
        String challengeConfiguration = Files.readString(ACME_CHALLENGE);
        String script = Files.readString(CONFIGURE_CERTBOT);

        assertThat(challengeConfiguration)
                .contains("location ^~ /.well-known/acme-challenge/")
                .contains("root /var/lib/letsencrypt;")
                .contains("try_files $uri =404;");
        assertThat(script)
                .contains("systemctl enable --now certbot-renew.timer")
                .contains("/etc/letsencrypt/renewal-hooks/deploy/50-reload-nginx.sh");
    }

    @Test
    void readsRuntimePropertiesFromElasticBeanstalk() throws IOException {
        String script = Files.readString(CONFIGURE_CERTBOT);

        assertThat(script)
                .contains("/opt/elasticbeanstalk/bin/get-config")
                .contains("environment -k \"$name\"")
                .contains("environment_value SERVER_PORT 5000");
    }
}
