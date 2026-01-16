package com.moa.moa_backend.global.filter;

import com.moa.moa_backend.domain.user.repository.UserRepository;
import com.moa.moa_backend.global.error.ApiException;
import com.moa.moa_backend.global.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class UserIdFilter extends OncePerRequestFilter {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ID_ATTRIBUTE = "userId";

    private final UserRepository userRepository;
    public UserIdFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String userIdHeader = request.getHeader(USER_ID_HEADER);

        //헤더 누락
        if(userIdHeader == null || userIdHeader.isEmpty()) {
            throw new ApiException(ErrorCode.REQUIRED_HEADER_MISSING);
        }
        //todo : 숫자 파싱
        Long userId;
        try {
            userId = Long.parseLong(userIdHeader);
        }catch (NumberFormatException e) {
            throw new ApiException(ErrorCode.INVALID_HEADER_VALUE);
        }

        //todo : userId > 0
        if(userId <= 0) {
            throw new ApiException(ErrorCode.INVALID_HEADER_VALUE);
        }

        if(!userRepository.existsById(userId)){
            throw new ApiException(ErrorCode.USER_NOT_FOUND);
        }

        //todo : request에 저장
        request.setAttribute(USER_ID_ATTRIBUTE, userId);

        //todo : 다음 필터 혹은 컨트롤러로 전달
        filterChain.doFilter(request, response);
    }

}
