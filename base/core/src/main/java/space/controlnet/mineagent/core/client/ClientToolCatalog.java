package space.controlnet.mineagent.core.client;

import space.controlnet.mineagent.core.tools.ToolCatalogEntry;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class ClientToolCatalog {
    private static final AtomicReference<List<ToolCatalogEntry>> TOOLS = new AtomicReference<>(List.of());
    private static final AtomicLong VERSION = new AtomicLong();

    private ClientToolCatalog() {
    }

    public static List<ToolCatalogEntry> get() {
        return TOOLS.get();
    }

    public static long version() {
        return VERSION.get();
    }

    public static void set(List<ToolCatalogEntry> tools) {
        TOOLS.set(tools == null ? List.of() : List.copyOf(tools));
        VERSION.incrementAndGet();
    }
}
