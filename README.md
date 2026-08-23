# 같이살기 - 백엔드

> 함께 사는 사람들의 공동 업무와 생활 공간을 편리하게 관리하는 셰어하우스 생활 관리자

같이살기는 셰어하우스, 기숙사, 공동주택처럼 여러 사람이 함께 생활하는 공간에서 발생하는 불편을 줄이기 위해 만들어졌습니다.

집안일을 구성원에게 공평하게 배정하고, 공동 공간의 예약 일정과 대타 요청을 한곳에서 관리할 수 있습니다.

## 주요 기능
### 소셜 로그인과 사용자 관리

- Google 및 Naver 계정으로 간편 로그인
- 최초 로그인 시 사용할 닉네임 설정
- 내 닉네임 조회 및 수정
- 세션 기반 로그인 유지 및 로그아웃

### 하우스 생성과 참여

- 새로운 하우스 생성
- 초대 코드를 이용한 하우스 참여
- 참여 중인 여러 하우스 조회 및 현재 사용할 하우스 선택
- 하우스 이름과 주소 조회 및 수정
- 초대 코드 재발급
- 하우스 나가기 및 하우스 삭제

### 멤버 관리

- 하우스 구성원 목록 조회 및 검색
- 구성원별 역할 확인
- 관리자의 멤버 강퇴 및 관리자 승격
- 하우스별 구성원 정보 관리

### 공동 업무와 로테이션

- 매일·매주·격주 단위의 반복 업무 생성 및 관리
- 참여 멤버를 기준으로 공동 업무 담당자 자동 배정
- 주간 및 달력 형태로 로테이션 일정 확인
- 내 일정과 하우스 전체 일정 구분 조회
- 업무 완료 및 완료 취소
- 마감이 지난 미완료 업무와 완료 기록 확인

### 대타 요청과 알림

- 담당 업무의 대타 요청 생성
- 다른 구성원의 대타 요청 수락 및 거절
- 마감 임박 업무와 대타 요청 알림 확인
- Web Push를 이용한 기기 알림 지원

### 공동 공간 예약

- 하우스에서 사용하는 공동 공간 등록 및 관리
- 날짜별 공간 예약 현황 조회
- 원하는 시간의 공간 예약 및 예약 취소
- 더 이상 사용하지 않는 예약 공간 삭제

### 반응형 웹과 PWA

- PC에서는 사이드바, 모바일에서는 하단 내비게이션 제공
- 화면 크기에 맞춰 자연스럽게 바뀌는 반응형 UI
- PWA Manifest와 앱 아이콘 적용
- 모바일 홈 화면에 추가하여 앱과 유사한 형태로 사용 가능

## 기술 스택 
### Backend

- Spring Boot 4.1.0
- Java 25
- Gradle
- H2(dev)+MySQL(prod) 
- Flyway
- Spring Security OAuth 2.0(Google, Naver)
- Spring Data JPA
- Spring Scheduling
- Web Push
- AWS Elastic Beanstalk

### 협업 및 배포

- Git / GitHub
- Vercel
- AWS
- Swagger

## 배포 링크
- 프론트엔드: [https://6th-sharing-log-frontend-teal.vercel.app](https://6th-sharing-log-frontend-teal.vercel.app)
- API 문서 (Swagger UI): [https://sharinglog-43-200-12-73.sslip.io/swagger-ui/index.html](https://sharinglog-43-200-12-73.sslip.io/swagger-ui/index.html)

## 프로젝트 구조

```
src/main/java/gdg/sharinglog/
├─ domain/       # JPA 엔티티 
├─ repository/   # Spring Data JPA 리포지토리 
├─ service/      # 비즈니스 로직 
├─ web/          # REST 컨트롤러 + 요청/응답 DTO 
├─ config/       # 스프링 설정 (OAuth2 보안, 스케줄링, S3, 레거시 스키마 마이그레이션 등)
└─ rotation/     # 프레임워크 의존 없는 순수 로테이션 배정 알고리즘 (engine, recurrence 규칙 계산)
```
