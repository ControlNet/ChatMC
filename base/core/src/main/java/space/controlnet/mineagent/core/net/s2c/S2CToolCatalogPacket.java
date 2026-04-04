package space.controlnet.mineagent.core.net.s2c;

import space.controlnet.mineagent.core.tools.ToolCatalogEntry;

import java.util.List;

public record S2CToolCatalogPacket(int protocolVersion, List<ToolCatalogEntry> tools) {
}
