package net.hero.genai.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import net.hero.genai.model.AuditLogEntry;
import net.hero.genai.model.SecurityRule;
import net.hero.genai.service.SecurityService;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SecuritySettingsController {

    private static final Logger LOGGER = Logger.getLogger(SecuritySettingsController.class.getName());

    @FXML private CheckBox chkEnableSecurity;
    @FXML private ComboBox<String> comboAutoRestore;
    @FXML private TabPane tabPane;

    // Rules Tab
    @FXML private TableView<SecurityRule> tblRules;
    @FXML private TableColumn<SecurityRule, String> colRuleCategory;
    @FXML private TableColumn<SecurityRule, String> colRuleType;
    @FXML private TableColumn<SecurityRule, String> colRulePattern;

    // Add form
    @FXML private ComboBox<String> comboNewCategory;
    @FXML private CheckBox chkNewIsDeny;
    @FXML private TextField txtNewPattern;
    @FXML private Button btnAddRule;

    @FXML private Button btnDeleteRule;
    @FXML private Button btnSaveRules;

    // Direct Edit Tab
    @FXML private TextArea txtRawConf;
    @FXML private Button btnResetConf;
    @FXML private Button btnSaveRawConf;

    // Audit Logs Tab
    @FXML private TableView<AuditLogEntry> tblAuditLogs;
    @FXML private TableColumn<AuditLogEntry, String> colAuditTimestamp;
    @FXML private TableColumn<AuditLogEntry, String> colAuditCategory;
    @FXML private TableColumn<AuditLogEntry, String> colAuditOperation;
    @FXML private TableColumn<AuditLogEntry, String> colAuditResult;
    @FXML private TableColumn<AuditLogEntry, AuditLogEntry> colAuditAction;

    @FXML private Button btnClearAuditLogs;

    private final SecurityService securityService = SecurityService.getInstance();
    private final ObservableList<SecurityRule> ruleList = FXCollections.observableArrayList();
    private final ObservableList<AuditLogEntry> auditLogList = FXCollections.observableArrayList();

    private boolean isUpdatingCheckbox = false;

    @FXML
    public void initialize() {
        LOGGER.log(Level.INFO, "Initializing SecuritySettingsController...");

        // Setup Checkbox & Restore ComboBox
        chkEnableSecurity.setSelected(securityService.isEnabled());
        chkEnableSecurity.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (isUpdatingCheckbox) {
                return;
            }
            if (!newVal) {
                // Confirm disabling security
                final Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("セキュリティ制限の無効化");
                alert.setHeaderText("警告: セキュリティ制限の無効化");
                alert.setContentText("セキュリティ制限を無効化すると、AIエージェントによるファイルの破壊、" +
                        "不要なコマンド実行、外部へのデータ送信のリスクが高まります。無効化しますか？");

                final ButtonType btnConfirm = new ButtonType("無効化する (非推奨)", ButtonType.OK.getButtonData());
                final ButtonType btnCancel = new ButtonType("キャンセル", ButtonType.CANCEL.getButtonData());
                alert.getButtonTypes().setAll(btnCancel, btnConfirm);

                final Optional<ButtonType> result = alert.showAndWait();
                if (result.isPresent() && result.get() == btnConfirm) {
                    securityService.setEnabled(false);
                } else {
                    // Revert checkbox state
                    isUpdatingCheckbox = true;
                    chkEnableSecurity.setSelected(true);
                    isUpdatingCheckbox = false;
                }
            } else {
                securityService.setEnabled(true);
            }
        });

        // Register callback to update checkbox state when timer auto-restores
        securityService.registerOnSecurityStateChanged(() -> {
            isUpdatingCheckbox = true;
            chkEnableSecurity.setSelected(securityService.isEnabled());
            isUpdatingCheckbox = false;
        });

        // Populate Auto Restore Combobox
        comboAutoRestore.getItems().addAll("5分", "10分", "30分", "セッション終了時", "自動復帰なし");
        selectAutoRestoreCombo(securityService.getAutoRestoreMinutes());
        comboAutoRestore.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                securityService.setAutoRestoreMinutes(parseAutoRestoreMinutes(newVal));
            }
        });

        // Setup Rules Table
        colRuleCategory.setCellValueFactory(new PropertyValueFactory<>("category"));
        colRuleType.setCellValueFactory(cellData -> new SimpleStringProperty(
                cellData.getValue().isDeny() ? "拒否 (Deny)" : "許可 (Allow)"
        ));
        colRulePattern.setCellValueFactory(new PropertyValueFactory<>("pattern"));

        ruleList.setAll(securityService.getRules());
        tblRules.setItems(ruleList);

        // Preload add-form category combo
        comboNewCategory.getItems().addAll("file-access", "program-execution", "http-url");
        comboNewCategory.getSelectionModel().select(0);

        // Setup Direct Edit Conf
        updateRawConfTextArea();

        // Setup Audit Logs Table
        colAuditTimestamp.setCellValueFactory(new PropertyValueFactory<>("timestamp"));
        colAuditCategory.setCellValueFactory(new PropertyValueFactory<>("category"));
        colAuditOperation.setCellValueFactory(new PropertyValueFactory<>("operation"));
        colAuditResult.setCellValueFactory(new PropertyValueFactory<>("result"));
        colAuditAction.setCellValueFactory(cellData -> new javafx.beans.property.SimpleObjectProperty<>(cellData.getValue()));

        colAuditAction.setCellFactory(column -> new TableCell<>() {
            private final Button btnAddAllow = new Button("許可に追加");

            {
                btnAddAllow.setStyle("-fx-font-size: 11px; -fx-padding: 2px 6px;");
                btnAddAllow.setOnAction(event -> {
                    final AuditLogEntry entry = getItem();
                    if (entry != null) {
                        handleQuickAddAllowRule(entry);
                    }
                });
            }

            @Override
            protected void updateItem(AuditLogEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else if (item.result().contains("DENY")) {
                    setGraphic(btnAddAllow);
                } else {
                    setGraphic(null);
                }
            }
        });

        auditLogList.setAll(securityService.getAuditLogs());
        tblAuditLogs.setItems(auditLogList);
    }

    private int parseAutoRestoreMinutes(final String label) {
        return switch (label) {
            case "5分" -> 5;
            case "10分" -> 10;
            case "30分" -> 30;
            case "セッション終了時" -> 0;
            default -> -1; // 自動復帰なし
        };
    }

    private void selectAutoRestoreCombo(final int minutes) {
        final String label = switch (minutes) {
            case 5 -> "5分";
            case 10 -> "10分";
            case 30 -> "30分";
            case 0 -> "セッション終了時";
            default -> "自動復帰なし";
        };
        comboAutoRestore.getSelectionModel().select(label);
    }

    @FXML
    private void handleAddRule() {
        final String category = comboNewCategory.getSelectionModel().getSelectedItem();
        final String pattern = txtNewPattern.getText();
        final boolean isDeny = chkNewIsDeny.isSelected();

        if (pattern == null || pattern.trim().isEmpty()) {
            final Alert alert = new Alert(Alert.AlertType.ERROR, "パターンを入力してください。");
            alert.showAndWait();
            return;
        }

        final SecurityRule rule = new SecurityRule(category, pattern.trim(), isDeny);
        ruleList.add(rule);
        tblRules.getSelectionModel().select(rule);
        txtNewPattern.clear();
        chkNewIsDeny.setSelected(false);
    }

    @FXML
    private void handleDeleteRule() {
        final SecurityRule selected = tblRules.getSelectionModel().getSelectedItem();
        if (selected != null) {
            ruleList.remove(selected);
        }
    }

    @FXML
    private void handleSaveRules() {
        securityService.setRules(new ArrayList<>(ruleList));
        saveToWorkspaceIfAvailable();
        updateRawConfTextArea();

        final Alert alert = new Alert(Alert.AlertType.INFORMATION, "ルールを保存しました。");
        alert.showAndWait();
    }

    @FXML
    private void handleResetConf() {
        securityService.loadDefaultRules();
        ruleList.setAll(securityService.getRules());
        updateRawConfTextArea();
        saveToWorkspaceIfAvailable();

        final Alert alert = new Alert(Alert.AlertType.INFORMATION, "デフォルトルールにリセットしました。");
        alert.showAndWait();
    }

    @FXML
    private void handleSaveRawConf() {
        final String rawText = txtRawConf.getText();
        try {
            // Write to a temporary file, parse using securityService, then load back
            final File temp = File.createTempFile("security_rules_temp", ".conf");
            temp.deleteOnExit();
            Files.writeString(temp.toPath(), rawText, StandardCharsets.UTF_8);

            securityService.loadFromFile(temp);
            ruleList.setAll(securityService.getRules());
            saveToWorkspaceIfAvailable();

            final Alert alert = new Alert(Alert.AlertType.INFORMATION, "直接編集した設定を反映して保存しました。");
            alert.showAndWait();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to apply raw configuration edits", e);
            final Alert alert = new Alert(Alert.AlertType.ERROR, "設定の保存中にエラーが発生しました: " + e.getMessage());
            alert.showAndWait();
        }
    }

    @FXML
    private void handleClearAuditLogs() {
        securityService.clearAuditLogs();
        auditLogList.clear();
    }

    private void handleQuickAddAllowRule(final AuditLogEntry entry) {
        // Quick add an allow rule corresponding to this denied item
        // If it's a file, we might suggest workspace pattern or the exact file
        String pattern = entry.operation();
        final File workspace = securityService.getActiveWorkspace();
        if (workspace != null && "file-access".equals(entry.category())) {
            final String workspacePath = workspace.getAbsolutePath().replace('\\', '/');
            final String opPath = pattern.replace('\\', '/');
            if (opPath.startsWith(workspacePath)) {
                // Generate a pattern relative to workspace
                pattern = "${WORKSPACE_DIR}" + opPath.substring(workspacePath.length());
            }
        }

        final SecurityRule newRule = new SecurityRule(entry.category(), pattern, false);
        ruleList.add(newRule);
        tblRules.getSelectionModel().select(newRule);

        // Show rules tab
        tabPane.getSelectionModel().select(0);

        final Alert alert = new Alert(Alert.AlertType.INFORMATION,
                "監査履歴から新しい許可ルールを提案・追加しました。保存ボタンを押して有効化してください。");
        alert.showAndWait();
    }

    private void updateRawConfTextArea() {
        final List<SecurityRule> rules = securityService.getRules();
        final List<String> lines = new ArrayList<>();
        final String[] categories = {"file-access", "program-execution", "http-url"};
        for (final String category : categories) {
            lines.add("[" + category + "]");
            for (final SecurityRule rule : rules) {
                if (rule.category().equalsIgnoreCase(category)) {
                    lines.add(rule.toLine());
                }
            }
            lines.add("");
        }
        txtRawConf.setText(String.join("\n", lines));
    }

    private void saveToWorkspaceIfAvailable() {
        final File workspace = securityService.getActiveWorkspace();
        if (workspace != null) {
            securityService.saveToFile(new File(workspace, "security_rules.conf"));
        }
    }

    public void refreshAuditLogs() {
        auditLogList.setAll(securityService.getAuditLogs());
    }
}
