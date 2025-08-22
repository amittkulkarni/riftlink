package com.riftlink.p2p.ui.viewmodel;

import com.riftlink.p2p.ui.model.DownloadItem;
import com.riftlink.p2p.ui.model.SearchResult;
import com.riftlink.p2p.ui.viewmodel.MainViewModel;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.cell.ProgressBarTableCell;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import java.io.File;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import java.util.Optional;

/**
 * The Controller for the main application window (MainWindow.fxml).
 * It handles user interactions and forwards them to the ViewModel.
 */
public class MainWindowController {

    // ViewModel
    private MainViewModel viewModel;

    // FXML Injected Fields
    @FXML private TextField searchTextField;
    @FXML private TableView<SearchResult> searchResultsTable;
    @FXML private TableView<DownloadItem> downloadsTable;
    @FXML private ListView<String> libraryListView;
    @FXML private TextField portTextField;
    @FXML private TextField downloadsFolderTextField;
    @FXML private TableColumn<DownloadItem, Double> progressColumn;
    @FXML private Button downloadButton;
    @FXML private Button copyHashButton;
    @FXML private Button pauseButton;
    @FXML private Button resumeButton;
    @FXML private Button cancelButton;
    @FXML private Button copyHashDownloadButton;
    @FXML private Label statusLabel;


    // To be called by the main application to inject the ViewModel
    public void initViewModel(MainViewModel viewModel) {
        this.viewModel = viewModel;
        bindViewModel();
    }

    /**
     * The initialize method is called automatically by JavaFX after the FXML file has been loaded.
     */
    @FXML
    public void initialize() {

        setupProgressColumn();
        setupSelectionListeners();
        setupDoubleClickActions();

        // Set up double-click action on search results to start a download
        searchResultsTable.setRowFactory(tv -> {
            TableRow<SearchResult> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    SearchResult rowData = row.getItem();
                    viewModel.startDownload(rowData);
                }
            });
            return row;
        });
    }

    private void setupProgressColumn() {
        // If you have a direct reference to the column from FXML
        if (progressColumn != null) {
            progressColumn.setCellFactory(ProgressBarTableCell.<DownloadItem>forTableColumn());
        } else {
            // Alternative: Find the progress column by index (it's the 2nd column, index 1)
            @SuppressWarnings("unchecked")
            TableColumn<DownloadItem, Double> progCol = 
                (TableColumn<DownloadItem, Double>) downloadsTable.getColumns().get(1);
            progCol.setCellFactory(ProgressBarTableCell.<DownloadItem>forTableColumn());
        }
    }

    private void setupSelectionListeners() {
        // Search results selection listener
        searchResultsTable.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldSelection, newSelection) -> {
                boolean hasSelection = newSelection != null;
                downloadButton.setDisable(!hasSelection);
                copyHashButton.setDisable(!hasSelection);
                
                if (hasSelection) {
                    statusLabel.setText("Ready to download: " + newSelection.getFilename());
                } else {
                    statusLabel.setText("Select a file to see actions");
                }
            }
        );
        
        // Downloads table selection listener
        downloadsTable.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldSelection, newSelection) -> {
                updateDownloadButtons(newSelection);
            }
        );
        
        // Library selection listener (for copy hash button)
        libraryListView.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldSelection, newSelection) -> {
                // You can add library-specific button logic here if needed
            }
        );
    }

    private void setupDoubleClickActions() {
        // Set up double-click action on search results to start a download
        searchResultsTable.setRowFactory(tv -> {
            TableRow<SearchResult> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    SearchResult rowData = row.getItem();
                    viewModel.startDownload(rowData);
                }
            });
            return row;
        });
    }

    private void updateDownloadButtons(DownloadItem selectedItem) {
        boolean hasSelection = selectedItem != null;
        
        // Enable/disable all buttons based on selection
        pauseButton.setDisable(!hasSelection);
        resumeButton.setDisable(!hasSelection);
        cancelButton.setDisable(!hasSelection);
        copyHashDownloadButton.setDisable(!hasSelection);
        
        // Update button states based on download status
        if (hasSelection) {
            String status = selectedItem.getStatus();
            
            // Enable pause only if downloading
            pauseButton.setDisable(!"Downloading".equals(status));
            
            // Enable resume only if paused
            resumeButton.setDisable(!"Paused".equals(status));
            
            // Cancel is always available for active downloads
            cancelButton.setDisable("Completed".equals(status));
        }
    }

    /**
     * Binds the UI components to the ViewModel's properties.
     */
    private void bindViewModel() {
        downloadsTable.setItems(viewModel.getDownloadItems());
        libraryListView.setItems(viewModel.getLibraryItems());
        searchResultsTable.setItems(viewModel.getSearchResults());
        
        // Add listener for search results changes
        viewModel.getSearchResults().addListener((javafx.collections.ListChangeListener<SearchResult>) change -> {
            Platform.runLater(() -> {
                if (viewModel.getSearchResults().isEmpty()) {
                    statusLabel.setText("No results found");
                } else {
                    statusLabel.setText("Found " + viewModel.getSearchResults().size() + " result(s)");
                }
            });
        });
    }

    @FXML
    private void handleSearchAction() {
        String query = searchTextField.getText();
        if (query != null && !query.trim().isEmpty()) {
            statusLabel.setText("Searching...");
            viewModel.search(query.trim());
            
            // Optional: Clear selection and disable buttons while searching
            searchResultsTable.getSelectionModel().clearSelection();
        } else {
            showAlert(Alert.AlertType.WARNING, "Search", "Please enter an infohash to search.");
        }
    }

    @FXML
    private void handleClearSearchAction() {
        searchTextField.clear();
        viewModel.getSearchResults().clear();
        statusLabel.setText("Enter an infohash to search");
    }

    @FXML
    private void handleShareFileAction() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select File to Share");
        File selectedFile = fileChooser.showOpenDialog(libraryListView.getScene().getWindow());
        if (selectedFile != null) {
            viewModel.shareFile(selectedFile);
        }
    }

    @FXML
    private void handleBrowseAction() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Downloads Folder");
        File selectedDirectory = directoryChooser.showDialog(downloadsFolderTextField.getScene().getWindow());
        if (selectedDirectory != null) {
            downloadsFolderTextField.setText(selectedDirectory.getAbsolutePath());
        }
    }

    @FXML
    private void handleSaveSettingsAction() {
        // Logic to save settings would go here
        // For example, writing to a properties file
        showAlert(Alert.AlertType.INFORMATION, "Settings", "Settings saved successfully!");
    }

    private void showAlert(Alert.AlertType alertType, String title, String message) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @FXML
    protected void handleDownloadAction(ActionEvent event) {
        SearchResult selected = searchResultsTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            viewModel.startDownload(selected);
            statusLabel.setText("Download started: " + selected.getFilename());
        }
    }

    @FXML
    protected void handleCopyInfoHashAction(ActionEvent event) {
        Optional<String> infoHashOpt = getSelectedInfoHash();

        if(infoHashOpt.isPresent()) {
            String infoHash = infoHashOpt.get();
            final Clipboard clipboard = Clipboard.getSystemClipboard();
            final ClipboardContent content = new ClipboardContent();
            content.putString(infoHash);
            clipboard.setContent(content);
            showAlert(Alert.AlertType.INFORMATION, "Clipboard", "InfoHash copied to Clipboard!");    
        } else {
            showAlert(Alert.AlertType.WARNING, "Clipboard", "No item selected to copy InfoHash from.");
        }
    }

    private Optional<String> getSelectedInfoHash() {
        SearchResult searchResult = searchResultsTable.getSelectionModel().getSelectedItem();
        if (searchResult != null) {
            return Optional.ofNullable(searchResult.getInfoHash());
        }

        DownloadItem downloadItem = downloadsTable.getSelectionModel().getSelectedItem();
        if (downloadItem != null) {
            return Optional.ofNullable(downloadItem.getInfoHash()); 
        }

        String libraryFile = libraryListView.getSelectionModel().getSelectedItem();
        if (libraryFile != null) {
            return viewModel.getInfoHashForLibraryFile(libraryFile);
        }

        return Optional.empty();
    }

    @FXML
    protected void handlePauseDownloadAction(ActionEvent event) {
        DownloadItem selected = downloadsTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            viewModel.pauseDownload(selected);
            updateDownloadButtons(selected); // Refresh button states
        }
    }

    @FXML
    protected void handleResumeDownloadAction(ActionEvent event) {
        DownloadItem selected = downloadsTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            viewModel.resumeDownload(selected);
            updateDownloadButtons(selected); // Refresh button states
        }
    }

    @FXML
    protected void handleCancelDownloadAction(ActionEvent event) {
        DownloadItem selected = downloadsTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
            confirmAlert.setTitle("Confirm Cancellation");
            confirmAlert.setHeaderText(null);
            confirmAlert.setContentText("Are you sure you want to cancel the download of: " + selected.getFilename() + "?");
            
            Optional<ButtonType> result = confirmAlert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                viewModel.cancelDownload(selected);
            }
        }
    }

    @FXML
    protected void handleRemoveFileAction(ActionEvent event) {
        String selected = libraryListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            viewModel.removeSharedFile(selected);
        }
    }
}