package com.riftlink.p2p.service;

import com.google.gson.Gson;
import com.riftlink.p2p.model.RiftFile;
import com.riftlink.p2p.util.Constants;
import com.riftlink.p2p.util.Hashing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Manages all file system interactions: creating .rift files, reading/writing chunks,
 * and reassembling completed files.
 */
public class FileManager {

    private static final Logger logger = LoggerFactory.getLogger(FileManager.class);
    private final Path sharedDirectory;
    private final Path downloadsDirectory;

    /**
     * Constructor for FileManager.
     * @param sharedDirectory The directory where original shared files are located.
     * @param downloadsDirectory The directory to save downloaded files and chunks.
     */
    public FileManager(Path sharedDirectory, Path downloadsDirectory) {
        this.sharedDirectory = sharedDirectory;
        this.downloadsDirectory = downloadsDirectory;

        try {
            Files.createDirectories(sharedDirectory);
            Files.createDirectories(downloadsDirectory);
        } catch (IOException e) {
            logger.error("Could not create necessary directories", e);
            throw new RuntimeException("Failed to initialize directories", e);
        }
    }

    /**
     * Analyzes a file, splits it into chunks, hashes them, and creates a metadata object.
     * It also saves the .rift file to the shared directory.
     * @param fileToShare The file to process for sharing.
     * @return A RiftFile metadata object.
     * @throws IOException if there is an error reading the file.
     */
    public RiftFile createRiftFile(File fileToShare) throws IOException {
        logger.info("Creating .rift file for: {}", fileToShare.getName());
        List<String> chunkHashes = new ArrayList<>();

        try (RandomAccessFile raf = new RandomAccessFile(fileToShare, "r")) {
            byte[] buffer = new byte[Constants.CHUNK_SIZE_BYTES];
            long bytesRemaining = raf.length();
            
            while (bytesRemaining > 0) {
                int bytesToRead = (int) Math.min(buffer.length, bytesRemaining);
                raf.readFully(buffer, 0, bytesToRead);
                
                byte[] chunkData;
                if (bytesToRead < buffer.length) {
                    chunkData = new byte[bytesToRead];
                    System.arraycopy(buffer, 0, chunkData, 0, bytesToRead);
                } else {
                    chunkData = buffer;
                }
                
                chunkHashes.add(Hashing.sha256(chunkData));
                bytesRemaining -= bytesToRead;
            }
        }

        RiftFile riftFile = new RiftFile(
            fileToShare.getName(),
            fileToShare.length(),
            Constants.CHUNK_SIZE_BYTES,
            chunkHashes
        );

        // Save the .rift file to disk
        saveRiftFile(riftFile);

        logger.info("Successfully created .rift file for {}", riftFile.filename());
        return riftFile;
    }

    /**
     * Reads a specific chunk from a shared file.
     * @param riftFile The metadata of the file.
     * @param chunkIndex The index of the chunk to read.
     * @return The byte array of the chunk data.
     * @throws IOException if there is an error reading the chunk.
     */
    public byte[] getChunk(RiftFile riftFile, int chunkIndex) throws IOException {
        Path filePath = sharedDirectory.resolve(riftFile.filename());
        if (!Files.exists(filePath)) {
            throw new IOException("Original file not found in shared directory: " + riftFile.filename());
        }

        try (RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "r")) {
            long startPosition = (long) chunkIndex * Constants.CHUNK_SIZE_BYTES;
            raf.seek(startPosition);

            long fileSize = raf.length();
            int bytesToRead = (int) Math.min(Constants.CHUNK_SIZE_BYTES, fileSize - startPosition);
            
            byte[] chunkData = new byte[bytesToRead];
            raf.readFully(chunkData);
            return chunkData;
        }
    }

    /**
     * Reassembles a file from its downloaded chunks.
     * @param riftFile The metadata for the file being assembled.
     * @param infohash The infohash of the file, used to find the chunk directory.
     * @throws IOException if there is an error during file writing or chunk reading.
     */
    public void reassembleFile(RiftFile riftFile, String infohash) throws IOException {
        Path chunkDir = downloadsDirectory.resolve(infohash);
        Path finalFile = downloadsDirectory.resolve(riftFile.filename());
        logger.info("Reassembling file: {}", finalFile);

        try (FileOutputStream fos = new FileOutputStream(finalFile.toFile())) {
            for (int i = 0; i < riftFile.getNumberOfChunks(); i++) {
                Path chunkPath = chunkDir.resolve("chunk_" + i);
                if (!Files.exists(chunkPath)) {
                    throw new IOException("Missing chunk " + i + " for file " + riftFile.filename());
                }
                byte[] chunkData = Files.readAllBytes(chunkPath);
                fos.write(chunkData);
            }
        }
        logger.info("File reassembled successfully: {}", finalFile);

        // Optional: Clean up chunk directory after reassembly
        // ...
    }

    /**
     * Finds and loads a RiftFile from the shared directory based on its filename.
     * @param filename The original filename to search for.
     * @return An Optional containing the RiftFile if found.
     */
    public Optional<RiftFile> getSharedRiftFile(String filename) {
        try (Stream<Path> paths = Files.walk(sharedDirectory)) {
            return paths
                .filter(path -> path.toString().endsWith(Constants.METADATA_EXTENSION))
                .map(this::loadRiftFileFromPath)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(riftFile -> riftFile.filename().equals(filename))
                .findFirst();
        } catch (IOException e) {
            logger.error("Could not read shared directory to find .rift file", e);
            return Optional.empty();
        }
    }

    /**
     * Removes a shared file and its corresponding .rift metadata file.
     * @param filename The filename of the file to remove.
     */
    public void removeSharedFile(String filename) {
        getSharedRiftFile(filename).ifPresent(riftFile -> {
            try {
                String infohash = Hashing.createInfoHash(riftFile);
                Path originalFilePath = sharedDirectory.resolve(filename);
                Path riftFilePath = sharedDirectory.resolve(infohash + Constants.METADATA_EXTENSION);

                Files.deleteIfExists(originalFilePath);
                Files.deleteIfExists(riftFilePath);

                logger.info("Successfully removed shared file and its metadata: {}", filename);
            } catch (IOException e) {
                logger.error("Failed to delete files for: {}", filename, e);
            }
        });
    }

    /**
     * Gets a list of all shared file names by reading the .rift files.
     * @return A list of filenames.
     */
    public List<String> getSharedFileNames() {
        try (Stream<Path> paths = Files.walk(sharedDirectory)) {
            return paths
                .filter(path -> path.toString().endsWith(Constants.METADATA_EXTENSION))
                .map(this::loadRiftFileFromPath)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(RiftFile::filename)
                .collect(Collectors.toList());
        } catch (IOException e) {
            logger.error("Failed to read shared directory for file list", e);
            return new ArrayList<>();
        }
    }

    /**
     * Saves a RiftFile metadata object as a JSON file in the shared directory.
     * @param riftFile The metadata to save.
     */
    private void saveRiftFile(RiftFile riftFile) throws IOException {
        String infohash = Hashing.createInfoHash(riftFile);
        Path riftFilePath = sharedDirectory.resolve(infohash + Constants.METADATA_EXTENSION);
        
        Gson gson = new Gson();
        String json = gson.toJson(riftFile);
        
        Files.writeString(riftFilePath, json, StandardCharsets.UTF_8);
    }

    private Optional<RiftFile> loadRiftFileFromPath(Path riftFilePath) {
        try {
            String json = Files.readString(riftFilePath, StandardCharsets.UTF_8);
            return Optional.of(new Gson().fromJson(json, RiftFile.class));
        } catch (IOException e) {
            logger.error("Failed to read or parse .rift file: {}", riftFilePath, e);
            return Optional.empty();
        }
    }

    public Path getSharedDirectory() {
        return sharedDirectory;
    }
}