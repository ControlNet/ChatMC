package space.controlnet.chatmc.common.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class ChatMCKeybinds {
    private static final String CATEGORY = "key.categories.chatmc";
    private static final String OPEN_TERMINAL_KEY = "key.chatmc.open_terminal";

    public static final KeyMapping OPEN_TERMINAL = new KeyMapping(
            OPEN_TERMINAL_KEY,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            CATEGORY
    );

    private ChatMCKeybinds() {
    }

    public static void init() {
        KeyMappingRegistry.register(OPEN_TERMINAL);
        ClientTickEvent.CLIENT_POST.register(client -> {
            while (OPEN_TERMINAL.consumeClick()) {
                openTerminal();
            }
        });
    }

    private static void openTerminal() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getConnection() == null) {
            return;
        }
        minecraft.getConnection().sendCommand("chatmc open");
    }
}
