package com.cliptv.neoforge;

import com.cliptv.ClipTVConstants;
import com.cliptv.recorder.VideoRecorder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@Mod(value = ClipTVConstants.MOD_ID, dist = Dist.CLIENT)
public class ClipTVNeoForge {

    private static final Logger LOGGER = LoggerFactory.getLogger("ClipTV/NeoForge");

    public ClipTVNeoForge(IEventBus modBus) {
        LOGGER.info("[ClipTV] Loading for NeoForge (Minecraft 1.21.4)");

        // Register network payloads
        modBus.addListener(this::onRegisterPayloads);

        // Register game events via NeoForge event bus
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(this::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
    }

    private void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar reg = event.registrar(ClipTVConstants.MOD_VERSION);

        reg.playToServer(
                ClipTVHandshakePayload.TYPE,
                ClipTVHandshakePayload.CODEC,
                (payload, ctx) -> {}); // server handles this as a vanilla plugin message

        reg.playToClient(
                ClipTVServerCommandPayload.TYPE,
                ClipTVServerCommandPayload.CODEC,
                (payload, ctx) -> ctx.workHandler().execute(() ->
                        handleServerCommand(payload.command())));
    }

    private void onPlayerLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
        // Send handshake — server plugin will detect and flag this player as mod-enabled
        try {
            PacketDistributor.sendToServer(new ClipTVHandshakePayload(ClipTVConstants.MOD_VERSION));
            LOGGER.info("[ClipTV] Handshake sent to server");
        } catch (Exception e) {
            LOGGER.debug("[ClipTV] Server doesn't support ClipTV: {}", e.getMessage());
        }
    }

    private void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        captureCurrentFrame();
    }

    private void onRegisterCommands(RegisterClientCommandsEvent event) {
        var dispatcher = event.getDispatcher();

        dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal("clip")
                .executes(ctx -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.player.displayClientMessage(Component.literal("§a[ClipTV] §fSaving clip..."), false);
                    VideoRecorder.saveClip().thenAccept(path -> {
                        if (path != null)
                            mc.execute(() -> mc.player.displayClientMessage(
                                    Component.literal("§a[ClipTV] §fClip saved: §e" + path.getFileName()), false));
                    });
                    return 1;
                }));

        dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal("rec")
                .executes(ctx -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (VideoRecorder.getState() == VideoRecorder.State.IDLE) {
                        VideoRecorder.startRecording();
                        mc.player.displayClientMessage(
                                Component.literal("§c● §f[ClipTV] Recording started. Type §e/rec §fagain to stop."), false);
                    } else {
                        mc.player.displayClientMessage(
                                Component.literal("§a[ClipTV] §fStopping and encoding..."), false);
                        VideoRecorder.stopRecording();
                    }
                    return 1;
                }));
    }

    private static void captureCurrentFrame() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        try {
            var fb = mc.getMainRenderTarget();
            var img = new net.minecraft.client.screenshot.ScreenshotRecorder(fb.width, fb.height);
            // NeoForge: grab framebuffer pixels via LWJGL
            int w = fb.width, h = fb.height;
            java.nio.ByteBuffer buf = org.lwjgl.BufferUtils.createByteBuffer(w * h * 4);
            com.mojang.blaze3d.systems.RenderSystem.bindTexture(fb.getColorTextureId());
            org.lwjgl.opengl.GL11.glGetTexImage(
                    org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0,
                    org.lwjgl.opengl.GL12.GL_BGRA,
                    org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, buf);
            BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int idx = ((h - 1 - y) * w + x) * 4; // flip Y
                    int b2 = buf.get(idx)     & 0xFF;
                    int g2 = buf.get(idx + 1) & 0xFF;
                    int r2 = buf.get(idx + 2) & 0xFF;
                    bi.setRGB(x, y, (r2 << 16) | (g2 << 8) | b2);
                }
            }
            VideoRecorder.onFrame(bi);
        } catch (Exception ignored) {}
    }

    private static void handleServerCommand(String command) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        switch (command) {
            case "REC_START" -> {
                VideoRecorder.startRecording();
                mc.player.displayClientMessage(
                        Component.literal("§c● §f[ClipTV] Server started recording."), false);
            }
            case "REC_STOP" -> VideoRecorder.stopRecording();
            case "CLIP"     -> VideoRecorder.saveClip();
        }
    }
}
