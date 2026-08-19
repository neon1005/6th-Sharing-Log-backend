#### 컨벤션
git switch -c feature/#50-group-api-check-ui      # 타입/이슈번호-작업명

#### 배포 흐름

코딩 -> 빌드(build) -> 아래 명령어 3개 powershell 입력

Set-Location 'C:\Users\SAMSUNG\Desktop\Sharing Log\sharingLog' # pc마다 다름

$env:JAVA_HOME='C:\Users\SAMSUNG\.jdks\corretto-26.0.1'

.\gradlew.bat clean ebBundle

-> zip 파일 배포(업로드)

참고) swagger.ui 
브라우저에 입력 -> https://sharinglog-43-200-12-73.sslip.io/swagger-ui/index.html

배포된 최신 버전 서버의 API 확인 가능

#### 동기화(최신화) 흐름

git stash push -u -m "before upstream sync" # 작업한 것 임시 저장
git fetch upstream
git merge upstream/develop
git stash pop # 임시 저장한 것 꺼내기


#### 프론트와의 연결 관련

BS 환경변수 (로컬 기준)
APP_FRONTEND_ORIGIN = http://localhost:5173
APP_OAUTH2_FAILURE_URL = http://localhost:5173/?error=true
APP_OAUTH2_SUCCESS_URL = http://localhost:5173/
APP_PUBLIC_BASE_URL = http://localhost:5173


BS 환경변수 (배포)
APP_FRONTEND_ORIGIN = https://6th-sharing-log-frontend-teal.vercel.app
APP_OAUTH2_FAILURE_URL = https://6th-sharing-log-frontend-teal.vercel.app/?error=true
APP_OAUTH2_SUCCESS_URL = https://6th-sharing-log-frontend-teal.vercel.app/
APP_PUBLIC_BASE_URL = https://sharinglog-43-200-12-73.sslip.io

BS 환경변수 공통
SERVER_PORT = 5000
SESSION_COOKIE_SAME_SITE = none
SESSION_COOKIE_SECURE = true

프론트는 API 요청에 credentials: "include"를 사용해야 합니다.
------------------------------------------------------------------------------------
백엔드에서 한 것 / 확인할 것
EB 공개 백엔드를 HTTPS로 열었습니다.
https://sharinglog-43-200-12-73.sslip.io

Nginx → Spring Boot 프록시에 X-Forwarded-Proto: https를 전달해, 네이버 OAuth 콜백 URL이 HTTPS로 만들어지게 했습니다.

실제 OAuth 시작 요청에서 아래가 확인됐습니다.
JSESSIONID에 Secure; HttpOnly; SameSite=None
네이버 콜백 URL이
https://sharinglog-43-200-12-73.sslip.io/login/oauth2/code/naver



프론트 담당자가 할 것
VITE_BACKEND_ORIGIN=https://sharinglog-43-200-12-73.sslip.io
Vite는 반드시 http://localhost:5173에서 실행해야 합니다. 포트가 달라지면 백엔드 CORS 허용 Origin과 불일치합니다.
로그인 버튼은 API 호출이 아니라 브라우저 페이지 이동으로 실행합니다.
window.location.href =
`${import.meta.env.VITE_BACKEND_ORIGIN}/oauth2/authorization/naver`;
로그인 후 인증이 필요한 API 요청에는 반드시 쿠키를 포함합니다.
fetch(`${import.meta.env.VITE_BACKEND_ORIGIN}/api/auth/me`, {
credentials: 'include',
});
프론트에 네이버 Callback URL을 만들거나 localhost:5173/login/oauth2/code/naver를 등록할 필요는 없습니다.
JSESSIONID는 프론트가 직접 저장·설정하지 않습니다. 브라우저가 처리합니다.



## Google 로그인 실행 방법
  
Google Cloud Console에서 OAuth 2.0 Client ID를 만들고 승인된 리디렉션 URI에 아래 주소를 등록합니다.

```text
http://localhost:8080/login/oauth2/code/google
```

PowerShell에서 앱을 실행하기 전에 발급받은 값을 환경변수로 설정합니다.

```powershell
Set-Location -LiteralPath "C:\Users\SAMSUNG\Desktop\Sharing Log\sharingLog"
$env:GOOGLE_CLIENT_ID="발급받은-client-id"
$env:GOOGLE_CLIENT_SECRET="발급받은-client-secret"
$env:JAVA_HOME="C:\Users\SAMSUNG\.jdks\corretto-26.0.1"
.\gradlew.bat bootRun
```

그다음 브라우저에서 `http://localhost:8080/login`에 접속하고 `구글로 로그인하기`를 클릭합니다.

환경변수를 설정하지 않아도 앱은 개발용 기본값으로 실행되지만, Google에서는 `invalid_client` 오류가 표시됩니다.

### IntelliJ에서 실행하는 경우

PowerShell에 설정한 `$env:GOOGLE_CLIENT_ID`와 `$env:GOOGLE_CLIENT_SECRET`은 IntelliJ 실행 버튼에는 자동으로 전달되지 않습니다.

IntelliJ 오른쪽 위 실행 설정에서 `SharingLogApplication`을 선택한 뒤 `Edit Configurations...`를 열고, `Environment variables`에 아래처럼 추가합니다.

```text
GOOGLE_CLIENT_ID=발급받은-client-id;GOOGLE_CLIENT_SECRET=발급받은-client-secret
```

Google에서 `401 invalid_client`가 뜨면 대부분 아래 중 하나입니다.

- 환경변수가 IDE 실행 설정에 들어가지 않아 `client-id`가 비어 있거나 잘못 전달됨
- Google Cloud Console에서 만든 OAuth Client가 `웹 애플리케이션` 타입이 아님
- Client ID/Secret을 복사할 때 앞뒤 공백이나 따옴표가 들어감
- 삭제했거나 다른 프로젝트의 OAuth Client ID를 사용함


## 로그아웃 테스트 방법

### 브라우저 테스트 방법

1. 서버 실행
```$env:JAVA_HOME='C:\Users\SAMSUNG\.jdks\corretto-26.0.1'
.\gradlew.bat bootRun
```

2.브라우저에서 로그인
```
http://localhost:8080/login
```

3. 로그인 완료 후 개발자도구 Console에서 실행
```
fetch('/api/auth/logout', {
method: 'POST',
credentials: 'include'
}).then(res => console.log(res.status))
```

4. 콘솔에 204가 찍히면 성공입니다.

5. 새로고침하거나 /로 가면 로그인되지 않은 상태로 보여야 합니다.


### POSTMAN 테스트 방법

1. 브라우저에서 먼저 Google 로그인까지 완료합니다.

2. 개발자도구 → Application → Cookies → http://localhost:8080에서 JSESSIONID 값을 복사합니다.

3. Postman에서 요청 생성:
```
POST http://localhost:8080/api/auth/logout
```


4. Headers 또는 Cookies에 추가:
```
Cookie: JSESSIONID=복사한값
```

5. Send 클릭.

6. 응답이 이렇게 오면 성공:204 No Content

+)  로그아웃:
    지금 설정에서는 /api/auth/logout만 CSRF 예외로 빼뒀기 때문에 
    Postman에서 CSRF 토큰 없이 바로 테스트할 수 있다. 
    모바일 앱에서도 같은 방식으로 세션 쿠키를 포함해서 
    POST /api/auth/logout 호출하면 된다.