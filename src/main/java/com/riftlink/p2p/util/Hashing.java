package com.riftlink.p2p.util;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * A utility class for cryptographic hashing operations.
 */
public final class Hashing {

    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();

    /**
     * Private constructor to prevent instantiation.
     */
    private Hashing() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Calculates the SHA-256 hash of a byte array.
     * @param input The byte array to hash.
     * @return The SHA-256 hash as a lowercase hexadecimal string.
     */
    public static String sha256(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance(Constants.HASH_ALGORITHM);
            byte[] encodedhash = digest.digest(input);
            return bytesToHex(encodedhash);
        } catch (NoSuchAlgorithmException e) {
            // This should never happen with a standard algorithm like SHA-256
            throw new RuntimeException("Could not find SHA-256 algorithm", e);
        }
    }

    /**
     * Generates the infohash for a RiftFile object.
     * The infohash is the SHA-256 hash of the metadata file's JSON representation.
     * @param riftFile The metadata object.
     * @return The infohash as a lowercase hexadecimal string.
     */
    public static String createInfoHash(Object riftFile) {
        Gson gson = new Gson();
        String json = gson.toJson(riftFile);
        return sha256(json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Converts a byte array to its hexadecimal string representation.
     * @param bytes The byte array to convert.
     * @return The hexadecimal string.
     */
    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }
}