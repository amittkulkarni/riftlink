package com.riftlink.p2p.ui.model;

import com.riftlink.p2p.model.RiftFile;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Represents a single search result item in the UI's search results table.
 */
public class SearchResult {
    private final StringProperty filename = new SimpleStringProperty();
    private final StringProperty size = new SimpleStringProperty();
    private final IntegerProperty peers = new SimpleIntegerProperty();
    private final String infohash;
    private RiftFile riftFile; // Can be null until fetched

    public SearchResult(String infohash, String filename, long totalSize, int peerCount) {
        this.infohash = infohash;
        this.filename.set(filename);
        this.size.set(formatSize(totalSize));
        this.peers.set(peerCount);
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    // --- JavaFX Properties ---
    public StringProperty filenameProperty() { return filename; }
    public StringProperty sizeProperty() { return size; }
    public IntegerProperty peersProperty() { return peers; }

    // --- Standard Getters ---
    public String getInfohash() { return infohash; }
    public RiftFile getRiftFile() { return riftFile; }
    public void setRiftFile(RiftFile riftFile) { this.riftFile = riftFile; }
}