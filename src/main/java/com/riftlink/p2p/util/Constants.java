package com.riftlink.p2p.util;

/**
 * A central place for application-wide constants.
 */
public final class Constants {

    /**
     * The size of each file chunk in bytes.
     * Default is 1MB (1 * 1024 * 1024 bytes).
     */
    public static final int CHUNK_SIZE_BYTES = 1 * 1024 * 1024;

    /**
     * The default port for the application to listen on for P2P connections.
     */
    public static final int DEFAULT_P2P_PORT = 4001;

    /**
     * The file extension for RiftLink metadata files.
     */
    public static final String METADATA_EXTENSION = ".rift";

    /**
     * The hashing algorithm used for file chunks and infohashes.
     */
    public static final String HASH_ALGORITHM = "SHA-256";
    
    /**
     * Private constructor to prevent instantiation.
     */
    private Constants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}