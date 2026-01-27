package com.moa.moa_backend.global.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // Request / Common (4xx)
    REQUIRED_HEADER_MISSING(HttpStatus.BAD_REQUEST, "REQ_001", "필수 헤더가 누락되었습니다."),
    INVALID_HEADER_VALUE(HttpStatus.BAD_REQUEST, "REQ_002", "헤더 값이 올바르지 않습니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "REQ_003", "요청 값이 올바르지 않습니다."),
    INVALID_QUERY_PARAM(HttpStatus.BAD_REQUEST, "REQ_004", "쿼리 파라미터가 올바르지 않습니다."),
    INVALID_JSON(HttpStatus.BAD_REQUEST, "REQ_005", "요청 JSON 형식이 올바르지 않습니다."),

    // Not Found (generic)
    NOT_FOUND(HttpStatus.NOT_FOUND, "REQ_404", "요청한 리소스를 찾을 수 없습니다."),

    //User (MVP: X-User-Id)
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USR_404", "사용자를 찾을 수 없습니다."),

    //Projects - 소유자 불일치도 404로 처리(존재 은닉)하는 것을 기본으로 추천
    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "PRJ_404", "프로젝트를 찾을 수 없습니다."),
    PROJECT_NAME_DUPLICATED(HttpStatus.CONFLICT, "PRJ_409", "이미 존재하는 프로젝트 이름입니다."),

    //drafts - TTL 만료는 410(Gone)로 분리
    DRAFT_NOT_FOUND(HttpStatus.NOT_FOUND, "DRF_404", "드래프트를 찾을 수 없습니다."),
    DRAFT_EXPIRED(HttpStatus.GONE, "DRF_410", "드래프트가 만료되었습니다."),
    DRAFT_ALREADY_COMMITTED(HttpStatus.CONFLICT, "DRF_409", "이미 처리된 드래프트입니다."),
    DRAFT_RECOMMENDATION_INVALID(HttpStatus.INTERNAL_SERVER_ERROR, "DRF_500", "드래프트 추천 결과가 유효하지 않습니다."),
    DRAFT_RECOMMENDATION_CONFIG_INVALID(HttpStatus.INTERNAL_SERVER_ERROR, "DRF_501", "드래프트 추천 설정이 유효하지 않습니다."),

    //scraps
    SCRAP_NOT_FOUND(HttpStatus.NOT_FOUND, "SCP_404", "스크랩을 찾을 수 없습니다."),
    SCRAP_CONTENT_CONVERSION_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "SCP_503", "스크랩 내용을 변환할 수 없습니다."),

    //server
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "SRV_500", "서버 오류가 발생했습니다."),
    EXTERNAL_DEPENDENCY_ERROR(HttpStatus.SERVICE_UNAVAILABLE, "SRV_503", "외부 시스템 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

    public HttpStatus httpStatus() {return httpStatus;}
    public String code() {return code;}
    public String message() {return message;}
}
