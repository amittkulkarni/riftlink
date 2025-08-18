package com.riftlink.p2p.ui.model;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * A model class representing a single download item in the UI's download list.
 * It uses JavaFX properties to allow for automatic UI updates upon value changes.
 */
public class DownloadItem {
    private final StringProperty filename = new SimpleStringProperty();
    private final StringProperty status = new SimpleStringProperty();
    private final DoubleProperty progress = new SimpleDoubleProperty();
    private final StringProperty speed = new SimpleStringProperty();

    public DownloadItem(String filename) {
        this.filename.set(filename);
        this.status.set("Starting...");
        this.progress.set(0.0);
        this.speed.set("0 KB/s");
    }

    // --- JavaFX Property Getters ---
    public StringProperty filenameProperty() { return filename; }
    public StringProperty statusProperty() { return status; }
    public DoubleProperty progressProperty() { return progress; }
    public StringProperty speedProperty() { return speed; }

    // --- Standard Getters/Setters for convenience ---
    public String getFilename() { return filename.get(); }
    public void setStatus(String status) { this.status.set(status); }
    public void setProgress(double progress) { this.progress.set(progress); }
    public void setSpeed(String speed) { this.speed.set(speed); }
}