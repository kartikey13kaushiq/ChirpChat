package com.chirpchat.chat;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** An expected failure, rendered as an RFC 9457 problem detail with a stable {@code code}. */
public class ApiException extends ErrorResponseException {

    private ApiException(HttpStatus status, String code, String detail) {
        super(status, problem(status, code, detail), null);
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }

    public static ApiException notFound(String what) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", what + " not found");
    }

    public static ApiException conflict(String code, String detail) {
        return new ApiException(HttpStatus.CONFLICT, code, detail);
    }

    public static ApiException forbidden(String detail) {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", detail);
    }

    public static ApiException badGateway(String detail) {
        return new ApiException(HttpStatus.BAD_GATEWAY, "UPSTREAM_UNAVAILABLE", detail);
    }

    static Map<String, Object> none() {
        return Map.of();
    }
}
