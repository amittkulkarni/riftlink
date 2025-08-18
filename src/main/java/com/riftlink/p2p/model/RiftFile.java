package com.riftlink.p2p.model;

import java.io.Serializable;
import java.util.List;

/**
 * Represents the metadata of a shared file.
 * This object is typically serialized to JSON to create a .rift file.
 *
 * @param filename The original name of the file.
 * @param totalSize The total size of the file in bytes.
 * @param chunkSize The size of each chunk in bytes.
 * @param chunkHashes A list of SHA-256 hashes, one for each chunk, in order.
 */
public record RiftFile(
    String filename,
    long totalSize,
    int chunkSize,
    List<String> chunkHashes
) implements Serializable {
    /**
     * Calculates the total number of chunks for the file.
     * @return The number of chunks.
     */
    public int getNumberOfChunks() {
        return chunkHashes.size();
    }
}