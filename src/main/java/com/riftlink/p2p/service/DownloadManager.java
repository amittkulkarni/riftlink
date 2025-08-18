package com.riftlink.p2p.service;

import com.google.gson.Gson;
import com.riftlink.p2p.model.RiftFile;
import com.riftlink.p2p.ui.model.DownloadItem;
import com.riftlink.p2p.util.Hashing;
import net.tomp2p.peers.PeerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLSocket;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages the client-side logic for downloads.
 * It coordinates finding peers, downloading chunks in parallel, verifying hashes,
 * and reassembling the final file.
 */
public class DownloadManager {
    private static final Logger logger = LoggerFactory.getLogger(DownloadManager.class);
    private final ExecutorService downloadExecutor = Executors.newFixedThreadPool(10); // Limits concurrent chunk downloads
    private final P2PService p2pService;
    private final SecurityService securityService;
    private final FileManager fileManager;
    private final Path downloadsDirectory;

    public DownloadManager(P2PService p2pService, SecurityService securityService, FileManager fileManager, Path downloadsDirectory) {
        this.p2pService = p2pService;
        this.securityService = securityService;
        this.fileManager = fileManager;
        this.downloadsDirectory = downloadsDirectory;
    }

    /**
     * Starts the download process for a file.
     * @param riftFile The metadata of the file to download.
     * @param infohash The unique identifier (hash) of the file's metadata.
     * @param downloadItem The UI model item to update with progress.
     */
    public void startDownload(RiftFile riftFile, String infohash, DownloadItem downloadItem) {
        CompletableFuture.runAsync(() -> {
            try {
                // 1. Find peers
                downloadItem.setStatus("Finding peers...");
                Collection<PeerAddress> peers = p2pService.findPeers(infohash).get();
                if (peers.isEmpty()) {
                    logger.error("No peers found for infohash: {}", infohash);
                    downloadItem.setStatus("Error: No peers found");
                    return;
                }
                List<PeerAddress> peerList = new ArrayList<>(peers);

                // 2. Prepare download directory
                Path chunkDir = downloadsDirectory.resolve(infohash);
                Files.createDirectories(chunkDir);

                // 3. Download all chunks in parallel
                downloadItem.setStatus("Downloading...");
                int totalChunks = riftFile.getNumberOfChunks();
                AtomicInteger completedChunks = new AtomicInteger(0);

                List<CompletableFuture<Void>> chunkFutures = new ArrayList<>();
                for (int i = 0; i < totalChunks; i++) {
                    final int chunkIndex = i;
                    CompletableFuture<Void> future = downloadChunk(peerList, infohash, riftFile, chunkIndex, chunkDir)
                        .thenRun(() -> {
                            int completed = completedChunks.incrementAndGet();
                            double progress = (double) completed / totalChunks;
                            downloadItem.setProgress(progress);
                        });
                    chunkFutures.add(future);
                }

                // 4. Wait for all chunks to complete
                CompletableFuture.allOf(chunkFutures.toArray(new CompletableFuture[0])).get();

                // 5. Reassemble the file
                downloadItem.setStatus("Reassembling...");
                fileManager.reassembleFile(riftFile, infohash);
                downloadItem.setStatus("Completed");
                downloadItem.setProgress(1.0);

            } catch (Exception e) {
                logger.error("Download failed for infohash: " + infohash, e);
                downloadItem.setStatus("Error: " + e.getMessage());
            }
        });
    }
    
    /**
     * Downloads a single chunk from an available peer. It will retry with different peers if one fails.
     */
    private CompletableFuture<Void> downloadChunk(List<PeerAddress> peers, String infohash, RiftFile riftFile, int chunkIndex, Path chunkDir) {
        return CompletableFuture.runAsync(() -> {
            // Simple strategy: try peers one by one until success.
            for (PeerAddress peer : peers) {
                try (
                    SSLSocket socket = securityService.createSocket(peer.inetAddress().getHostAddress(), peer.tcpPort());
                    PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                    InputStream inputStream = socket.getInputStream()
                ) {
                    // Protocol: Send infohash, then chunk index, each on a new line
                    writer.println(infohash);
                    writer.println(chunkIndex);

                    byte[] chunkData = inputStream.readAllBytes();
                    
                    // Verify hash
                    String expectedHash = riftFile.chunkHashes().get(chunkIndex);
                    String actualHash = Hashing.sha256(chunkData);

                    if (!expectedHash.equals(actualHash)) {
                        throw new IOException("Chunk hash mismatch for index " + chunkIndex);
                    }

                    // Save chunk to disk
                    Path chunkPath = chunkDir.resolve("chunk_" + chunkIndex);
                    Files.write(chunkPath, chunkData);
                    logger.info("Successfully downloaded and verified chunk {}", chunkIndex);
                    return; // Success, exit loop
                } catch (Exception e) {
                    logger.warn("Failed to download chunk {} from peer {}. Trying next peer. Reason: {}", chunkIndex, peer, e.getMessage());
                }
            }
            // If the loop completes without returning, all peers failed for this chunk.
            throw new RuntimeException("Could not download chunk " + chunkIndex + " from any available peer.");
        }, downloadExecutor);
    }
    
    /**
     * Shuts down the download manager's thread pool.
     */
     public void shutdown() {
        downloadExecutor.shutdown();
     }
}