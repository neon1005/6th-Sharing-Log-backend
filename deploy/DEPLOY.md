# EC2 배포 런북 (로그인 + 그룹 생성)

도메인 없이 `sslip.io` + Let's Encrypt로 HTTPS를 붙여서, 백엔드(Docker)와 프론트(정적 빌드)를 EC2 하나에 같이 띄운다.
구글 OAuth는 `localhost`가 아닌 주소는 리다이렉트 URI가 HTTPS여야 해서, 도메인을 사지 않고도 HTTPS를 받을 방법이 필요했다.

이 브랜치는 `develop`(팀원 neon1005가 만든 그룹 생성/초대/구글·네이버 로그인 구현) 기준이다. 프론트는 `feature/api-setup` 브랜치를 이 계약에 맞게 고친 버전을 쓴다.

## 0. 미리 필요한 것

- AWS 콘솔 로그인 (EC2 생성 권한)
- 이 프로젝트의 `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET`
- 네이버 로그인도 같이 등록되어 있어서 `NAVER_CLIENT_ID`/`NAVER_CLIENT_SECRET`도 필요 (안 쓸 거면 아무 문자열이나 넣어도 됨 — 앱 기동에만 필요, 실제 네이버 버튼만 안 누르면 문제없음)
- 구글 클라우드 콘솔 접근 권한 (리다이렉트 URI 추가할 사람)

## 1. EC2 인스턴스 생성

1. AWS 콘솔 → EC2 → 인스턴스 시작
2. AMI: **Ubuntu Server 22.04 LTS**
3. 인스턴스 유형: `t3.micro` (프리티어면 `t2.micro`)
4. 키 페어: 새로 만들거나 기존 것 선택 (SSH 접속용, `.pem` 파일 잘 보관)
5. 보안 그룹 인바운드 규칙: **22(SSH), 80(HTTP), 443(HTTPS)** 허용 — 8080은 열지 않는다 (백엔드는 Nginx를 통해서만 접근)
6. 시작 후 **퍼블릭 IPv4 주소** 확인 (예: `3.35.12.34`)

## 2. sslip.io 호스트네임 확정

퍼블릭 IP의 점(`.`)을 하이픈(`-`)으로 바꾸고 `.sslip.io`를 붙인다.

```
3.35.12.34  →  3-35-12-34.sslip.io
```

이 문서에서 `<SSLIP_HOST>`는 전부 이 값으로 바꿔서 진행한다. 브라우저에서 `http://<SSLIP_HOST>`로 접속하면 EC2로 연결되는지(아직 아무것도 안 떠서 에러 나는 게 정상) 먼저 확인해봐도 좋다.

## 3. 구글 클라우드 콘솔에 리다이렉트 URI 추가

APIs & Services → Credentials → 사용 중인 OAuth 2.0 클라이언트 ID → 승인된 리디렉션 URI에 추가:

```
https://<SSLIP_HOST>/login/oauth2/code/google
```

(기존 `localhost`용 URI는 그대로 두고 추가만 — 로컬 개발도 계속 되게)

## 4. SSH 접속 후 필요한 것들 설치

```bash
ssh -i <키파일>.pem ubuntu@<SSLIP_HOST>

sudo apt update
sudo apt install -y docker.io docker-compose-plugin nginx certbot python3-certbot-nginx git
sudo usermod -aG docker $USER
newgrp docker
```

## 5. 백엔드 배포

```bash
git clone https://github.com/minhyeong521/6th-Sharing-Log-backend.git
cd 6th-Sharing-Log-backend
git checkout develop   # 또는 이 배포 설정이 merge된 브랜치

cp .env.example .env
nano .env
# GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET, NAVER_CLIENT_ID, NAVER_CLIENT_SECRET,
# APP_FRONTEND_ORIGIN=https://<SSLIP_HOST>,
# APP_OAUTH2_SUCCESS_URL=https://<SSLIP_HOST>/,
# APP_OAUTH2_FAILURE_URL=https://<SSLIP_HOST>/?error=true,
# APP_PUBLIC_BASE_URL=https://<SSLIP_HOST> 채우기

docker compose up -d --build
docker compose logs -f   # "Started SharingLogApplication" 뜨는지 확인, Ctrl+C로 빠져나오기
```

**참고**: `Dockerfile`은 `eclipse-temurin:25-jdk`/`eclipse-temurin:25-jre` 이미지를 씁니다 (`build.gradle`의 툴체인이 일래스틱 빈스토크 자바 버전에 맞춰 25로 고정되어 있어서, 이미지도 25로 맞춰야 함). 만약 태그를 못 받아온다는 에러가 나면 [hub.docker.com/_/eclipse-temurin/tags](https://hub.docker.com/_/eclipse-temurin/tags)에서 25 관련 태그를 확인해서 `Dockerfile`의 두 `FROM` 줄을 맞는 태그로 바꿔주세요.

## 6. 프론트 빌드 & 배포

로컬(또는 EC2, Node 설치되어 있으면 어디든)에서:

```bash
git clone --branch feature/api-setup https://github.com/gdg-hongik-univ-program/6th-Sharing-Log-frontend.git
cd 6th-Sharing-Log-frontend
npm install
VITE_BACKEND_ORIGIN=https://<SSLIP_HOST> npm run build
```

`dist/` 폴더가 만들어지면 EC2로 복사:

```bash
scp -i <키파일>.pem -r dist/* ubuntu@<SSLIP_HOST>:/tmp/frontend-dist
ssh -i <키파일>.pem ubuntu@<SSLIP_HOST> "sudo mkdir -p /var/www/frontend && sudo cp -r /tmp/frontend-dist/* /var/www/frontend/"
```

## 7. Nginx 설정

EC2에서:

```bash
cd ~/6th-Sharing-Log-backend/deploy
sed "s/<SSLIP_HOST>/3-35-12-34.sslip.io/g" nginx.conf.template | sudo tee /etc/nginx/sites-available/sharinglog
# sed 명령의 <SSLIP_HOST> 부분을 실제 값으로 바꿔서 실행하세요

sudo ln -sf /etc/nginx/sites-available/sharinglog /etc/nginx/sites-enabled/sharinglog
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t   # 문법 에러 없는지 확인
sudo systemctl reload nginx
```

이 시점에 `http://<SSLIP_HOST>`로 접속하면 프론트 화면이 떠야 한다 (아직 HTTPS 전).

## 8. HTTPS 인증서 발급

```bash
sudo certbot --nginx -d <SSLIP_HOST>
```

이메일 입력, 약관 동의 후 발급됨. certbot이 `/etc/nginx/sites-available/sharinglog`에 443 블록과 인증서 경로를 자동으로 추가하고 Nginx를 리로드한다. 90일마다 만료되는데, certbot이 설치한 systemd timer가 자동 갱신을 처리한다 (`sudo systemctl status certbot.timer`로 확인 가능).

## 9. 최종 확인

브라우저로 `https://<SSLIP_HOST>` 접속:

1. "Google 계정으로 계속하기" 클릭
2. 구글 로그인 완료 → 저장된 초대 요청이 있으면 초대 화면으로 복귀하고, 없으면 `APP_OAUTH2_SUCCESS_URL`로 이동
3. "새 하우스를 만들고 싶어요" → 그룹 이름 입력 → `POST /api/groups` 201 확인
4. 초대 링크 발급 화면에서 링크 복사 → 다른 계정/시크릿창으로 링크 열기 → 로그인 → 초대 미리보기의 "그룹 가입하기" → 그룹 참여 성공 확인

## 문제 생겼을 때

- **`redirect_uri_mismatch`**: 3단계에서 등록한 URI와 실제 접속 주소(https, 정확한 sslip 호스트)가 정확히 일치하는지 확인
- **로그인 후 이상한 곳으로 감**: `.env`의 `APP_OAUTH2_SUCCESS_URL`을 확인. 초대 링크로 로그인을 시작했다면 원래 `/invite/{code}` 요청이 세션에 저장됐는지도 확인
- **`Invalid CORS request`**: `.env`의 `APP_FRONTEND_ORIGIN`이 실제 브라우저 origin과 정확히 같은지 확인. 경로나 끝 슬래시는 넣지 않는다
- **그룹 생성이 403**: CSRF 토큰 문제일 가능성이 큼 — 프론트가 `GET /api/auth/csrf`를 먼저 호출해서 토큰을 받아오는지 확인
- **백엔드 컨테이너가 안 뜸**: `docker compose logs`로 스택트레이스 확인 (대부분 `GOOGLE_CLIENT_ID`/`SECRET`/`NAVER_CLIENT_ID`/`SECRET` 중 하나 미설정)
