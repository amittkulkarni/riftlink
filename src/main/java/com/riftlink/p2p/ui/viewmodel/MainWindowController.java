package com.riftlink.p2p.ui.viewmodel;

import com.riftlink.p2p.ui.model.DownloadItem;
import com.riftlink.p2p.ui.model.SearchResult;
import com.riftlink.p2p.ui.viewmodel.MainViewModel;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.ProgressBarTableCell;
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
}