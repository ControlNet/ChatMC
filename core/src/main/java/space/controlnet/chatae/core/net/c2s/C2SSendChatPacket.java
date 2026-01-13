package space.controlnet.chatae.core.net.c2s;

public record C2SSendChatPacket(int protocolVersion, String text, String clientLocale, String aiLocaleOverride) {
}
