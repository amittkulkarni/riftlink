package com.riftlink.p2p;

import com.riftlink.p2p.service.*;
import com.riftlink.p2p.ui.controller.MainWindowController;
import com.riftlink.p2p.util.Constants;
import com.riftlink.p2p.viewmodel.MainViewModel;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * The main entry point for the RiftLink P2P application.
 * Initializes services, loads the UI, and manages the application lifecycle.
 */
public class RiftLinkApp extends Application {
    private static final Logger logger = LoggerFactory.getLogger(RiftLinkApp.class);

    // Backend Services
    private P2PService p2pService;
    private UploadManager uploadManager;
    private DownloadManager downloadManager;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        logger.info("Starting RiftLink P2P Application...");

        // --- 1. Initialize Configuration and Directories ---
        Path userHome = Paths.get(System.getProperty("user.home"));
        Path appDir = userHome.resolve(".riftlink");
        Path sharedDir = appDir.resolve("shared");
        Path downloadsDir = appDir.resolve("downloads");

        // --- 2. Initialize Backend Services ---
        SecurityService securityService = new SecurityService();
        FileManager fileManager = new FileManager(sharedDir, downloadsDir);
        p2pService = new P2PService(Constants.P2P_PORT);
        uploadManager = new UploadManager(securityService, fileManager, sharedDir);
        downloadManager = new DownloadManager(p2pService, securityService, fileManager, downloadsDir);

        // --- 3. Start Networking Services ---
        handleBootstrapping();
        uploadManager.start(Constants.UPLOAD_PORT);

        // --- 4. Initialize ViewModel and UI ---
        MainViewModel viewModel = new MainViewModel(p2pService, fileManager, downloadManager, securityService);
        loadUI(primaryStage, viewModel);
    }

    /**
     * Handles the peer bootstrapping logic based on command-line arguments.
     */
    private void handleBootstrapping() {
        Parameters params = getParameters();
        List<String> args = params.getRaw();
        InetAddress bootstrapAddress = null;
        int bootstrapPort = Constants.P2P_PORT;

        if (!args.isEmpty()) {
            try {
                String[] parts = args.get(0).split(":");
                bootstrapAddress = InetAddress.getByName(parts[0]);
                if (parts.length > 1) {
                    bootstrapPort = Integer.parseInt(parts[1]);
                }
            } catch (Exception e) {
                logger.error("Invalid bootstrap address provided: {}. Starting as a root peer.", args.get(0), e);
            }
        }

        p2pService.start(bootstrapAddress, bootstrapPort);
    }
    
    /**
     * Loads the FXML, initializes the controller, and shows the main application window.
     */
    private void loadUI(Stage primaryStage, MainViewModel viewModel) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("ui/view/MainWindow.fxml"));
        Scene scene = new Scene(loader.load());

        MainWindowController controller = loader.getController();
        controller.initViewModel(viewModel);

        primaryStage.setTitle("RiftLink P2P");
        primaryStage.setScene(scene);
        primaryStage.show();
    }
    
    /**
     * This method is called when the application should stop, and is the
     * ideal place to gracefully shut down services.
     */
    @Override
    public void stop() {
        logger.info("Shutting down RiftLink P2P Application...");
        if (p2pService != null) {
            p2pService.shutdown();
        }
        if (uploadManager != null) {
            uploadManager.stop();
        }
        if (downloadManager != null) {
            downloadManager.shutdown();
        }
        logger.info("Shutdown complete.");
    }
}