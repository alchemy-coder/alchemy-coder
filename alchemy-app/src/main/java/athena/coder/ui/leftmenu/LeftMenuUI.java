package athena.coder.ui.leftmenu;

import athena.coder.entity.model.EmbeddingModelEnum;
import athena.coder.entity.model.LLMModelEnum;
import athena.coder.entity.tree.ProjectNode;
import athena.coder.entity.tree.QuestEntity;
import athena.coder.ui.modelselect.ModelConfigDialog;
import athena.coder.ui.modelselect.ModelSelectView;
import atlantafx.base.theme.Styles;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.*;

import static athena.coder.app.AppState.*;
import static athena.coder.app.ProjectManager.getQuestTreeData;
import static athena.coder.app.ProjectManager.parseProjectNode;
import static athena.coder.app.ProjectManager.updateProjectExpand;
import static athena.coder.entity.tree.TreeNodeType.PROJECT;

public class LeftMenuUI {

    public final static double leftMenuWidth = 200;

    public static Button newCreateQuestButton() {

        Button createQuestButton = new Button("创建Quest", new FontIcon(Feather.PLUS));
        createQuestButton.getStyleClass().addAll(
                Styles.FLAT,
                Styles.ACCENT
        );
        createQuestButton.setMaxWidth(Double.MAX_VALUE);
        createQuestButton.setAlignment(Pos.CENTER_LEFT);
        createQuestButton.setCursor(Cursor.HAND);
        createQuestButton.setOnAction(_ -> {
            chatModel.setValue(false);
            chatList.clear();
            curTaskId.setValue(0);
        });
        createQuestButton.setMinWidth(leftMenuWidth);
        return createQuestButton;
    }

    public static Button newSelectModelButton() {
        Button selectModel = new Button("选择模型", new FontIcon(Feather.ZAP));
        selectModel.setMaxWidth(Double.MAX_VALUE);
        selectModel.setAlignment(Pos.CENTER_LEFT);
        selectModel.setCursor(Cursor.HAND);
        selectModel.getStyleClass().addAll(Styles.FLAT, Styles.ACCENT);

        selectModel.setOnAction(e -> {
            ModelConfigDialog dialog = new ModelConfigDialog(
                    LLMModelEnum.DEEPSEEKV4PRO, null,
                    EmbeddingModelEnum.QIANWEN_EMBEDDING_V4, null);
            dialog.show((javafx.stage.Stage) selectModel.getScene().getWindow(),
                    result -> selectModel.setText(ModelSelectView.formatModelName(result.llmModel())));
        });
        return selectModel;
    }

    public static TreeView<QuestEntity> newQuestTreeView() {
        TreeView<QuestEntity> treeView = new TreeView<>();
        treeView.setMinWidth(leftMenuWidth);
        treeView.setShowRoot(false);
        treeView.getStyleClass().addAll(Styles.SMALL, Styles.ACCENT);
        treeView.setCellFactory(_ -> new QuestCell());
        treeView.setStyle(
                "-fx-border-color: transparent; " +
                        "-fx-focus-color: transparent; " +
                        "-fx-faint-focus-color: transparent; " +
                        "-fx-background-insets: 0; " +
                        "-fx-padding: 0; " +
                        "-fx-background-color: transparent;"
        );
        refreshTreeView(treeView);
        treeListFresh.addListener((_, _, _) -> refreshTreeView(treeView));
        return treeView;
    }

    private static void refreshTreeView(TreeView<QuestEntity> treeView) {
        autoFitTreeWidth(treeView);
        List<QuestEntity> dataList = getQuestTreeData();
        if (dataList.isEmpty()) {
            return;
        }

        // 查找根节点 (parentId为0或null)
        Optional<QuestEntity> rootEntity = dataList.stream()
                .filter(entity -> entity.getParentId() == null || entity.getParentId() == 0)
                .findAny();

        if (rootEntity.isPresent()) {
            // O(n) 构建 parentId → children 索引
            Map<Long, List<QuestEntity>> childrenByParentId = new HashMap<>();
            for (QuestEntity entity : dataList) {
                Long pid = entity.getParentId();
                if (pid != null && pid != 0) {
                    childrenByParentId.computeIfAbsent(pid, k -> new ArrayList<>()).add(entity);
                }
            }

            TreeItem<QuestEntity> root = new TreeItem<>(rootEntity.get());
            buildChildren(root, childrenByParentId);
            treeView.setRoot(root);
        }
    }

    private static <T> void autoFitTreeWidth(TreeView<T> treeView) {
        Runnable fit = () -> Platform.runLater(() -> {
            treeView.applyCss();
            treeView.layout();
            double maxW = treeView.lookupAll(".tree-cell").stream()
                    .mapToDouble(n -> n.prefWidth(-1))
                    .max().orElse(0);
            if (maxW > 0) treeView.setPrefWidth(maxW + 30);
        });

        // 用事件过滤器统一拦截所有节点的展开/折叠，无需递归绑定
//        treeView.addEventFilter(TreeItem.branchExpandedEvent(), e -> fit.run());
//        treeView.addEventFilter(TreeItem.branchCollapsedEvent(), e -> fit.run());
        fit.run(); // 初始计算
    }


    private static void buildChildren(TreeItem<QuestEntity> parent, Map<Long, List<QuestEntity>> childrenByParentId) {
        if (Objects.isNull(parent) || parent.getValue().getId() == null) {
            return;
        }
        List<QuestEntity> children = childrenByParentId.get(parent.getValue().getId());
        if (children == null) {
            return;
        }
        for (QuestEntity entity : children) {
            TreeItem<QuestEntity> childNode = new TreeItem<>(entity);
            parent.getChildren().add(childNode);

            // 给 project 节点设置展开/折叠事件
            if (PROJECT.name().equals(entity.getType())) {
                childNode.expandedProperty().addListener((_, oldValue, newValue) ->
                        updateProjectExpand(entity, newValue));

                // 设置节点展开状态
                String expand = entity.getExpand();
                if (expand != null && !expand.isEmpty()) {
                    ProjectNode projectNode = parseProjectNode(expand);
                    if (projectNode != null && projectNode.getIsExpand() != null) {
                        childNode.setExpanded(projectNode.getIsExpand());
                    }
                }
            }

            buildChildren(childNode, childrenByParentId);
        }
    }

}