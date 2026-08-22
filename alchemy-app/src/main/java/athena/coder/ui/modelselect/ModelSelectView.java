package athena.coder.ui.modelselect;

import atlantafx.base.theme.Styles;
import athena.coder.entity.model.LLMModelEnum;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.function.Consumer;

/**
 * 模型选择面板
 * 从 ModelEnum 动态生成选项，点击后回调通知调用方
 */
public class ModelSelectView {

    /**
     * 创建模型选择弹窗面板
     *
     * @param onSelect 选中模型后的回调
     * @param onClose  关闭面板的回调
     */
    public static VBox createModelSelectPanel(Consumer<LLMModelEnum> onSelect, Runnable onClose) {
        VBox panel = new VBox(10);
        panel.setAlignment(Pos.TOP_CENTER);
        panel.setStyle(
                "-fx-background-color: #FFFFFF;" +
                        "-fx-border-color: #E0E0E0;" +
                        "-fx-border-radius: 8;" +
                        "-fx-background-radius: 8;" +
                        "-fx-padding: 16;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 10, 0, 0, 2);"
        );
        panel.setMaxWidth(250);

        // 标题
        javafx.scene.control.Label title = new javafx.scene.control.Label("选择模型");
        title.setFont(Font.font("System", FontWeight.BOLD, 14));
        panel.getChildren().add(title);

        // 模型列表
        for (LLMModelEnum model : LLMModelEnum.values()) {
            Button modelBtn = createModelButton(model);
            modelBtn.setOnAction(e -> {
                onSelect.accept(model);
                onClose.run();
            });
            panel.getChildren().add(modelBtn);
        }

        // 取消按钮
        Button cancelBtn = new Button("取消");
        cancelBtn.getStyleClass().addAll(Styles.FLAT);
        cancelBtn.setMaxWidth(Double.MAX_VALUE);
        cancelBtn.setCursor(Cursor.HAND);
        cancelBtn.setOnAction(e -> onClose.run());
        panel.getChildren().add(cancelBtn);

        return panel;
    }

    private static Button createModelButton(LLMModelEnum model) {
        String displayName = formatModelName(model);
        Button btn = new Button(displayName, new FontIcon(Feather.CPU));
        btn.getStyleClass().addAll(Styles.FLAT, Styles.ACCENT);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setCursor(Cursor.HAND);
        return btn;
    }

    /**
     * 将 ModelEnum 格式化为可读的显示名称
     */
    public static String formatModelName(LLMModelEnum model) {
        return switch (model) {
            case QIANWEN37MAX -> "Qwen 3.7 Max";
            case QIANWEN35FLASH -> "Qwen 3.5 Flash";
            case DEEPSEEKV4PRO -> "DeepSeek V4 Pro";
        };
    }

    /**
     * 创建模型选择按钮（用于 LeftMenuUI 底部）
     */
    public static Button createModelSelectButton() {
        Button selectModel = new Button("选择模型", new FontIcon(Feather.ZAP));
        selectModel.setMaxWidth(Double.MAX_VALUE);
        selectModel.setAlignment(Pos.CENTER_LEFT);
        selectModel.setCursor(Cursor.HAND);
        selectModel.getStyleClass().addAll(Styles.FLAT, Styles.ACCENT);
        return selectModel;
    }
}
