package com.tuneflow.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuneflow.common.exception.ErrorCode;
import com.tuneflow.common.exception.ErrorResponseFactory;
import com.tuneflow.common.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Returns {@code 401 UNAUTHENTICATED} with the standard error envelope instead of the servlet
 * container's HTML error page.
 *
 * <p>This matters for the Flutter client: a 401 is the signal to attempt a refresh-token exchange,
 * whereas 403 means "authenticated but not allowed" and must not trigger a token refresh loop.
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = ErrorResponseFactory.of(ErrorCode.UNAUTHENTICATED, request.getRequestURI());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
