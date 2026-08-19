# Elastic Beanstalk HTTPS + 로컬 프론트 연결

아래 예시는 로컬 Vite 프론트 `http://localhost:5173`과 배포 백엔드
`https://api.example.com`을 연결하는 구성이다.

## 현재 Single instance 환경: sslip.io + Nginx + Let's Encrypt

로드 밸런서가 없는 Single instance 환경에서는 ALB/ACM 리스너 대신 인스턴스의
Nginx가 HTTPS를 종료한다. Elastic IP가 `43.200.12.73`이면 공개 백엔드 호스트는
`sharinglog-43-200-12-73.sslip.io`처럼 정한다. Elastic IP를 유지해야 이 호스트와
인증서, OAuth callback URL이 유지된다.

소스에 포함된 `.platform` 배포 설정은 다음 환경 변수를 사용한다. 매 배포 뒤
Certbot이 인증서를 발급하거나 기존 인증서를 재사용하고, Elastic Beanstalk가
기본 Nginx 설정을 다시 생성하더라도 별도의 `/etc/nginx/conf.d/https.conf`와
HTTP 리다이렉트 설정을 다시 만든다.

```properties
HTTPS_PUBLIC_HOST=sharinglog-43-200-12-73.sslip.io
CERTBOT_EMAIL=<인증서 만료 알림을 받을 이메일>
```

- 보안 그룹은 TCP `80`, `443`을 인터넷에 열고, 애플리케이션 포트 `5000`은 열지 않는다.
- 최초 발급과 인스턴스 교체 후 재발급에는 포트 `80`이 필요하다.
- `SERVER_PORT`를 생략하면 HTTPS 프록시는 Elastic Beanstalk Java SE 기본값인
  `5000`을 사용한다.
- 배포 훅은 `certbot-renew.timer`와 인증서 갱신 후 Nginx reload 훅을 활성화한다.
- Let’s Encrypt는 짧은 기간에 반복 발급하면 제한될 수 있으므로, 배포 실패를 반복하지
  않는다.

### 배포 파일 생성

JAR 파일만 업로드하면 `.platform`의 Nginx와 Certbot 훅이 포함되지 않는다. 항상
다음 작업으로 Elastic Beanstalk 소스 번들을 생성한다.

```powershell
$env:JAVA_HOME='C:\Users\SAMSUNG\.jdks\corretto-26.0.1'
.\gradlew.bat clean ebBundle
```

생성 파일은 `build/distributions/sharingLog-0.0.1-SNAPSHOT-eb.zip`이다. ZIP 내부에는
다음 항목이 루트 기준으로 함께 있어야 한다.

```text
application.jar
.platform/hooks/postdeploy/50-configure-certbot.sh
.platform/confighooks/postdeploy/50-configure-certbot.sh
.platform/scripts/configure-certbot.sh
.platform/nginx/conf.d/elasticbeanstalk/00-acme-challenge.conf
```

Elastic Beanstalk 콘솔에는 `build/libs/*.jar`가 아니라 이 ZIP을 업로드한다.

## 1. HTTPS 준비

1. Elastic Beanstalk 환경과 같은 리전의 AWS Certificate Manager에서
   `api.example.com` 인증서를 요청하고 DNS 검증을 완료한다.
2. `api.example.com` DNS 레코드를 Elastic Beanstalk 환경 CNAME으로 연결한다.
3. Elastic Beanstalk 콘솔에서 환경의 **Configuration > Load balancer > Edit**로
   이동한다.
4. 포트 `443`, 프로토콜 `HTTPS`, 발급한 ACM 인증서를 사용하는 리스너를
   추가하고 기본 프로세스로 전달한다.
5. 로드 밸런서 보안 그룹의 인바운드에 TCP 443을 허용한다. 인스턴스의
   애플리케이션 포트는 로드 밸런서 보안 그룹에서 오는 요청만 허용한다.
6. 443 동작을 확인한 후 포트 80 리스너를 HTTPS 443으로 리다이렉트한다.

`Load balancer` 설정을 편집할 수 없다면 Single instance 환경이다. 이 경우
Load balanced 환경으로 변경하는 방법을 권장한다. Single instance를 유지하면
인스턴스의 프록시 서버에 인증서와 443 리스너를 직접 구성해야 한다.

AWS 공식 문서:

- <https://docs.aws.amazon.com/elasticbeanstalk/latest/dg/configuring-https-elb.html>
- <https://docs.aws.amazon.com/elasticbeanstalk/latest/dg/configuring-https-httpredirect.html>

## 2. Elastic Beanstalk 런타임 환경 변수

**Configuration > Updates, monitoring, and logging > Runtime environment
variables**에 다음 값을 설정한다.

```properties
APP_FRONTEND_ORIGIN=http://localhost:5173
APP_OAUTH2_SUCCESS_URL=http://localhost:5173/
APP_OAUTH2_FAILURE_URL=http://localhost:5173/?error=true
APP_PUBLIC_BASE_URL=http://localhost:5173

SESSION_COOKIE_SECURE=true
SESSION_COOKIE_SAME_SITE=none

NAVER_CLIENT_ID=<네이버 클라이언트 ID>
NAVER_CLIENT_SECRET=<네이버 클라이언트 Secret>
```

- `APP_FRONTEND_ORIGIN`에는 경로와 끝 슬래시를 넣지 않는다.
- 실제 프론트 포트가 다르면 `5173`을 실제 포트로 변경한다.
- `SESSION_COOKIE_SECURE=true`와 `SameSite=None` 쿠키는 HTTPS 백엔드에서만
  정상 동작한다. 백엔드 접속 주소가 `http://`라면 이 구성으로 로그인 세션을
  유지할 수 없다.
- IntelliJ의 환경 변수는 로컬에서 실행한 백엔드에만 적용된다. 배포 백엔드는
  Elastic Beanstalk 런타임 환경 변수의 값을 사용한다.
- 배포 환경에서 `SPRING_PROFILES_ACTIVE=local`을 사용하지 않는다.
- 네이버 Client Secret은 프론트 환경 변수에 넣지 않는다.

## 3. 네이버 개발자센터

네이버 로그인 Callback URL에는 프론트 주소가 아니라 HTTPS 백엔드 callback을
등록한다.

```text
https://api.example.com/login/oauth2/code/naver
```

로컬 프론트의 `.env.local`에는 다음 값만 둔다.

```properties
VITE_BACKEND_ORIGIN=https://api.example.com
```

프론트는 API `fetch`에 `credentials: "include"`를 사용하고, 로그인 버튼은
`VITE_BACKEND_ORIGIN/oauth2/authorization/naver`로 브라우저 전체를 이동시켜야
한다.

## 4. 완료 확인

1. 로컬 프론트의 네이버 로그인 버튼이
   `https://api.example.com/oauth2/authorization/naver`로 이동한다.
2. 네이버 로그인 완료 후 `http://localhost:5173/`으로 돌아온다.
3. callback 응답의 `JSESSIONID`에 `Secure`, `HttpOnly`, `SameSite=None`이 있다.
4. 프론트의 `GET https://api.example.com/api/auth/me` 요청에 쿠키가 포함되고
   `200` JSON을 반환한다.
5. 비로그인 상태의 같은 요청은 `/login` 리다이렉트가 아닌 `401`을 반환한다.
6. API 응답에 아래 CORS 헤더가 있다.

```text
Access-Control-Allow-Origin: http://localhost:5173
Access-Control-Allow-Credentials: true
```

브라우저가 제3자 쿠키를 차단하도록 설정된 경우, `localhost`에서 배포 백엔드로
보내는 요청에 `JSESSIONID`가 포함되지 않을 수 있다. 이때는 개발 확인 중 해당
백엔드 사이트의 제3자 쿠키를 허용하거나, 프론트와 백엔드를 같은 site의 HTTPS
도메인으로 배포해 확인한다. CORS 설정만으로 브라우저의 쿠키 차단을 우회할 수는
없다.
