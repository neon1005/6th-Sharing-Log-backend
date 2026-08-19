package gdg.sharinglog.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class S3Config { // AWS S3와 통신할 S3 Client 를 생성해 스프링 빈으로 등록하는 설정
                        // cloud.aws.region 값을 주입받아 해당 리전에 맞는 S3 Client를 만들고
                        // 다른 서비스에서 주입받아 재사용할 수 있도록 한다
    @Bean
    public S3Client s3Client(@Value("${cloud.aws.region}") String region) {
        return S3Client.builder()
                .region(Region.of(region))
                .build();
    }
}
