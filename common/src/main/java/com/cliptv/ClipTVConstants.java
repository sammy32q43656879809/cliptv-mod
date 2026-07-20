package com.cliptv;

public final class ClipTVConstants {
    public static final String MOD_ID      = "cliptv";
    public static final String MOD_VERSION = "1.0.0";

    /** Plugin-messaging channel used for server handshake and commands. */
    public static final String CHANNEL = "clapcraft:cliptv";

    /** Clip buffer duration (seconds). */
    public static final int CLIP_BUFFER_SECONDS = 120;

    /** Capture rate for the clip buffer (frames per second). */
    public static final int CLIP_FPS = 5;

    /** JPEG compression quality (0.0 – 1.0). */
    public static final float JPEG_QUALITY = 0.70f;

    /** Maximum buffered frames = CLIP_BUFFER_SECONDS × CLIP_FPS. */
    public static final int MAX_BUFFER_FRAMES = CLIP_BUFFER_SECONDS * CLIP_FPS;

    private ClipTVConstants() {}
}
