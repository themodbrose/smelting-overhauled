package com.example.smeltingoverhauled;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;

public class SmeltingClient implements ClientModInitializer {
    public static KeyMapping openConfigKey;

    @Override
    public void onInitializeClient() {
        openConfigKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.smeltingoverhauled.config",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                KeyMapping.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openConfigKey.consumeClick()) {
                openConfigSafely(client);
            }
        });
    }

    private static void openConfigSafely(Object client) {
        try {
            SmeltingConfigScreen screen = new SmeltingConfigScreen();
            for (Method m : client.getClass().getMethods()) {
                if (m.getParameterCount() == 1 && m.getName().toLowerCase().contains("screen") && m.getName().toLowerCase().startsWith("set")) {
                    m.invoke(client, screen);
                    return;
                }
            }
        } catch (Exception ignored) {}
    }
}