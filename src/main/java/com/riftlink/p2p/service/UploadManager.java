package com.riftlink.p2p.service;

import com.google.gson.Gson;
import com.riftlink.p2p.model.RiftFile;
import com.riftlink.p2p.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Manages the server-side logic for uploads.
 * It listens for incoming peer connections and serves file chunks concurrently.
 */
public class UploadManager {
    private static final Logger logger = LoggerFactory.getLogger(UploadManager.class);
    private final ExecutorService threadPool = Executors.newCachedThreadPool();
    private final SecurityService securityService;
    private final FileManager fileManager;
    private final Path sharedDirectory;
    private volatile boolean running = true;
    private SSLServerSocket serverSocket;

    public UploadManager(SecurityService securityService, FileManager fileManager, Path sharedDirectory) {
        this.securityService = securityService;
        this.fileManager = fileManager;
        this.sharedDirectory = sharedDirectory;
    }

    public void start(int port) {
        threadPool.submit(() -> {
            try {
                serverSocket = securityService.createServerSocket(port);
                logger.info("UploadManager started, listening on port: {}", port);
                while (running) {
                    SSLSocket clientSocket = (SSLSocket) serverSocket.accept();
                    logger.debug("Accepted connection from {}", clientSocket.getRemoteSocketAddress());
                    threadPool.submit(() -> handleClientRequest(clientSocket));
                }
            } catch (IOException e) {
                if(running) logger.error("Error in UploadManager server socket", e);
            }
        });
    }

    private void handleClientRequest(SSLSocket socket) {
        try (
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            OutputStream outputStream = socket.getOutputStream()
        ) {
            // Read the request type from the first line.
            String requestType = reader.readLine();

            if ("GET_RIFT".equals(requestType)) {
                // Handle metadata request
                String infohash = reader.readLine();
                handleMetadataRequest(infohash, outputStream);
            } else {
                // Handle chunk request (original logic)
                String infohash = requestType; // In the old protocol, the first line was the infohash
                String chunkIndexStr = reader.readLine();
                handleChunkRequest(infohash, chunkIndexStr, outputStream);
            }

        } catch (Exception e) {
            logger.error("Error handling client request from " + socket.getRemoteSocketAddress(), e);
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                logger.error("Error closing client socket", e);
            }
        }
    }

    private void handleMetadataRequest(String infohash, OutputStream outputStream) throws IOException {
        logger.debug("Received metadata request for infohash {}", infohash);
        Path riftFilePath = sharedDirectory.resolve(infohash + Constants.METADATA_EXTENSION);
        if (Files.exists(riftFilePath)) {
            String json = Files.readString(riftFilePath);
            PrintWriter writer = new PrintWriter(outputStream);
            writer.print(json); // Use print to avoid extra newline
            writer.flush();
        } else {
            logger.error("Requested .rift file not found for infohash: {}", infohash);
        }
    }

    private void handleChunkRequest(String infohash, String chunkIndexStr, OutputStream outputStream) throws Exception {
        if (infohash == null || chunkIndexStr == null) {
            logger.warn("Invalid chunk request from peer");
            return;
        }
        int chunkIndex = Integer.parseInt(chunkIndexStr);
        logger.debug("Received chunk request for infohash {} chunk {}", infohash, chunkIndex);

        Path riftFilePath = sharedDirectory.resolve(infohash + Constants.METADATA_EXTENSION);
        if (!Files.exists(riftFilePath)) {
            logger.error("Requested .rift file not found for infohash: {}", infohash);
            return;
        }
        String json = Files.readString(riftFilePath);
        RiftFile riftFile = new Gson().fromJson(json, RiftFile.class);

        byte[] chunkData = fileManager.getChunk(riftFile, chunkIndex);
        outputStream.write(chunkData);
        outputStream.flush();
        logger.debug("Sent chunk {} for infohash {}", chunkIndex, infohash);
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
        } catch (IOException e) {
            logger.error("Error closing server socket", e);
        }
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) threadPool.shutdownNow();
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
        }
        logger.info("UploadManager stopped.");
    }
}