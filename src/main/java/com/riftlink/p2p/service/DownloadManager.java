package com.riftlink.p2p.service;

import com.riftlink.p2p.model.RiftFile;
import com.riftlink.p2p.ui.model.DownloadItem;
import com.riftlink.p2p.util.Hashing;
import com.riftlink.p2p.util.Constants;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class DownloadManager {
    private static final Logger logger = LoggerFactory.getLogger(DownloadManager.class);
    private final ExecutorService downloadExecutor = Executors.newFixedThreadPool(10);
    private final P2PService p2pService;
    private final SecurityService securityService;
    private final FileManager fileManager;
    private final Path downloadsDirectory;

    private final ConcurrentMap<String, DownloadTask> activeDownloads = new ConcurrentHashMap<>();

    /**
     * Inner class to hold the state and future of an active download.
     */
    private static class DownloadTask {
        // 'volatile' ensures that changes to this variable are visible across threads.
        volatile CompletableFuture<Void> mainFuture;
        final AtomicBoolean paused = new AtomicBoolean(false);
        final AtomicBoolean cancelled = new AtomicBoolean(false);

        // Constructor is now empty.
        DownloadTask() {}

        // Setter to associate the future after creation.
        void setMainFuture(CompletableFuture<Void> mainFuture) {
            this.mainFuture = mainFuture;
        }

        void cancel() {
            cancelled.set(true);
            if (mainFuture != null) {
                mainFuture.cancel(true);
            }
        }

        void pause() {
            paused.set(true);
        }

        void resume() {
            paused.set(false);
        }
    }

    public DownloadManager(P2PService p2pService, SecurityService securityService, FileManager fileManager, Path downloadsDirectory) {
        this.p2pService = p2pService;
        this.securityService = securityService;
        this.fileManager = fileManager;
        this.downloadsDirectory = downloadsDirectory;
    }

    public void startDownload(RiftFile riftFile, String infohash, DownloadItem downloadItem) {
        // 1. Create the task and add it to the map immediately.
        DownloadTask task = new DownloadTask();
        activeDownloads.put(infohash, task);

        // 2. Define the main future (the download logic).
        CompletableFuture<Void> mainFuture = CompletableFuture.runAsync(() -> {
            try {
                if (task.cancelled.get()) return;
                downloadItem.setStatus("Finding peers...");

                Collection<PeerAddress> peers = p2pService.findPeers(infohash).get();
                if (peers.isEmpty()) throw new RuntimeException("No peers found");
                List<PeerAddress> peerList = new ArrayList<>(peers);

                Path chunkDir = downloadsDirectory.resolve(infohash);
                Files.createDirectories(chunkDir);

                downloadItem.setStatus("Downloading...");
                int totalChunks = riftFile.getNumberOfChunks();
                AtomicInteger completedChunks = new AtomicInteger(0);

                List<CompletableFuture<Void>> chunkFutures = new ArrayList<>();
                for (int i = 0; i < totalChunks; i++) {
                    if (task.cancelled.get()) break;

                    while (task.paused.get()) {
                        Thread.sleep(500); // Wait while paused
                        if (task.cancelled.get()) return;
                    }

                    final int chunkIndex = i;
                    CompletableFuture<Void> future = downloadChunk(peerList, infohash, riftFile, chunkIndex, chunkDir)
                        .thenRun(() -> {
                            int completed = completedChunks.incrementAndGet();
                            downloadItem.setProgress((double) completed / totalChunks);
                        });
                    chunkFutures.add(future);
                }

                CompletableFuture.allOf(chunkFutures.toArray(new CompletableFuture[0])).get();

                if (task.cancelled.get()) {
                    downloadItem.setStatus("Cancelled");
                    return;
                }

                downloadItem.setStatus("Reassembling...");
                fileManager.reassembleFile(riftFile, infohash);
                downloadItem.setStatus("Completed");
                downloadItem.setProgress(1.0);

            } catch (Exception e) {
                if (e instanceof InterruptedException || e instanceof CancellationException) {
                    Thread.currentThread().interrupt();
                    downloadItem.setStatus("Cancelled");
                } else {
                    logger.error("Download failed for infohash: " + infohash, e);
                    downloadItem.setStatus("Error: " + e.getMessage());
                }
            } finally {
                activeDownloads.remove(infohash);
            }
        });

        // 3. Now that mainFuture exists, link it to the task.
        task.setMainFuture(mainFuture);
    }

    public void pauseDownload(String infohash) {
        DownloadTask task = activeDownloads.get(infohash);
        if (task != null) {
            task.pause();
            logger.info("Download paused for infohash: {}", infohash);
        }
    }

    public void resumeDownload(String infohash) {
        DownloadTask task = activeDownloads.get(infohash);
        if (task != null) {
            task.resume();
            logger.info("Download resumed for infohash: {}", infohash);
        }
    }

    public void cancelDownload(String infohash) {
        DownloadTask task = activeDownloads.get(infohash);
        if (task != null) {
            task.cancel();
            logger.info("Download cancelled for infohash: {}", infohash);
        }
    }

    private CompletableFuture<Void> downloadChunk(List<PeerAddress> peers, String infohash, RiftFile riftFile, int chunkIndex, Path chunkDir) {
        return CompletableFuture.runAsync(() -> {
            for (PeerAddress peer : peers) {
                try (SSLSocket socket = securityService.createSocket(peer.inetAddress().getHostAddress(), Constants.UPLOAD_PORT);
                     PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                     InputStream inputStream = socket.getInputStream()) {
                    
                    writer.println(infohash);
                    writer.println(chunkIndex);

                    byte[] chunkData = inputStream.readAllBytes();
                    
                    String expectedHash = riftFile.chunkHashes().get(chunkIndex);
                    String actualHash = Hashing.sha256(chunkData);

                    if (!expectedHash.equals(actualHash)) {
                        throw new IOException("Chunk hash mismatch for index " + chunkIndex);
                    }

                    Path chunkPath = chunkDir.resolve("chunk_" + chunkIndex);
                    Files.write(chunkPath, chunkData);
                    return;
                } catch (Exception e) {
                    logger.warn("Failed to download chunk {} from peer {}. Reason: {}", chunkIndex, peer, e.getMessage());
                }
            }
            throw new RuntimeException("Could not download chunk " + chunkIndex + " from any peer.");
        }, downloadExecutor);
    }
    
    public void shutdown() {
        downloadExecutor.shutdownNow();
    }
}