package athena.coder.ui.leftmenu;

import athena.coder.entity.model.LLMModelEnum;
import athena.coder.entity.model.ModelType;
import athena.coder.entity.tree.ProjectNode;
import athena.coder.entity.tree.QuestEntity;
import athena.coder.infra.repository.SqliteModelConfig;
import athena.coder.ui.modelselect.ModelConfigDialog;
import athena.coder.ui.modelselect.ModelSelectView;
import atlantafx.base.theme.Styles;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
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

    private static final String MENU_BTN_STYLE =
            "-fx-background-color: #F3F4F6;" +
            "-fx-text-fill: #374151;" +
            "-fx-font-size: 13px;" +
            "-fx-padding: 8 14 8 14;" +
            "-fx-background-radius: 8;" +
            "-fx-cursor: hand;";

    private static final String MENU_BTN_HOVER_STYLE =
            "-fx-background-color: #E5E7EB;" +
            "-fx-text-fill: #111827;" +
            "-fx-font-size: 13px;" +
            "-fx-padding: 8 14 8 14;" +
            "-fx-background-radius: 8;" +
            "-fx-cursor: hand;";

    public static Button newCreateQuestButton() {
        FontIcon icon = new FontIcon(Feather.PLUS);
        icon.setIconColor(Color.web("#6B7280"));
        icon.setIconSize(16);

        Button createQuestButton = new Button("创建Quest", icon);
        createQuestButton.setStyle(MENU_BTN_STYLE);
        createQuestButton.setMaxWidth(Double.MAX_VALUE);
        createQuestButton.setAlignment(Pos.CENTER_LEFT);
        createQuestButton.setOnMouseEntered(e -> {
            createQuestButton.setStyle(MENU_BTN_HOVER_STYLE);
            icon.setIconColor(Color.web("#374151"));
        });
        createQuestButton.setOnMouseExited(e -> {
            createQuestButton.setStyle(MENU_BTN_STYLE);
            icon.setIconColor(Color.web("#6B7280"));
        });
        createQuestButton.setOnAction(_ -> {
            chatModel.setValue(false);
            chatList.clear();
            curTaskId.setValue(0);
        });
        return createQuestButton;
    }

    public static Button newSelectModelButton() {
        FontIcon icon = new FontIcon(Feather.ZAP);
        icon.setIconColor(Color.web("#6B7280"));
        icon.setIconSize(16);

        String btnText = "选择大模型";
        SqliteModelConfig modelConfig = new SqliteModelConfig();
        String[] defaultLlm = modelConfig.findDefaultModel(ModelType.CHAT);
        if (defaultLlm != null) {
            LLMModelEnum llmModel = LLMModelEnum.fromNameVersion(defaultLlm[0], defaultLlm[1]);
            if (llmModel != null) {
                btnText = ModelSelectView.formatModelName(llmModel);
            }
        }

        Button selectModel = new Button(btnText, icon);
        selectModel.setStyle(MENU_BTN_STYLE);
        selectModel.setMaxWidth(Double.MAX_VALUE);
        selectModel.setAlignment(Pos.CENTER_LEFT);
        selectModel.setOnMouseEntered(e -> {
            selectModel.setStyle(MENU_BTN_HOVER_STYLE);
            icon.setIconColor(Color.web("#374151"));
        });
        selectModel.setOnMouseExited(e -> {
            selectModel.setStyle(MENU_BTN_STYLE);
            icon.setIconColor(Color.web("#6B7280"));
        });

        selectModel.setOnAction(e -> {
            ModelConfigDialog dialog = new ModelConfigDialog();
            dialog.show((Stage) selectModel.getScene().getWindow(),
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