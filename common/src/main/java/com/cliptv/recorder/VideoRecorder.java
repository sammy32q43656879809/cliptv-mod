package com.cliptv.recorder;

import com.cliptv.ClipTVConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.*;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/**
 * Core recording state machine — platform-agnostic.
 *
 * Platforms call {@link #onFrame(BufferedImage)} from their render hook.
 * The recorder handles the clip circular buffer and /rec continuous recording.
 */
public class VideoRecorder {

    private static final Logger LOGGER = LoggerFactory.getLogger("ClipTV/Recorder");
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    public enum State { IDLE, RECORDING }

    private static volatile State state = State.IDLE;

    /** Circular buffer for /clip — stores JPEG-compressed frames. */
    private static final Deque<byte[]> clipBuffer = new ArrayDeque<>(ClipTVConstants.MAX_BUFFER_FRAMES + 1);

    /** Frames collected for /rec. Written to temp dir, encoded on stop. */
    private static final List<byte[]> recFrames = new ArrayList<>();

    /** Frame counter (used to implement FPS throttle). */
    private static long lastCaptureNs = 0;
    private static final long FRAME_INTERVAL_NS =
            (long) (1_000_000_000L / ClipTVConstants.CLIP_FPS);

    /** Executor for heavy encode work — avoids blocking the render thread. */
    private static final ExecutorService ENCODER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ClipTV-Encoder");
        t.setDaemon(true);
        return t;
    });

    // ── Public API called by platforms ──────────────────────────────────────

    /**
     * Called by the render-hook mixin on every rendered frame.
     * The image must be right-side-up (Minecraft framebuffers are flipped).
     */
    public static void onFrame(BufferedImage frame) {
        long now = System.nanoTime();
        if (now - lastCaptureNs < FRAME_INTERVAL_NS) return;
        lastCaptureNs = now;

        ENCODER.submit(() -> {
            try {
                byte[] jpeg = toJpeg(frame);
                addToClipBuffer(jpeg);
                if (state == State.RECORDING) {
                    recFrames.add(jpeg);
                }
            } catch (Exception e) {
                LOGGER.warn("Frame capture failed", e);
            }
        });
    }

    /** /rec start */
    public static synchronized void startRecording() {
        if (state == State.RECORDING) return;
        recFrames.clear();
        state = State.RECORDING;
        LOGGER.info("[ClipTV] Recording started");
    }

    /** /rec stop → encode and save. Returns the saved file path. */
    public static synchronized CompletableFuture<Path> stopRecording() {
        if (state != State.RECORDING) return CompletableFuture.completedFuture(null);
        state = State.IDLE;
        List<byte[]> frames = new ArrayList<>(recFrames);
        recFrames.clear();
        LOGGER.info("[ClipTV] Recording stopped — {} frames", frames.size());
        return CompletableFuture.supplyAsync(() -> encode(frames, "rec"), ENCODER);
    }

    /** /clip → encode last 120 s from the circular buffer. */
    public static CompletableFuture<Path> saveClip() {
        List<byte[]> snapshot;
        synchronized (clipBuffer) {
            snapshot = new ArrayList<>(clipBuffer);
        }
        LOGGER.info("[ClipTV] Saving clip — {} frames", snapshot.size());
        return CompletableFuture.supplyAsync(() -> encode(snapshot, "clip"), ENCODER);
    }

    public static State getState() { return state; }

    // ── Internal helpers ────────────────────────────────────────────────────

    private static synchronized void addToClipBuffer(byte[] jpeg) {
        clipBuffer.addLast(jpeg);
        if (clipBuffer.size() > ClipTVConstants.MAX_BUFFER_FRAMES) {
            clipBuffer.pollFirst();
        }
    }

    /** Encode a list of JPEG frames to an MJPEG AVI file in the ClipTV drafts folder. */
    private static Path encode(List<byte[]> frames, String prefix) {
        if (frames.isEmpty()) {
            LOGGER.warn("[ClipTV] No frames to encode");
            return null;
        }
        try {
            Path dir = draftsDir();
            String name = prefix + "_" + LocalDateTime.now().format(TS) + ".avi";
            Path out = dir.resolve(name);
            MjpegWriter.write(frames, ClipTVConstants.CLIP_FPS, out);
            LOGGER.info("[ClipTV] Saved: {}", out);
            return out;
        } catch (Exception e) {
            LOGGER.error("[ClipTV] Encode failed", e);
            return null;
        }
    }

    private static Path draftsDir() throws IOException {
        // Save next to the Minecraft run directory
        Path dir = Path.of(System.getProperty("user.dir"), "cliptv", "drafts");
        Files.createDirectories(dir);
        return dir;
    }

    /** Convert a {@link BufferedImage} to JPEG bytes at the configured quality. */
    public static byte[] toJpeg(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(65536);
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) throw new IOException("No JPEG ImageWriter found");
        ImageWriter writer = writers.next();
        JPEGImageWriteParam param = new JPEGImageWriteParam(null);
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(ClipTVConstants.JPEG_QUALITY);
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return baos.toByteArray();
    }
}
