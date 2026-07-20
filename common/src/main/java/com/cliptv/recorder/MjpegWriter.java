package com.cliptv.recorder;

import java.io.*;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * Pure-Java MJPEG AVI 1.0 writer.
 *
 * Uses {@link RandomAccessFile} so size fields can be back-patched
 * without buffering every frame in memory.
 *
 * Output is compatible with VLC, Windows Media Player, FFmpeg, and
 * most Discord-supported video players.
 */
public final class MjpegWriter {

    private MjpegWriter() {}

    public static void write(List<byte[]> jpegFrames, int fps, Path output) throws IOException {
        if (jpegFrames.isEmpty()) return;

        int totalFrames = jpegFrames.size();
        // Detect width/height from first JPEG JFIF/SOFn marker (or use defaults)
        int[] wh = jpegDimensions(jpegFrames.get(0));
        int w = wh[0], h = wh[1];

        // frame-offset table for idx1
        int[] frameOffset = new int[totalFrames];
        int[] frameSize   = new int[totalFrames];

        try (RandomAccessFile raf = new RandomAccessFile(output.toFile(), "rw")) {
            raf.setLength(0);

            // ── RIFF header ────────────────────────────────────────────────
            writeFourCC(raf, "RIFF");
            long riffSizeOff = raf.getFilePointer(); writeU32(raf, 0);
            writeFourCC(raf, "AVI ");

            // ── LIST hdrl ──────────────────────────────────────────────────
            writeFourCC(raf, "LIST");
            long hdrlSizeOff = raf.getFilePointer(); writeU32(raf, 0);
            writeFourCC(raf, "hdrl");

            // avih — AVI main header (56 bytes body)
            writeFourCC(raf, "avih");
            writeU32(raf, 56);
            writeU32(raf, 1_000_000 / fps); // MicroSecPerFrame
            writeU32(raf, 0);               // MaxBytesPerSec
            writeU32(raf, 0);               // PaddingGranularity
            writeU32(raf, 0x10);            // Flags = AVIF_HASINDEX
            writeU32(raf, totalFrames);
            writeU32(raf, 0);               // InitialFrames
            writeU32(raf, 1);               // Streams
            writeU32(raf, 0);               // SuggestedBufferSize
            writeU32(raf, w);
            writeU32(raf, h);
            writeU32(raf, 0); writeU32(raf, 0); writeU32(raf, 0); writeU32(raf, 0); // Reserved

            // LIST strl
            writeFourCC(raf, "LIST");
            long strlSizeOff = raf.getFilePointer(); writeU32(raf, 0);
            writeFourCC(raf, "strl");

            // strh — stream header (56 bytes body)
            writeFourCC(raf, "strh");
            writeU32(raf, 56);
            writeFourCC(raf, "vids"); // fccType
            writeFourCC(raf, "MJPG"); // fccHandler
            writeU32(raf, 0);         // Flags
            writeU16(raf, 0);         // Priority
            writeU16(raf, 0);         // Language
            writeU32(raf, 0);         // InitialFrames
            writeU32(raf, 1);         // Scale
            writeU32(raf, fps);       // Rate
            writeU32(raf, 0);         // Start
            writeU32(raf, totalFrames);
            writeU32(raf, 0);         // SuggestedBufferSize
            writeU32(raf, 0xFFFFFFFFL); // Quality = -1
            writeU32(raf, 0);         // SampleSize
            writeU16(raf, 0); writeU16(raf, 0); writeU16(raf, w); writeU16(raf, h);

            // strf — BITMAPINFOHEADER (40 bytes body)
            writeFourCC(raf, "strf");
            writeU32(raf, 40);
            writeU32(raf, 40);        // biSize
            writeI32(raf, w);         // biWidth
            writeI32(raf, h);         // biHeight (positive = bottom-up)
            writeU16(raf, 1);         // biPlanes
            writeU16(raf, 24);        // biBitCount
            writeFourCC(raf, "MJPG"); // biCompression
            writeU32(raf, w * h * 3); // biSizeImage
            writeI32(raf, 0);         // biXPelsPerMeter
            writeI32(raf, 0);         // biYPelsPerMeter
            writeU32(raf, 0);         // biClrUsed
            writeU32(raf, 0);         // biClrImportant

            patchSize(raf, strlSizeOff);
            patchSize(raf, hdrlSizeOff);

            // ── LIST movi ─────────────────────────────────────────────────
            writeFourCC(raf, "LIST");
            long moviSizeOff = raf.getFilePointer(); writeU32(raf, 0);
            long moviStart   = raf.getFilePointer();
            writeFourCC(raf, "movi");

            for (int i = 0; i < totalFrames; i++) {
                byte[] jpeg = jpegFrames.get(i);
                frameOffset[i] = (int) (raf.getFilePointer() - moviStart);
                frameSize[i]   = jpeg.length;

                writeFourCC(raf, "00dc");
                writeU32(raf, jpeg.length);
                raf.write(jpeg);
                if ((jpeg.length & 1) != 0) raf.write(0); // word-align
            }

            patchSize(raf, moviSizeOff);

            // ── idx1 ──────────────────────────────────────────────────────
            writeFourCC(raf, "idx1");
            writeU32(raf, (long) 16 * totalFrames);
            for (int i = 0; i < totalFrames; i++) {
                writeFourCC(raf, "00dc");
                writeU32(raf, 0x10);           // AVIIF_KEYFRAME
                writeU32(raf, frameOffset[i]);
                writeU32(raf, frameSize[i]);
            }

            patchSize(raf, riffSizeOff);
        }
    }

    // ── Back-patching ────────────────────────────────────────────────────────

    /** Seek back to {@code sizeFieldOffset} and write (currentPos - sizeFieldOffset - 4). */
    private static void patchSize(RandomAccessFile raf, long sizeFieldOffset) throws IOException {
        long currentPos = raf.getFilePointer();
        int size = (int) (currentPos - sizeFieldOffset - 4);
        raf.seek(sizeFieldOffset);
        writeU32(raf, size);
        raf.seek(currentPos);
    }

    // ── Little-endian I/O ────────────────────────────────────────────────────

    private static void writeFourCC(RandomAccessFile raf, String s) throws IOException {
        byte[] b = s.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        raf.write(b, 0, Math.min(b.length, 4));
        for (int i = b.length; i < 4; i++) raf.write(' ');
    }

    private static void writeU32(RandomAccessFile raf, long v) throws IOException {
        raf.write((int)  v        & 0xFF);
        raf.write((int) (v >>  8) & 0xFF);
        raf.write((int) (v >> 16) & 0xFF);
        raf.write((int) (v >> 24) & 0xFF);
    }

    private static void writeU32(RandomAccessFile raf, int v) throws IOException { writeU32(raf, (long) v & 0xFFFFFFFFL); }
    private static void writeI32(RandomAccessFile raf, int v) throws IOException { writeU32(raf, (long) v & 0xFFFFFFFFL); }

    private static void writeU16(RandomAccessFile raf, int v) throws IOException {
        raf.write(v & 0xFF);
        raf.write((v >> 8) & 0xFF);
    }

    // ── JPEG dimension extraction ────────────────────────────────────────────

    /**
     * Parses width and height from a JPEG byte array by scanning for SOF markers.
     * Returns {1280, 720} if parsing fails.
     */
    static int[] jpegDimensions(byte[] jpeg) {
        try {
            int i = 2; // skip FF D8
            while (i < jpeg.length - 8) {
                if (jpeg[i] != (byte) 0xFF) break;
                int marker = jpeg[i + 1] & 0xFF;
                int len    = ((jpeg[i + 2] & 0xFF) << 8) | (jpeg[i + 3] & 0xFF);
                // SOF0=0xC0 … SOF3=0xC3, SOF5=0xC5 … SOF7=0xC7, SOF9=0xC9 … SOF11=0xCB
                if ((marker >= 0xC0 && marker <= 0xC3) ||
                    (marker >= 0xC5 && marker <= 0xC7) ||
                    (marker >= 0xC9 && marker <= 0xCB)) {
                    int h = ((jpeg[i + 5] & 0xFF) << 8) | (jpeg[i + 6] & 0xFF);
                    int w = ((jpeg[i + 7] & 0xFF) << 8) | (jpeg[i + 8] & 0xFF);
                    return new int[]{w, h};
                }
                i += 2 + len;
            }
        } catch (Exception ignored) {}
        return new int[]{1280, 720};
    }
}
