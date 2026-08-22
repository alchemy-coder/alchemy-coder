package athena.coder.ui.modelselect;

import athena.coder.entity.model.EmbeddingModelEnum;
import athena.coder.entity.model.LLMModelEnum;
import athena.coder.entity.model.ModelType;
import athena.coder.infra.repository.SqliteModelConfig;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.jspecify.annotations.NonNull;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.function.Consumer;

/**
 * 模型配置弹窗：语言大模型 + 向量大模型选择，以及各自的 API Key 设置。
 */
public class ModelConfigDialog {

    private static final double DIALOG_WIDTH = 440;
    private static final double DIALOG_HEIGHT = 450;

    private static final String CARD_STYLE =
            "-fx-background-color: #F9FAFB;" +
            "-fx-border-color: #E5E7EB;" +
            "-fx-border-radius: 8;" +
            "-fx-background-radius: 8;" +
            "-fx-padding: 12;";

    private static final String INPUT_STYLE =
            "-fx-background-color: #FFFFFF;" +
            "-fx-border-color: #D1D5DB;" +
            "-fx-border-radius: 6;" +
            "-fx-background-radius: 6;";

    private static final String INPUT_FOCUS_STYLE =
            "-fx-background-color: #FFFFFF;" +
            "-fx-border-color: #6366F1;" +
            "-fx-border-radius: 6;" +
            "-fx-background-radius: 6;";

    private static final String INNER_FIELD_STYLE =
            "-fx-background-color: transparent;" +
            "-fx-border-color: transparent;" +
            "-fx-padding: 8 28 8 12;" +
            "-fx-font-size: 13px;";

    private static final String RADIO_STYLE =
            "-fx-font-size: 13px;" +
            "-fx-padding: 6 10 6 10;" +
            "-fx-background-color: transparent;" +
            "-fx-background-radius: 6;" +
            "-fx-text-fill: #374151;";

    private static final String RADIO_SELECTED_STYLE =
            "-fx-font-size: 13px;" +
            "-fx-padding: 6 10 6 10;" +
            "-fx-background-color: #F3F4F6;" +
            "-fx-background-radius: 6;" +
            "-fx-text-fill: #374151;";

    private static final String BTN_STYLE =
            "-fx-background-color: #F3F4F6;" +
            "-fx-text-fill: #374151;" +
            "-fx-font-size: 13px;" +
            "-fx-padding: 8 14 8 14;" +
            "-fx-background-radius: 8;" +
            "-fx-cursor: hand;";

    private static final String BTN_HOVER_STYLE =
            "-fx-background-color: #E5E7EB;" +
            "-fx-text-fill: #111827;" +
            "-fx-font-size: 13px;" +
            "-fx-padding: 8 14 8 14;" +
            "-fx-background-radius: 8;" +
            "-fx-cursor: hand;";

    private static final String SAVE_BTN_STYLE =
            "-fx-background-color: #EEF2FF;" +
            "-fx-text-fill: #4F46E5;" +
            "-fx-font-weight: bold;" +
            "-fx-font-size: 13px;" +
            "-fx-padding: 8 14 8 14;" +
            "-fx-background-radius: 8;" +
            "-fx-cursor: hand;";

    private static final String SAVE_BTN_HOVER_STYLE =
            "-fx-background-color: #E0E7FF;" +
            "-fx-text-fill: #4338CA;" +
            "-fx-font-weight: bold;" +
            "-fx-font-size: 13px;" +
            "-fx-padding: 8 14 8 14;" +
            "-fx-background-radius: 8;" +
            "-fx-cursor: hand;";

    private final SqliteModelConfig modelConfig = new SqliteModelConfig();

    private LLMModelEnum initialLlmModel;
    private String initialLlmKey;
    private EmbeddingModelEnum initialEmbModel;
    private String initialEmbKey;

    private LLMModelEnum selectedLlmModel;
    private EmbeddingModelEnum selectedEmbModel;

    private final ToggleGroup llmGroup = new ToggleGroup();
    private final ToggleGroup embGroup = new ToggleGroup();

    private final PasswordToggleField llmKeyField = new PasswordToggleField();
    private final PasswordToggleField embKeyField = new PasswordToggleField();

    private final Label llmKeyError = new Label("请填写语言大模型 API Key");

    public ModelConfigDialog() {
        llmKeyError.setStyle("-fx-text-fill: #EF4444; -fx-font-size: 12px;");
        llmKeyError.setVisible(false);

        llmKeyField.textProperty().addListener((obs, old, val) -> {
            llmKeyError.setVisible(false);
        });

        String[] defaultLlm = modelConfig.findDefaultModel(ModelType.CHAT);
        LLMModelEnum llmFromDb = LLMModelEnum.fromNameVersion(defaultLlm != null ? defaultLlm[0] : "", defaultLlm != null ? defaultLlm[1] : "");
        this.initialLlmModel = llmFromDb != null ? llmFromDb : LLMModelEnum.DEEPSEEKV4PRO;
        this.initialLlmKey = modelConfig.findApiKey(ModelType.CHAT, initialLlmModel.getModel(), initialLlmModel.getVersion());
        this.selectedLlmModel = initialLlmModel;

        String[] defaultEmb = modelConfig.findDefaultModel(ModelType.EMBEDDING);
        EmbeddingModelEnum embFromDb = EmbeddingModelEnum.fromNameVersion(defaultEmb != null ? defaultEmb[0] : "", defaultEmb != null ? defaultEmb[1] : "");
        this.initialEmbModel = embFromDb != null ? embFromDb : EmbeddingModelEnum.QIANWEN_EMBEDDING_V4;
        this.initialEmbKey = modelConfig.findApiKey(ModelType.EMBEDDING, initialEmbModel.getModel(), initialEmbModel.getVersion());
        this.selectedEmbModel = initialEmbModel;
    }

    public void show(Stage owner, Consumer<Result> onSave) {
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.UNDECORATED);
        dialog.setScene(new Scene(buildContent(dialog, onSave), DIALOG_WIDTH, DIALOG_HEIGHT));
        dialog.setResizable(false);
        dialog.show();
    }

    private VBox buildContent(Stage dialog, Consumer<Result> onSave) {
        VBox root = new VBox(8);
        root.setPadding(new Insets(14));
        root.setStyle(
                "-fx-background-color: #FFFFFF;" +
                "-fx-border-color: #D1D5DB;" +
                "-fx-border-radius: 8;" +
                "-fx-background-radius: 8;" +
                "-fx-border-width: 1;"
        );

        HBox titleBar = new HBox();
        titleBar.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("模型配置");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #111827;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeBtn = getCloseBtn("✕", "-fx-background-color: transparent;" +
                "-fx-text-fill: #9CA3AF;" +
                "-fx-font-size: 14px;" +
                "-fx-padding: 0 0 0 8;" +
                "-fx-cursor: hand;", "-fx-background-color: transparent;" +
                "-fx-text-fill: #374151;" +
                "-fx-font-size: 14px;" +
                "-fx-padding: 0 0 0 8;" +
                "-fx-cursor: hand;", dialog);

        titleBar.getChildren().addAll(title, spacer, closeBtn);

        root.getChildren().add(titleBar);
        root.getChildren().add(buildLlmCard());
        root.getChildren().add(buildEmbCard());
        root.getChildren().add(buildButtonBar(dialog, onSave));

        return root;
    }

    private static @NonNull Button getCloseBtn(String text, String value, String value1, Stage dialog) {
        Button closeBtn = new Button(text);
        closeBtn.setStyle(
                value
        );
        closeBtn.setOnMouseEntered(e -> closeBtn.setStyle(
                value1
        ));
        closeBtn.setOnMouseExited(e -> closeBtn.setStyle(
                value
        ));
        closeBtn.setOnAction(e -> dialog.close());
        return closeBtn;
    }

    private VBox buildLlmCard() {
        VBox card = new VBox(8);
        card.setStyle(CARD_STYLE);

        card.getChildren().add(buildSectionTitle("语言大模型"));
        card.getChildren().add(buildLlmRadioGroup());
        card.getChildren().add(buildKeyField(llmKeyField, "语言大模型 API Key", initialLlmKey));
        card.getChildren().add(llmKeyError);

        selectInitial(llmGroup, initialLlmModel);
        return card;
    }

    private VBox buildEmbCard() {
        VBox card = new VBox(8);
        card.setStyle(CARD_STYLE);

        card.getChildren().add(buildSectionTitle("向量大模型"));
        card.getChildren().add(buildEmbRadioGroup());
        card.getChildren().add(buildKeyField(embKeyField, "向量大模型 API Key", initialEmbKey));

        selectInitial(embGroup, initialEmbModel);
        return card;
    }

    private Label buildSectionTitle(String text) {
        Label title = new Label(text);
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #374151;");
        return title;
    }

    private VBox buildLlmRadioGroup() {
        VBox group = new VBox(2);

        for (LLMModelEnum model : LLMModelEnum.values()) {
            RadioButton rb = createStyledRadioButton(ModelSelectView.formatModelName(model));
            rb.setToggleGroup(llmGroup);
            rb.setUserData(model);
            rb.setOnAction(e -> {
                selectedLlmModel = model;
                llmKeyField.setPromptText("输入 " + ModelSelectView.formatModelName(model) + " 的 API Key");
                String apiKey = modelConfig.findApiKey(ModelType.CHAT, model.getModel(), model.getVersion());
                llmKeyField.setText(apiKey != null ? apiKey : "");
            });
            group.getChildren().add(rb);
        }

        return group;
    }

    private VBox buildEmbRadioGroup() {
        VBox group = new VBox(2);

        for (EmbeddingModelEnum model : EmbeddingModelEnum.values()) {
            RadioButton rb = createStyledRadioButton(formatEmbModelName(model));
            rb.setToggleGroup(embGroup);
            rb.setUserData(model);
            rb.setOnAction(e -> {
                selectedEmbModel = model;
                embKeyField.setPromptText("输入 " + formatEmbModelName(model) + " 的 API Key");
                String apiKey = modelConfig.findApiKey(ModelType.EMBEDDING, model.getModel(), model.getVersion());
                embKeyField.setText(apiKey != null ? apiKey : "");
            });
            group.getChildren().add(rb);
        }

        return group;
    }

    private RadioButton createStyledRadioButton(String text) {
        RadioButton rb = new RadioButton(text);
        rb.setStyle(RADIO_STYLE);
        rb.setCursor(Cursor.HAND);
        rb.setMaxWidth(Double.MAX_VALUE);
        rb.selectedProperty().addListener((obs, old, selected) -> {
            if (selected) {
                rb.setStyle(RADIO_SELECTED_STYLE);
            } else {
                rb.setStyle(RADIO_STYLE);
            }
        });
        return rb;
    }

    private Region buildKeyField(PasswordToggleField field, String prompt, String initialKey) {
        field.setPromptText(prompt);
        field.setMaxWidth(Double.MAX_VALUE);
        field.setText(initialKey != null ? initialKey : "");
        field.setStyle(INPUT_STYLE);

        field.focusedProperty().addListener((obs, old, focused) -> {
            if (focused) {
                field.setStyle(INPUT_FOCUS_STYLE);
            } else {
                field.setStyle(INPUT_STYLE);
            }
        });

        return field;
    }

    private void selectInitial(ToggleGroup group, Object model) {
        if (model != null) {
            for (var toggle : group.getToggles()) {
                if (toggle.getUserData() == model) {
                    toggle.setSelected(true);
                    break;
                }
            }
        }
    }

    private HBox buildButtonBar(Stage dialog, Consumer<Result> onSave) {
        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = getCloseBtn("取消", BTN_STYLE, BTN_HOVER_STYLE, dialog);

        Button saveBtn = new Button("保存配置");
        saveBtn.setStyle(SAVE_BTN_STYLE);
        saveBtn.setOnMouseEntered(e -> saveBtn.setStyle(SAVE_BTN_HOVER_STYLE));
        saveBtn.setOnMouseExited(e -> saveBtn.setStyle(SAVE_BTN_STYLE));
        saveBtn.setDefaultButton(true);
        saveBtn.setOnAction(e -> {
            String llmKey = llmKeyField.getText().trim();
            if (llmKey.isEmpty()) {
                llmKeyError.setVisible(true);
                llmKeyField.requestFocus();
                return;
            }
            modelConfig.saveModel(ModelType.CHAT, selectedLlmModel.getModel(), selectedLlmModel.getVersion(), llmKey, true);
            String embKey = embKeyField.getText().trim();
            if (!embKey.isEmpty()) {
                modelConfig.saveModel(ModelType.EMBEDDING, selectedEmbModel.getModel(), selectedEmbModel.getVersion(), embKey, true);
            }
            onSave.accept(new Result(selectedLlmModel, llmKey, selectedEmbModel, embKey));
            dialog.close();
        });

        bar.getChildren().addAll(cancelBtn, saveBtn);
        return bar;
    }

    private static String formatEmbModelName(EmbeddingModelEnum model) {
        return switch (model) {
            case QIANWEN_EMBEDDING_V4 -> "千问 Embedding V4";
        };
    }

    private static class PasswordToggleField extends StackPane {

        private final PasswordField passwordField = new PasswordField();
        private final TextField textField = new TextField();
        private final Button toggleBtn = new Button();
        private boolean revealed = false;

        PasswordToggleField() {
            passwordField.setMaxWidth(Double.MAX_VALUE);
            textField.setMaxWidth(Double.MAX_VALUE);
            passwordField.setStyle(INNER_FIELD_STYLE);
            textField.setStyle(INNER_FIELD_STYLE);

            textField.managedProperty().bind(textField.visibleProperty());
            passwordField.managedProperty().bind(passwordField.visibleProperty());

            textField.visibleProperty().addListener((obs, old, visible) -> {
                if (visible) {
                    textField.requestFocus();
                    textField.positionCaret(textField.getLength());
                }
            });

            passwordField.visibleProperty().addListener((obs, old, visible) -> {
                if (visible) {
                    passwordField.requestFocus();
                    passwordField.positionCaret(passwordField.getLength());
                }
            });

            textField.textProperty().bindBidirectional(passwordField.textProperty());

            FontIcon eyeIcon = new FontIcon(Feather.EYE);
            eyeIcon.setIconSize(16);
            toggleBtn.setGraphic(eyeIcon);
            toggleBtn.setStyle(
                    "-fx-background-color: transparent;" +
                    "-fx-padding: 4 8 4 4;" +
                    "-fx-cursor: hand;"
            );
            toggleBtn.setOnAction(e -> toggle());

            textField.setVisible(false);
            passwordField.setVisible(true);

            StackPane.setAlignment(toggleBtn, Pos.CENTER_RIGHT);
            getChildren().addAll(passwordField, textField, toggleBtn);
        }

        private void toggle() {
            revealed = !revealed;
            FontIcon icon = new FontIcon(revealed ? Feather.EYE_OFF : Feather.EYE);
            icon.setIconSize(16);
            toggleBtn.setGraphic(icon);
            textField.setVisible(revealed);
            passwordField.setVisible(!revealed);
        }

        void setPromptText(String text) {
            passwordField.setPromptText(text);
            textField.setPromptText(text);
        }

        String getText() {
            return passwordField.getText();
        }

        void setText(String text) {
            passwordField.setText(text);
        }

        javafx.beans.property.StringProperty textProperty() {
            return passwordField.textProperty();
        }
    }

    public record Result(LLMModelEnum llmModel, String llmApiKey,
                         EmbeddingModelEnum embModel, String embApiKey) {
    }
}