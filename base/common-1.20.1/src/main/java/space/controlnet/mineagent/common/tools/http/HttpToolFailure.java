package space.controlnet.mineagent.common.tools.http;

public record HttpToolFailure(String code, String message) {
    public HttpToolFailure {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code is required.");
        }
        message = message == null ? "" : message;
    }
}
