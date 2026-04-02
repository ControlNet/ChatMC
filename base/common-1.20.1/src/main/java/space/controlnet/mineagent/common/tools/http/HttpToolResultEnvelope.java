package space.controlnet.mineagent.common.tools.http;

import java.util.Objects;

public record HttpToolResultEnvelope(
        String kind,
        HttpToolRequest request,
        HttpToolResponse response,
        HttpToolFailure failure,
        boolean truncated
) {
    public static final String KIND = "http_result";

    public HttpToolResultEnvelope(HttpToolRequest request, HttpToolResponse response, boolean truncated) {
        this(KIND, request, response, null, truncated);
    }

    public HttpToolResultEnvelope(HttpToolRequest request, HttpToolFailure failure, boolean truncated) {
        this(KIND, request, null, failure, truncated);
    }

    public HttpToolResultEnvelope {
        if (!KIND.equals(kind)) {
            throw new IllegalArgumentException("kind must be http_result.");
        }
        request = Objects.requireNonNull(request, "request");
        if (response == null && failure == null) {
            throw new IllegalArgumentException("response or failure is required.");
        }
        if (response != null && failure != null) {
            throw new IllegalArgumentException("response and failure are mutually exclusive.");
        }
    }
}
