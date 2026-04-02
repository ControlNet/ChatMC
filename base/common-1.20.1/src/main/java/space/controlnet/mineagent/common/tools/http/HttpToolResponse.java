package space.controlnet.mineagent.common.tools.http;

import java.util.List;

public record HttpToolResponse(
        int statusCode,
        String finalUrl,
        List<HttpToolEntry> headers,
        String contentType,
        String bodyText,
        String bodyBase64,
        long bodyBytes
) {
    public HttpToolResponse {
        if (statusCode < 100 || statusCode > 599) {
            throw new IllegalArgumentException("statusCode must be between 100 and 599.");
        }
        if (finalUrl == null || finalUrl.isBlank()) {
            throw new IllegalArgumentException("finalUrl is required.");
        }
        headers = headers == null ? List.of() : List.copyOf(headers);
        contentType = contentType == null || contentType.isBlank() ? null : contentType;
        HttpToolRequest.validateExclusiveBodyFields(bodyText, bodyBase64, "Response");
        if (bodyBytes < 0L) {
            throw new IllegalArgumentException("bodyBytes must be greater than or equal to 0.");
        }
    }
}
