package athena.coder.ui.leftmenu;

import athena.coder.entity.tree.ProjectNode;
import athena.coder.entity.tree.QuestEntity;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import static athena.coder.app.ProjectManager.itemClick;
import static athena.coder.app.SerializationUtil.deserializeFromString;
import static athena.coder.entity.tree.TreeNodeType.PROJECT;
import static athena.coder.ui.leftmenu.LeftMenuUI.leftMenuWidth;


public class QuestCell extends TreeCell<QuestEntity> {

    private static final Long ROOT_PARENT_ID = 0L;

    // 预创建 UI 组件，避免每次 updateItem 重新分配
    private final HBox hBox;
    private final FontIcon folderIcon;
    private final Circle dotIcon;
    private final Label titleLabel;

    private final Tooltip tooltip;

    public QuestCell() {
        this.setStyle("-fx-background-color: transparent; -fx-indent: 0;");

        folderIcon = new FontIcon(Feather.FOLDER);
        dotIcon = new Circle(2.5, Color.GRAY);

        titleLabel = new Label();
        titleLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        titleLabel.setMaxWidth(leftMenuWidth);
        titleLabel.setAlignment(Pos.CENTER_LEFT);
        titleLabel.setTooltip(new Tooltip());

        tooltip = new Tooltip();
        tooltip.setShowDuration(Duration.millis(1500));

        hBox = new HBox();
        hBox.setAlignment(Pos.CENTER_LEFT);
        hBox.setSpacing(5);
    }

    @Override
    protected void updateItem(QuestEntity item, boolean empty) {
        super.updateItem(item, empty);
        setCursor(Cursor.HAND);

        if (item == null || empty || ROOT_PARENT_ID.equals(item.getParentId())) {
            setText(null);
            setGraphic(null);
            return;
        }

        // 仅更新组件属性，不重新创建
        if (PROJECT.name().equals(item.getType())) {
            if (hBox.getChildren().isEmpty() || hBox.getChildren().get(0) != folderIcon) {
                hBox.getChildren().clear();
                hBox.getChildren().add(folderIcon);
                ProjectNode projectNode = deserializeFromString(item.getExpand(), ProjectNode.class);
                tooltip.setText(projectNode != null ? projectNode.getAbsoluteFullPath() : item.getTitle());
            }
        } else {
            if (hBox.getChildren().isEmpty() || hBox.getChildren().get(0) != dotIcon) {
                hBox.getChildren().clear();
                hBox.getChildren().add(dotIcon);
                tooltip.setText(item.getTitle());
            }
        }
        titleLabel.setText(item.getTitle());
        titleLabel.setTooltip(tooltip);

        if (hBox.getChildren().size() < 2) {
            hBox.getChildren().add(titleLabel);
        }

        this.setGraphic(hBox);
        this.setOnMouseClicked(event -> {
            if (event.getClickCount() == 1) {
                itemClick(item);
            }
        });
    }
}