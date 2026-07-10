package com.duradb.codec;

import com.duradb.model.Student;
import com.duradb.model.Course;
import com.duradb.model.Node;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/* 自定义二进制序列化器*/

public class BinaryCodec {

    // ==================== Student ====================
/* * 将 Student 对象序列化为紧凑的二进制格式，并能反序列化回来。
 * 二进制布局：
 * +--------+----------+--------+--------+--------+--------+
 * | id     | nameLen  | name   | name   | ...    | gpa    |
 * | (4B)   | (1B)     | (变长) | (变长)  |        | (8B)   |
 * +--------+----------+--------+--------+--------+--------+
 *  字节0-3    字节4     字节5..                    末尾-8..末尾-1 */

    public byte[] serializeStudent(Student student) {
        byte[] nameBytes = student.getName().getBytes(StandardCharsets.UTF_8);
        // id(4) + nameLen(1) + name(变长) + gpa(8)
        int totalLen = 4 + 1 + nameBytes.length + 8;
        ByteBuffer buf = ByteBuffer.allocate(totalLen).order(ByteOrder.BIG_ENDIAN);
        buf.putInt(student.getId());
        buf.put((byte) nameBytes.length);
        buf.put(nameBytes);
        buf.putDouble(student.getGpa());
        return buf.array();
    }

    public Student deserializeStudent(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        int id = buf.getInt();
        byte nameLen = buf.get();
        byte[] nameBytes = new byte[nameLen];
        buf.get(nameBytes);
        String name = new String(nameBytes, StandardCharsets.UTF_8);
        double gpa = buf.getDouble();
        return new Student(id, name, gpa);
    }

    // ==================== Course（嵌套对象） ====================

    public byte[] serializeCourse(Course course) {
        // 1. 先序列化所有 Student
        List<byte[]> studentDataList = new ArrayList<>();
        int totalLen = 0;

        // code 和 name 先算长度
        byte[] codeBytes = course.getCode().getBytes(StandardCharsets.UTF_8);
        byte[] nameBytes = course.getName().getBytes(StandardCharsets.UTF_8);

        for (Student s : course.getStudents()) {
            byte[] sData = serializeStudent(s);
            studentDataList.add(sData);
            totalLen += sData.length;
        }

        // 2. 总长度 = code(长度前缀+数据) + name(长度前缀+数据) + studentCount(4) + 所有Student数据
        int codeLen = 1 + codeBytes.length;
        int nameLen = 1 + nameBytes.length;
        int finalLen = codeLen + nameLen + 4 + totalLen;

        ByteBuffer buf = ByteBuffer.allocate(finalLen).order(ByteOrder.BIG_ENDIAN);

        // code
        buf.put((byte) codeBytes.length);
        buf.put(codeBytes);

        // name
        buf.put((byte) nameBytes.length);
        buf.put(nameBytes);

        // student 数量
        buf.putInt(course.getStudents().size());

        // 每个 Student 的数据
        for (byte[] sData : studentDataList) {
            buf.put(sData);
        }

        return buf.array();
    }

    public Course deserializeCourse(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);

        // 读取 code
        byte codeLen = buf.get();
        byte[] codeBytes = new byte[codeLen];
        buf.get(codeBytes);
        String code = new String(codeBytes, StandardCharsets.UTF_8);

        // 读取 name
        byte nameLen = buf.get();
        byte[] nameBytes = new byte[nameLen];
        buf.get(nameBytes);
        String name = new String(nameBytes, StandardCharsets.UTF_8);

        // 读取 student 数量
        int studentCount = buf.getInt();

        // 读取每个 Student
        List<Student> students = new ArrayList<>();
        for (int i = 0; i < studentCount; i++) {
            // 计算当前 Student 数据的长度
            // 简单方式：先读 id(4) + nameLen(1) + name(变长) + gpa(8)
            int id = buf.getInt();
            byte sNameLen = buf.get();
            byte[] sNameBytes = new byte[sNameLen];
            buf.get(sNameBytes);
            String sName = new String(sNameBytes, StandardCharsets.UTF_8);
            double gpa = buf.getDouble();
            students.add(new Student(id, sName, gpa));
        }

        return new Course(code, name, students);
    }

    // ==================== Node（图结构 + 循环引用） ====================

    /**
     * 序列化 Node（支持循环引用）策略：DFS 遍历所有节点，分配唯一 ID，邻居列表只存 ID
     */
    public byte[] serializeNode(Node root) {
        // 1. 收集所有节点，分配 ID
        Map<Node, Integer> nodeIdMap = new HashMap<>();
        List<Node> allNodes = new ArrayList<>();
        collectNodes(root, nodeIdMap, allNodes);

        // 2. 先计算总长度
        int totalLen = 4; // 节点数量
        List<byte[]> nodeDataList = new ArrayList<>();

        for (Node node : allNodes) {
            byte[] valueBytes = node.getValue().getBytes(StandardCharsets.UTF_8);
            List<Integer> neighborIds = new ArrayList<>();
            for (Node neighbor : node.getNeighbors()) {
                neighborIds.add(nodeIdMap.get(neighbor));
            }

            // 每个节点的格式：id(4) + valueLen(1) + value(变长) + neighborCount(4) + neighborIds(4 * N)
            int nodeLen = 4 + 1 + valueBytes.length + 4 + neighborIds.size() * 4;
            ByteBuffer nodeBuf = ByteBuffer.allocate(nodeLen).order(ByteOrder.BIG_ENDIAN);
            nodeBuf.putInt(node.getId());  // 用 Node 自己的 ID，不是重新分配的
            nodeBuf.put((byte) valueBytes.length);
            nodeBuf.put(valueBytes);
            nodeBuf.putInt(neighborIds.size());
            for (int nid : neighborIds) {
                nodeBuf.putInt(nid);
            }
            nodeDataList.add(nodeBuf.array());
            totalLen += nodeLen;
        }

        // 3. 组装最终数据
        ByteBuffer result = ByteBuffer.allocate(totalLen).order(ByteOrder.BIG_ENDIAN);
        result.putInt(allNodes.size());
        for (byte[] nd : nodeDataList) {
            result.put(nd);
        }

        return result.array();
    }

    private void collectNodes(Node node, Map<Node, Integer> map, List<Node> list) {
        if (map.containsKey(node)) return;
        map.put(node, list.size());
        list.add(node);
        for (Node neighbor : node.getNeighbors()) {
            collectNodes(neighbor, map, list);
        }
    }

    /**
     * 反序列化 Node（恢复循环引用）
     */
    public Node deserializeNode(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);

        int nodeCount = buf.getInt();
        List<Node> nodes = new ArrayList<>();
        List<int[]> neighborIdLists = new ArrayList<>();

        // 1. 第一遍：读取所有节点的基本信息和邻居ID列表
        for (int i = 0; i < nodeCount; i++) {
            int id = buf.getInt();           // 读取 Node 自己的 ID
            byte valueLen = buf.get();
            byte[] valueBytes = new byte[valueLen];
            buf.get(valueBytes);
            String value = new String(valueBytes, StandardCharsets.UTF_8);
            Node node = new Node(id, value); // 用读取到的 ID 创建 Node
            nodes.add(node);

            int neighborCount = buf.getInt();
            int[] neighborIds = new int[neighborCount];
            for (int j = 0; j < neighborCount; j++) {
                neighborIds[j] = buf.getInt();
            }
            neighborIdLists.add(neighborIds);
        }

        // 2. 建立 ID → Node 映射
        Map<Integer, Node> nodeMap = new HashMap<>();
        for (Node node : nodes) {
            nodeMap.put(node.getId(), node);
        }

        // 3. 重建邻居关系
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            int[] neighborIds = neighborIdLists.get(i);
            for (int neighborId : neighborIds) {
                Node neighbor = nodeMap.get(neighborId);
                if (neighbor != null) {
                    node.addNeighbor(neighbor);
                }
            }
        }

        return nodes.isEmpty() ? null : nodes.get(0);
    }


    // ==================== 通用接口（兼容旧代码） ====================

    /**
     * 通用序列化（根据对象类型自动选择）
     */
    public byte[] serialize(Object obj) throws Exception {
        if (obj instanceof Student) {
            return serializeStudent((Student) obj);
        } else if (obj instanceof Course) {
            return serializeCourse((Course) obj);
        } else if (obj instanceof Node) {
            return serializeNode((Node) obj);
        } else {
            throw new IllegalArgumentException("不支持的类型: " + obj.getClass());
        }
    }

}