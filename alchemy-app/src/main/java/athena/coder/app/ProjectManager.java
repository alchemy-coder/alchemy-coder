package athena.coder.app;

import athena.coder.ApplicationLauncher;
import athena.coder.ai.rag.RagManager;
import athena.coder.infra.repository.QuestRepository;
import athena.coder.entity.tree.ProjectNode;
import athena.coder.entity.tree.QuestEntity;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static athena.coder.app.AppState.*;
import static athena.coder.app.ChatManager.refreshChatDataByChatId;
import static athena.coder.app.SerializationUtil.deserializeFromString;
import static athena.coder.entity.tree.TreeNodeType.PROJECT;

/**
 * 项目/任务树管理：树节点点击、项目创建、展开状态、项目路径获取。
 */
public class ProjectManager {

    private ProjectManager() {
    }

    /**
     * 树节点点击：项目节点切换当前项目；任务节点加载聊天记录
     */
    public static void itemClick(QuestEntity item) {
        if (item == null) {
            return;
        }

        QuestEntity project;
        if (PROJECT.name().equals(item.getType())) {
            project = item;
            curTaskId.set(0);
            chatList.clear();
            chatModel.set(false);
            indexProjectAsync(item);
        } else {
            project = QuestRepository.findById(item.getParentId());
            if (project == null) {
                return;
            }
            chatModel.setValue(true);
            refreshChatDataByChatId(item.getId());
            curTaskId.set(item.getId());
        }

        QuestRepository.touch(List.of(item.getId(), project.getId()));
        curProjectTitle.setValue(project.getTitle());
        curProject.set(project.getId());
    }

    public static void updateProjectExpand(QuestEntity item, Boolean isExpand) {
        String expand = item.getExpand();
        if (expand == null || expand.isEmpty()) {
            return;
        }
        ProjectNode projectNode = deserializeFromString(expand, ProjectNode.class);
        if (projectNode == null) {
            return;
        }
        projectNode.setIsExpand(isExpand);
        item.setExpand(SerializationUtil.serializeToString(projectNode));
        QuestRepository.updateExpand(item.getId(), item.getExpand());
    }

    public static void createProject() {
        File selectFolder = selectFolder();
        if (selectFolder == null) {
            return;
        }

        ProjectNode projectNode = new ProjectNode();
        projectNode.setAbsoluteFullPath(selectFolder.getAbsolutePath());
        projectNode.setIsExpand(true);

        QuestEntity project = new QuestEntity();
        project.setParentId(-1L);
        project.setTitle(selectFolder.getName());
        project.setType(PROJECT.name());
        project.setExpand(SerializationUtil.serializeToString(projectNode));

        long projectId = QuestRepository.insert(project);
        curProjectTitle.set(project.getTitle());
        curProject.set(projectId);
        treeListFresh.setValue(treeListFresh.getValue() + 1);
        chatModel.setValue(false);
        chatList.clear();
    }

    public static String getProjectPath() {
        Long projectId = curProject.get();
        if (projectId <= 0) {
            return null;
        }
        QuestEntity entity = QuestRepository.findById(projectId);
        if (entity == null || entity.getExpand() == null || entity.getExpand().isBlank()) {
            return null;
        }
        ProjectNode node = deserializeFromString(entity.getExpand(), ProjectNode.class);
        return node != null ? node.getAbsoluteFullPath() : null;
    }

    /**
     * 全部项目/任务树数据，头部追加虚拟 Root 节点
     */
    public static List<QuestEntity> getQuestTreeData() {
        List<QuestEntity> questEntities = QuestRepository.listAll();
        if (questEntities.isEmpty()) {
            return new ArrayList<>();
        }
        QuestEntity root = new QuestEntity();
        root.setId(-1L);
        root.setParentId(0L);
        root.setTitle("Root");
        questEntities.addFirst(root);
        return questEntities;
    }

    /**
     * 项目选中后异步触发 RAG 索引（失败静默，不影响主流程）
     */
    private static void indexProjectAsync(QuestEntity project) {
        String expand = project.getExpand();
        if (expand == null || expand.isBlank()) {
            return;
        }
        ProjectNode node = deserializeFromString(expand, ProjectNode.class);
        if (node != null && node.getAbsoluteFullPath() != null) {
            RagManager.getInstance().indexAsync(node.getAbsoluteFullPath());
        }
    }

    /**
     * 弹出文件夹选择对话框
     */
    private static File selectFolder() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("选择文件夹");
        directoryChooser.setInitialDirectory(new File(System.getProperty("user.home")));
        return directoryChooser.showDialog(ApplicationLauncher.primaryStage);
    }
}
