package com.vikashbasant.paymentsai.web;

import com.vikashbasant.paymentsai.mt103.Mt103ParseException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(Mt103ParseException.class)
    ProblemDetail parseError(Mt103ParseException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, StringIndexOutOfBoundsException.class,
            java.time.format.DateTimeParseException.class})
    ProblemDetail mappingError(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY,
                "Message cannot be converted: " + e.getMessage() + ". Run /api/mt103/validate first.");
    }
}
