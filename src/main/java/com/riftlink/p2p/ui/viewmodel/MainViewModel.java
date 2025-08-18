package com.riftlink.p2p.ui.viewmodel;

import com.google.gson.Gson;
import com.riftlink.p2p.model.RiftFile;
import com.riftlink.p2p.service.DownloadManager;
import com.riftlink.p2p.service.FileManager;
import com.riftlink.p2p.service.P2PService;
import com.riftlink.p2p.service.SecurityService;
import com.riftlink.p2p.ui.model.DownloadItem;
import com.riftlink.p2p.ui.model.SearchResult;
import com.riftlink.p2p.util.Hashing;
import com.riftlink.p2p.util.Constants;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import net.tomp2p.peers.PeerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLSocket;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;

/**
 * The ViewModel for the main application window.
 * It holds the UI state and orchestrates actions by calling backend services.
 */
public class MainViewModel {
    private static final Logger logger = LoggerFactory.getLogger(MainViewModel.class);

    // Services
    private final P2PService p2pService;
    private final FileManager fileManager;
    private final DownloadManager downloadManager;
    private final SecurityService securityService;

    // UI State
    private final ObservableList<DownloadItem> downloadItems = FXCollections.observableArrayList();
    private final ObservableList<String> libraryItems = FXCollections.observableArrayList();
    private final ObservableList<SearchResult> searchResults = FXCollections.observableArrayList();

    public MainViewModel(P2PService p2pService, FileManager fileManager, DownloadManager downloadManager, SecurityService securityService) {
        this.p2pService = p2pService;
        this.fileManager = fileManager;
        this.downloadManager = downloadManager;
        this.securityService = securityService;
    }

    // --- Getters for UI State ---
    public ObservableList<DownloadItem> getDownloadItems() { return downloadItems; }
    public ObservableList<String> getLibraryItems() { return libraryItems; }
    public ObservableList<SearchResult> getSearchResults() { return searchResults; }


    /**
     * Processes a file for sharing, creates metadata, and announces it to the DHT.
     * @param file The file to be shared.
     */
    public void shareFile(File file) {
        CompletableFuture.runAsync(() -> {
            try {
                // Get the shared directory from FileManager
                Path sharedDirectory = fileManager.getSharedDirectory();
                Path targetPath = sharedDirectory.resolve(file.getName());
                
                // Copy the selected file to shared directory if not already there
                if (!Files.exists(targetPath)) {
                    Files.copy(file.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
                    logger.info("Copied file to shared directory: {} -> {}", 
                            file.getAbsolutePath(), targetPath.toAbsolutePath());
                } else {
                    logger.info("File already exists in shared directory: {}", targetPath);
                }
                
                // Create RiftFile metadata from the file in shared directory
                RiftFile riftFile = fileManager.createRiftFile(targetPath.toFile());
                String infohash = Hashing.createInfoHash(riftFile);
                
                // Announce to DHT
                p2pService.announceFile(infohash).join();
                
                Platform.runLater(() -> libraryItems.add(file.getName()));
                logger.info("Successfully shared file {} with infohash {}", file.getName(), infohash);
                
            } catch (IOException e) {
                logger.error("Could not share file " + file.getName(), e);
                // Show error to user in UI
            }
        });
    }


    /**
     * Initiates a search on the DHT for a given query.
     * For now, we assume the query is the infohash itself for simplicity.
     * A real implementation would use a more complex indexing/search mechanism.
     * @param query The infohash to search for.
     */
    public void search(String query) {
        // This is a simplified search. We will search for peers, then fetch the
        // metadata from the first peer to display file details.
        String infohash = query.trim();
        if (infohash.isEmpty()) return;

        searchResults.clear();

        p2pService.findPeers(infohash).thenAccept(peers -> {
            if (peers == null || peers.isEmpty()) {
                logger.warn("No peers found for infohash: {}", infohash);
                return;
            }
            // Fetch metadata from the first peer to get filename and size
            fetchRiftFileFromPeer(infohash, peers.iterator().next()).thenAccept(riftFile -> {
                if (riftFile != null) {
                    SearchResult result = new SearchResult(infohash, riftFile.filename(), riftFile.totalSize(), peers.size());
                    result.setRiftFile(riftFile); // Store the fetched metadata
                    Platform.runLater(() -> searchResults.add(result));
                }
            });
        });
    }

    /**
     * Starts the download for a selected search result.
     * @param selectedResult The search result item to download.
     */
    public void startDownload(SearchResult selectedResult) {
        if (selectedResult == null || selectedResult.getRiftFile() == null) {
            logger.error("Cannot start download, search result or its metadata is null.");
            return;
        }
        
        RiftFile riftFile = selectedResult.getRiftFile();
        DownloadItem newItem = new DownloadItem(riftFile.filename());
        Platform.runLater(() -> downloadItems.add(newItem));
        
        downloadManager.startDownload(riftFile, selectedResult.getInfohash(), newItem);
    }
    
    private CompletableFuture<RiftFile> fetchRiftFileFromPeer(String infohash, PeerAddress peer) {
    return CompletableFuture.supplyAsync(() -> {
        SSLSocket socket = null;
        try {
            String host = peer.inetAddress().getHostAddress();
            int uploadPort = Constants.UPLOAD_PORT;
            
            // Create SSL socket
            socket = securityService.createSocket(host, uploadPort);
            
            // Use try-with-resources for streams only
            try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                
                // A special request type to distinguish from chunk requests
                out.println("GET_RIFT");
                out.println(infohash);
                
                // Read the JSON response line by line
                StringBuilder jsonBuilder = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    jsonBuilder.append(line);
                }
                
                return new Gson().fromJson(jsonBuilder.toString(), RiftFile.class);
            }
            
        } catch (IOException e) {
            logger.error("Failed to fetch .rift file from peer " + peer, e);
            return null;
        } finally {
            // Manually close the socket
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    logger.error("Error closing SSLSocket", e);
                }
            }
        }
    });
}   
}