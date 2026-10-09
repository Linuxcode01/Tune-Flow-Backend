package com.tuneflow.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuneflow.common.exception.ErrorCode;
import com.tuneflow.common.exception.ErrorResponseFactory;
import com.tuneflow.common.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Returns {@code 403 FORBIDDEN} with the standard error envelope for authenticated-but-unauthorized calls. */
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = ErrorResponseFactory.of(ErrorCode.FORBIDDEN, request.getRequestURI());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
