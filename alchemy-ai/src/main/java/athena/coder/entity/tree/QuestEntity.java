package athena.coder.entity.tree;

import java.io.Serializable;

public class QuestEntity implements Serializable {
    private Long id;
    /**
     * parentId是0的时候，是根节点
     */
    private Long parentId;

    private String title;

    /**
     * 类型PROJECT / TASK
     */
    private String type;

    private String expand;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getExpand() {
        return expand;
    }

    public void setExpand(String expand) {
        this.expand = expand;
    }
}
