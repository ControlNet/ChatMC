package space.controlnet.mineagent.common.tools.http;

public record HttpToolEntry(String name, String value) {
    public HttpToolEntry {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required.");
        }
        value = value == null ? "" : value;
    }
}
