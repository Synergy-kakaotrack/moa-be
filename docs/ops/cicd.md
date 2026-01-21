# CI/CD 운영 가이드 (MOA Backend)

## 1. 개요
MOA Backend는 GitHub Actions를 통해 아래 흐름으로 CI/CD를 운영한다

- CI: 테스트/검증 (PR + main push)
- Publish: CI 성공 시 GHCR에 이미지 빌드/푸시
- Deploy: Publish 이후 EC2에서 docker-compose로 최신 이미지 pull & 재기동

## 2. 워크플로우 구성

### 2.1 CI (`.github/workflows/ci.yml`)
- 트리거
    - pull_request (모든 PR)
    - push: main
- 주요 동작
    - GitHub Actions 서비스 컨테이너로 Postgres 실행
    - `SPRING_PROFILES_ACTIVE=ci`로 테스트 수행
    - Flyway migration 포함한 통합 테스트 성격

성공 기준
- `./gradlew test`가 0 exit code로 종료
- Postgres healthcheck 통과 및 테스트 완료

### 2.2 Publish/Deploy (`.github/workflows/publish.yml`)
- 트리거
    - workflow_run: CI 완료 이벤트
- 실행 조건(게이트)
    - `github.event.workflow_run.conclusion == 'success'`
    - `github.event.workflow_run.head_branch == 'main'`

주요 동작
- Publish
    - GHCR 로그인 후 Docker 이미지 빌드/푸시
    - 태그: `latest`, `${{ github.sha }}`
- Deploy
    - EC2에 SSH 접속
    - `/opt/moa`에서 `docker-compose pull && docker-compose up -d`

성공 기준
- GHCR push 성공
- EC2에서 compose pull/up 성공
- 대상 컨테이너가 "running" 상태로 확인되면 최종 성공으로 본다

## 3. 배포 환경/설정
- EC2 경로: `/opt/moa`
- 환경변수 주입: EC2 환경변수는 `/home/ec2-user/moa/moa.env` 파일로 구성 후 docker-composer에서 참조한다. 
  - 해당 파일은 수동 생성/관리하며 Git에는 포함하지 않는다.
- 배포 명령:
    - `/usr/local/bin/docker-compose pull`
    - `/usr/local/bin/docker-compose up -d`

## 4. 배포 성공/실패 판단 기준

### 4.1 성공
- GitHub Actions publish.yml: Deploy job이 성공(초록)
- EC2에서 아래가 만족:
    - `docker ps`에서 서비스 컨테이너가 running
    - 컨테이너가 재시작 루프가 아님 (짧은 시간 내 재시작 반복 X)

### 4.2 실패
- Publish 실패: GHCR 로그인/빌드/푸시 단계에서 실패
- Deploy 실패:
    - SSH 접속 실패
    - compose pull 실패
    - compose up 실패
    - 컨테이너가 즉시 종료/CrashLoop

## 5. 장애/실패 시 확인 동선 (체크리스트)

### 5.1 GitHub Actions에서 확인
1) CI 워크플로우가 실패했는지 확인 (PR Checks / Actions 탭)
2) publish.yml이 스킵인지 / 실패인지 확인
    - 스킵: CI 실패 or main 아님
    - 실패: build/push 또는 deploy 단계 에러
3) 실패한 step 로그에서 최초 에러 메시지 확인

### 5.2 EC2에서 확인 (SSH)
1) compose 상태/컨테이너 상태
- `cd /opt/moa`
- `/usr/local/bin/docker-compose ps`
- `docker ps --filter "name=moa-"`

2) 컨테이너 로그 확인
- `docker logs <container_name> --tail 80`

3) 이미지 pull 여부 확인
- `docker images | grep moa-backend`
- `docker inspect <container_name> | grep Image`

4) env 파일/설정 확인
- `.env` 존재 여부 및 키 누락 여부
- docker-compose.yml에서 env_file 참조 경로 확인

## 6. 자주 발생하는 실패 유형
- DB 연결 실패 / env 누락: 컨테이너 시작 직후 종료 → docker logs에서 원인 확인
- GHCR pull 권한/로그인 문제: compose pull 실패 → GHCR auth 확인
- 포트 충돌/기존 컨테이너 잔존: compose up 실패 → 기존 컨테이너/프로세스 확인
