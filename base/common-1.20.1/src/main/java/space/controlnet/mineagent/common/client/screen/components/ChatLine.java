package space.controlnet.mineagent.common.client.screen.components;

import space.controlnet.mineagent.core.session.ChatRole;
import java.util.List;

public record ChatLine(List<ChatSpan> spans, ChatRole role, boolean isMeta, int width, String plainText) {
}
