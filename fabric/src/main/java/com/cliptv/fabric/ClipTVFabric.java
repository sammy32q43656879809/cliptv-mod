package com.cliptv.fabric;

import com.cliptv.recorder.VideoRecorder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;

@Environment(EnvType.CLIENT)
public class ClipTVFabric implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("ClipTV/Fabric");

    @Override
    public void onInitializeClient() {
        LOGGER.info("[ClipTV] Loading for Fabric/Quilt (Minecraft 26.2)");

        // Capture a frame at the end of every client tick
        ClientTickEvents.END_CLIENT_TICK.register(client -> captureCurrentFrame());

        // Register client-side commands
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {

            // /clip — save last 120 s
            dispatcher.register(ClientCommandManager.literal("clip").executes(ctx -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.player == null) return 0;
                mc.player.sendMessage(Text.of("§a[ClipTV] §fSaving clip..."), false);
                VideoRecorder.saveClip().thenAccept(path -> {
                    if (path != null)
                        mc.execute(() -> mc.player.sendMessage(
                                Text.of("§a[ClipTV] §fClip saved: §e" + path.getFileName()), false));
                    else
                        mc.execute(() -> mc.player.sendMessage(
                                Text.of("§c[ClipTV] §fNo frames captured yet — play for a moment first."), false));
                });
                return 1;
            }));

            // /rec — toggle recording
            dispatcher.register(ClientCommandManager.literal("rec").executes(ctx -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.player == null) return 0;
                if (VideoRecorder.getState() == VideoRecorder.State.IDLE) {
                    VideoRecorder.startRecording();
                    mc.player.sendMessage(Text.of("§c● §f[ClipTV] Recording started. Type §e/rec §fagain to stop."), false);
                } else {
                    mc.player.sendMessage(Text.of("§a[ClipTV] §fStopping and encoding..."), false);
                    VideoRecorder.stopRecording().thenAccept(path -> {
                        if (path != null)
                            mc.execute(() -> mc.player.sendMessage(
                                    Text.of("§a[ClipTV] §fRecording saved: §e" + path.getFileName()), false));
                    });
                }
                return 1;
            }));
        });
    }

    private static void captureCurrentFrame() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        try {
            net.minecraft.client.util.ScreenshotRecorder.takeScreenshot(
                    mc.getFramebuffer(), img -> {
                try {
                    int iw = img.getWidth(), ih = img.getHeight();
                    BufferedImage bi = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_RGB);
                    for (int y = 0; y < ih; y++) {
                        for (int x = 0; x < iw; x++) {
                            bi.setRGB(x, y, img.getColorArgb(x, y) & 0xFFFFFF);
                        }
                    }
                    img.close();
                    VideoRecorder.onFrame(bi);
                } catch (Exception ignored) {
                }
            });
        } catch (Throwable ignored) {
        }
    }
}
