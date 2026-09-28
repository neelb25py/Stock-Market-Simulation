package org.api.stockmarket.modules.competition;

import org.springframework.http.HttpStatus;

public class CompetitionException extends RuntimeException {
    private final HttpStatus status;

    public CompetitionException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() { return status; }
}
