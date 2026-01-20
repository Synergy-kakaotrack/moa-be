package com.moa.moa_backend.domain.scrap.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moa.moa_backend.global.error.ApiException;
import com.moa.moa_backend.global.error.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

public final class ScrapCursorCodec {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ScrapCursorCodec() {}

    public static String encode(Instant lastCapturedAt, Long lastScrapId) {
        try {
            Map<String, String> payload = Map.of(
                    "lastCapturedAt", lastCapturedAt.toString(),
                    "lastScrapId", String.valueOf(lastScrapId)
            );

            String json = OBJECT_MAPPER.writeValueAsString(payload);

            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "cursor 생성 실패");
        }
    }

    public static Cursor decodeOrNull(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;

        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor);
            String json = new String(decoded, StandardCharsets.UTF_8);

            @SuppressWarnings("unchecked")
            Map<String, Object> map = OBJECT_MAPPER.readValue(json, Map.class);

            Instant t = Instant.parse(String.valueOf(map.get("lastCapturedAt")));
            Long id = Long.parseLong(String.valueOf(map.get("lastScrapId")));

            return new Cursor(t, id);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.INVALID_QUERY_PARAM, "cursor 값이 올바르지 않습니다.");
        }
    }

    public record Cursor(Instant lastCapturedAt, Long lastScrapId) {}
}
