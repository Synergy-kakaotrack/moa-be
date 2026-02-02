# MOA Backend System Architecture

본 문서는 **MOA 백엔드 서비스가 개발 → 테스트 → 빌드 → 배포 → 운영**까지 어떻게 동작하는지를 설명한다.
아래 두 개의 아키텍처 다이어그램을 기준으로, **CI/CD 흐름**과 **운영(Runtime) 구조**를 분리하여 설명한다.

---

## 1. 아키텍처 개요

MOA 백엔드는 다음 원칙을 기반으로 설계되었다.

* **빌드와 실행의 분리**: 애플리케이션 빌드는 GitHub Actions에서만 수행하고, EC2는 실행만 담당한다.
* **검증 후 배포**: 모든 배포는 CI 테스트를 통과한 Docker 이미지로만 이루어진다.
* **컨테이너 기반 운영**: Spring Boot와 PostgreSQL은 Docker 컨테이너로 실행된다.
* **데이터 영속성 보장**: DB 데이터는 컨테이너 외부(EBS/Host Volume)에 저장된다.

---

## 2. CI / CD 아키텍처
![Image 1: CI/CD Architecture](../images/be-archi1.gif)
### 2.1 전체 흐름

1. 개발자가 로컬에서 코드를 작성하고 `main` 브랜치로 push/merge
2. GitHub Actions가 트리거되어 CI Workflow 실행
3. CI 성공 시 Publish Workflow 실행
4. Docker 이미지가 GHCR(GitHub Container Registry)에 push
5. Deploy Job이 SSH를 통해 EC2에 접속하여 배포 명령 실행

---

### 2.2 CI Workflow (테스트 단계)

**목적**
`main` 브랜치에 병합되는 코드가 최소한의 품질을 만족하는지 검증한다.

**수행 작업**

* Repository checkout
* JDK 설치
* PostgreSQL service 컨테이너 실행 (테스트용)
* `spring.profiles.active=ci` 환경에서 Gradle test 실행

**특징**

* 운영 환경(`prod`)과 분리된 `ci` 프로파일 사용
* 외부 API 키 없이도 테스트 가능
* 테스트 실패 시 이후 단계(Publish/Deploy)로 진행하지 않음

---

### 2.3 Publish Workflow (이미지 빌드 단계)

**목적**
테스트를 통과한 소스 코드로 **배포 가능한 Docker 이미지**를 생성한다.

**수행 작업**

* Docker Buildx 설정
* Docker 이미지 빌드
* 이미지 push

    * `latest`
    * `commit SHA`

**결과**

* EC2에서 바로 실행 가능한 이미지가 GHCR에 저장됨

---

### 2.4 Deploy Job (배포 트리거)

**목적**
EC2에서 최신 이미지를 기준으로 컨테이너를 재기동한다.

**방식: SSH 기반 배포**

* GitHub Actions Runner가 SSH(22번 포트)로 EC2에 접속
* EC2 내부에서 배포 명령을 실행

**실행되는 대표 명령**

* `docker compose pull`
* `docker compose up -d`

Deploy Job 자체가 배포를 수행하는 것이 아니라, **EC2에서 실행될 명령을 트리거**하는 역할만 담당한다.

---

## 3. Runtime 아키텍처
![Image 2: Architecture](../images/be-archi2.gif)

### 3.1 EC2 실행 환경

* AWS EC2 (Amazon Linux)
* Docker / Docker Compose 설치
* 애플리케이션 설정 경로: `/opt/moa`

    * `docker-compose.yml`
    * `moa.env`

EC2는 **빌드 책임을 가지지 않으며**, GHCR에서 이미지를 pull하여 실행만 담당한다.

---

### 3.2 Docker 구성

#### Docker Network (bridge)

* Spring Boot Container (`moa-backend`)

    * port: 8080
    * profile: prod
* PostgreSQL Container

    * port: 5432

두 컨테이너는 동일한 Docker 네트워크에서 통신하며,<br>
Spring Boot는 JDBC를 통해 PostgreSQL에 접근한다.

---

### 3.3 포트 흐름

* User / Browser → EC2 Host :80
* EC2 Host :80 → Spring Boot Container :8080 (port mapping)

DB 포트(5432)는 외부에 노출되지 않으며 내부 네트워크에서만 접근 가능하다.

---

### 3.4 데이터 영속성

* PostgreSQL 데이터는 Docker Volume(`moa_pgdata`)에 저장
* 해당 Volume은 EC2 Host의 EBS에 마운트됨

따라서:

* 컨테이너 재시작 / 재배포 시에도 데이터는 유지됨

---

### 3.5 외부 API 연동

* Spring Boot 컨테이너는 외부 LLM API(Gemini)와 HTTPS로 통신
* Gemini는 시스템 외부 의존성으로, 내부 인프라와 분리되어 있음

---

## 4. 한 문장 요약

> **MOA 백엔드는 테스트를 통과한 Docker 이미지만을 운영 환경에 배포하며,<br> EC2는 실행 환경으로만 사용되는 구조를 가진다.**
