package com.mudassir.framework.api;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * AutomationExercise always answers HTTP 200 and puts the real status in the body
 * as "responseCode". This wrapper exposes both so tests can assert on the business code.
 */
public record ApiResult(int httpStatus, JsonNode body) {

    public int responseCode() {
        return body.path("responseCode").asInt(-1);
    }

    public String message() {
        return body.path("message").asText("");
    }
}
