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

       @FXML
    protected void handleDownloadAction(ActionEvent event) {
        SearchResult selected = searchResultsTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            viewModel.startDownload(selected);
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
        }
    }

    @FXML
    protected void handleResumeDownloadAction(ActionEvent event) {
        DownloadItem selected = downloadsTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            viewModel.resumeDownload(selected);
        }
    }

    @FXML
    protected void handleCancelDownloadAction(ActionEvent event) {
        DownloadItem selected = downloadsTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            viewModel.cancelDownload(selected);
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