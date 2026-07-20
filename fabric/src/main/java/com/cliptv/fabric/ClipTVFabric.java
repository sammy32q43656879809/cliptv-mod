package com.cliptv.fabric;

import com.cliptv.ClipTVConstants;
import com.cliptv.recorder.VideoRecorder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

@Environment(EnvType.CLIENT)
public class ClipTVFabric implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("ClipTV/Fabric");

    @Override
    public void onInitializeClient() {
        LOGGER.info("[ClipTV] Loading for Fabric/Quilt (Minecraft 1.21.4)");

        // Register custom payload types
        PayloadTypeRegistry.playC2S().register(
                ClipTVHandshakePayload.ID, ClipTVHandshakePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(
                ClipTVServerCommandPayload.ID, ClipTVServerCommandPayload.CODEC);

        // Send handshake to server on join — server plugin checks for this
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            try {
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
                        .send(new ClipTVHandshakePayload(ClipTVConstants.MOD_VERSION));
                LOGGER.info("[ClipTV] Handshake sent to server");
            } catch (Exception e) {
                // Server doesn't have the plugin — that's fine
                LOGGER.debug("[ClipTV] Server doesn't support ClipTV: {}", e.getMessage());
            }
        });

        // Listen for server commands (start/stop recording on behalf of the server)
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
                .registerGlobalReceiver(ClipTVServerCommandPayload.ID, (payload, ctx) ->
                        ctx.client().execute(() -> handleServerCommand(payload.command())));

        // Capture a frame at the end of every client tick (20 fps)
        ClientTickEvents.END_CLIENT_TICK.register(client -> captureCurrentFrame());

        // Register client-side commands
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {

            // /clip — save last 120 s
            dispatcher.register(ClientCommandManager.literal("clip").executes(ctx -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                mc.player.sendMessage(Text.of("§a[ClipTV] §fSaving clip..."), false);
                VideoRecorder.saveClip().thenAccept(path -> {
                    if (path != null)
                        mc.execute(() -> mc.player.sendMessage(
                                Text.of("§a[ClipTV] §fClip saved: §e" + path.getFileName()), false));
                    else
                        mc.execute(() -> mc.player.sendMessage(
                                Text.of("§c[ClipTV] §fFailed to save clip (no frames captured yet)."), false));
                });
                return 1;
            }));

            // /rec — toggle recording
            dispatcher.register(ClientCommandManager.literal("rec").executes(ctx -> {
                MinecraftClient mc = MinecraftClient.getInstance();
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

    // ── Frame capture ────────────────────────────────────────────────────────

    /**
     * Reads the current frame from the Minecraft framebuffer and passes it to
     * {@link VideoRecorder}. Called from the render thread via WorldRenderEvents.
     */
    private static void captureCurrentFrame() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        try {
            // 1.21.11: screenshot capture is async via callback
            net.minecraft.client.util.ScreenshotRecorder.takeScreenshot(
                    mc.getFramebuffer(), img -> {
                try {
                    int iw = img.getWidth(), ih = img.getHeight();
                    BufferedImage bi = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_RGB);
                    for (int y = 0; y < ih; y++) {
                        for (int x = 0; x < iw; x++) {
                            // getColorArgb returns standard ARGB — drop alpha
                            bi.setRGB(x, y, img.getColorArgb(x, y) & 0xFFFFFF);
                        }
                    }
                    img.close();
                    VideoRecorder.onFrame(bi);
                } catch (Exception ignored) {
                }
            });
        } catch (Throwable t) {
            // Don't spam logs — capture errors are transient
        }
    }

    private static void handleServerCommand(String command) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        switch (command) {
            case "REC_START" -> {
                VideoRecorder.startRecording();
                mc.player.sendMessage(Text.of("§c● §f[ClipTV] Server started recording."), false);
            }
            case "REC_STOP"  -> VideoRecorder.stopRecording();
            case "CLIP"      -> VideoRecorder.saveClip();
        }
    }
}
