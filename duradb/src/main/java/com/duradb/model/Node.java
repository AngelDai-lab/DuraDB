package com.duradb.model;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

/* 图节点模型 - 测试循环引用
 * Node 包含一个 neighbors 列表，可以形成循环引用：A → B，B → A
* 使用 @JsonIdentityInfo 处理循环引用：
第一次遇到对象时分配一个 ID，后续遇到同一个对象只输出 ID，不再展开。
 */
@JsonIdentityInfo(
    generator = ObjectIdGenerators.PropertyGenerator.class,
    property = "id"
)
public class Node {
    private int id;
    private String value;
    private List<Node> neighbors;

    public Node() {
        this.neighbors = new ArrayList<>();
    }

    public Node(int id, String value) {
        this.id = id;
        this.value = value;
        this.neighbors = new ArrayList<>();
    }

    // ---- Getter and Setter ----
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public List<Node> getNeighbors() { return neighbors; }
    public void setNeighbors(List<Node> neighbors) { this.neighbors = neighbors; }

    public void addNeighbor(Node node) {
        this.neighbors.add(node);
    }

    @Override
    public String toString() {
        // 只打印 id 和 value，避免无限递归打印
        return "Node{id=" + id + ", value='" + value + "', neighbors=" + neighbors.size() + " 个}";
    }
}
