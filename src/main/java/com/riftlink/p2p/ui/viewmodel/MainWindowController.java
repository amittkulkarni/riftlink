package com.riftlink.p2p.ui.controller;

import com.riftlink.p2p.ui.model.DownloadItem;
import com.riftlink.p2p.ui.model.SearchResult;
import com.riftlink.p2p.viewmodel.MainViewModel;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import java.io.File;

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

    /**
     * Binds the UI components to the ViewModel's properties.
     */
    private void bindViewModel() {
        downloadsTable.setItems(viewModel.getDownloadItems());
        libraryListView.setItems(viewModel.getLibraryItems());
        searchResultsTable.setItems(viewModel.getSearchResults());
    }

    @FXML
    private void handleSearchAction() {
        String query = searchTextField.getText();
        if (query != null && !query.isEmpty()) {
            viewModel.search(query);
        }
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
}