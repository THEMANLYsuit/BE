package com.themanly.media;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;


public enum ImageFormat {
    PNG("image/png", ".png"),
    JPEG("image/jpeg", ".jpg"),
    WEBP("image/webp", ".webp"),
    GIF("image/gif", ".gif"),
    AVIF("image/avif", ".avif");
    private static final byte[] PNG_SIG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_SIG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private final String contentType;
    private final String extension;
    ImageFormat(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }
    public String contentType() {
        return contentType;
    }
    public String extension() {
        return extension;
    }

    public static ImageFormat sniff(byte[] head) {
        if (head == null) {
            return null;
        }
        if (startsWith(head, PNG_SIG)) {
            return PNG;
        }
        if (startsWith(head, JPEG_SIG)) {
            return JPEG;
        }
        if (head.length >= 12 && startsWith(head, new byte[] {'R', 'I', 'F', 'F'})
                && Arrays.equals(head, 8, 12, new byte[] {'W', 'E', 'B', 'P'}, 0, 4)) {
            return WEBP;
        }
        if (startsWith(head, "GIF87a".getBytes(StandardCharsets.US_ASCII))
                || startsWith(head, "GIF89a".getBytes(StandardCharsets.US_ASCII))) {
            return GIF;
        }
        return avif(head) ? AVIF : null;
    }
    private static boolean avif(byte[] head) {
        if (head.length < 16 || !Arrays.equals(head, 4, 8, new byte[] {'f', 't', 'y', 'p'}, 0, 4)) {
            return false;
        }
        String major = new String(head, 8, 4, StandardCharsets.US_ASCII);
        if (major.equals("avif") || major.equals("avis")) {
            return true;
        }
        if (!major.equals("mif1") && !major.equals("msf1")) {
            return false;
        }
        int boxSize = (head[0] & 0xff) << 24 | (head[1] & 0xff) << 16 | (head[2] & 0xff) << 8 | (head[3] & 0xff);
        int end = Math.min(Math.max(boxSize, 16), head.length);
        for (int i = 16; i + 4 <= end; i += 4) {
            String brand = new String(head, i, 4, StandardCharsets.US_ASCII);
            if (brand.equals("avif") || brand.equals("avis")) {
                return true;
            }
        }
        return false;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        return data.length >= prefix.length && Arrays.equals(data, 0, prefix.length, prefix, 0, prefix.length);
    }
}