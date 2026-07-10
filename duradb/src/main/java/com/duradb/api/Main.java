package com.duradb.api;

import com.duradb.model.Student;
import com.duradb.model.Course;
import com.duradb.model.Node;
import com.duradb.codec.BinaryCodec;
import com.duradb.codec.JsonCodec;
import com.duradb.codec.ProtobufCodec;
import java.util.Arrays;

public class Main {
    public static void main(String[] args) throws Exception {

        System.out.println("========================================");
        System.out.println("       DuraDB 序列化格式对比");
        System.out.println("========================================");
        System.out.println();

        // ========== 准备测试数据 ==========
        Student student = new Student(1001, "Zhang San", 3.85);

        Course course = new Course("CS101", "数据结构", Arrays.asList(
            new Student(1, "Alice", 3.8),
            new Student(2, "Bob", 3.5),
            new Student(3, "Charlie", 3.9)
        ));

        Node nodeA = new Node(1, "A");
        Node nodeB = new Node(2, "B");
        nodeA.addNeighbor(nodeB);
        nodeB.addNeighbor(nodeA);

        System.out.println("【测试数据】");
        System.out.println("  Student: " + student);
        System.out.println("  Course:  " + course);
        System.out.println("  Node:    " + nodeA);
        System.out.println();

        // ========== 1. BinaryCodec ==========
        System.out.println("----------------------------------------");
        System.out.println("【BinaryCodec】自定义二进制格式");
        System.out.println("----------------------------------------");
        testBinary(student, course, nodeA); 

        // ========== 2. JsonCodec ==========
        System.out.println("----------------------------------------");
        System.out.println("【JsonCodec】JSON 文本格式");
        System.out.println("----------------------------------------");
        testJson(student, course, nodeA);

        // ========== 3. ProtobufCodec ==========
        System.out.println("----------------------------------------");
        System.out.println("【ProtobufCodec】Protocol Buffers");
        System.out.println("----------------------------------------");
        testProtobuf(student, course, nodeA);

        System.out.println();
    }

    // ============================================================
    // 1. BinaryCodec 测试（目前只支持 Student）
    // ============================================================
    private static void testBinary(Student student, Course course, Node node) throws Exception {
    BinaryCodec codec = new BinaryCodec();

    // ---- Student ----
    byte[] sData = codec.serializeStudent(student);
    Student sRestored = codec.deserializeStudent(sData);
    System.out.println("  Student  → " + sData.length + " 字节");
    System.out.println("  恢复: " + sRestored);
    System.out.println("  结果: " + (student.getId() == sRestored.getId()
            && student.getName().equals(sRestored.getName())
            && Double.compare(student.getGpa(), sRestored.getGpa()) == 0 ? "一致" : "失败"));
    System.out.println();

    // ---- Course ----
    byte[] cData = codec.serializeCourse(course);
    Course cRestored = codec.deserializeCourse(cData);
    System.out.println("  Course   → " + cData.length + " 字节");
    System.out.println("  恢复: " + cRestored);
    boolean courseOk = course.getCode().equals(cRestored.getCode())
            && course.getName().equals(cRestored.getName())
            && course.getStudents().size() == cRestored.getStudents().size();
    System.out.println("  结果: " + (courseOk ? "一致" : "失败"));
    System.out.println();

    // ---- Node ----
    byte[] nData = codec.serializeNode(node);
    Node nRestored = codec.deserializeNode(nData);
    System.out.println("  Node     → " + nData.length + " 字节");
    System.out.println("  恢复: " + nRestored);
    boolean nodeOk = node.getId() == nRestored.getId()
            && node.getValue().equals(nRestored.getValue())
            && node.getNeighbors().size() == nRestored.getNeighbors().size();
    System.out.println("  结果: " + (nodeOk ? "一致" : "失败"));
    System.out.println();
}

    // ============================================================
    // 2. JsonCodec 测试（支持所有类型）
    // ============================================================
    private static void testJson(Student student, Course course, Node node) throws Exception {
        JsonCodec codec = new JsonCodec();

        // ---- Student ----
        byte[] sData = codec.serialize(student);
        Student sRestored = codec.deserialize(sData, Student.class);
        System.out.println("  Student  → " + sData.length + " 字节");
        System.out.println("  恢复: " + sRestored);
        System.out.println("  结果: " + (student.getId() == sRestored.getId()
                && student.getName().equals(sRestored.getName())
                && Double.compare(student.getGpa(), sRestored.getGpa()) == 0 ? "一致" : "失败"));
        System.out.println();

        // ---- Course ----
        byte[] cData = codec.serialize(course);
        Course cRestored = codec.deserialize(cData, Course.class);
        System.out.println("  Course   → " + cData.length + " 字节");
        System.out.println("  恢复: " + cRestored);
        boolean courseOk = course.getCode().equals(cRestored.getCode())
                && course.getName().equals(cRestored.getName())
                && course.getStudents().size() == cRestored.getStudents().size();
        System.out.println("  结果: " + (courseOk ? "一致" : "失败"));
        System.out.println();

        // ---- Node ----
        byte[] nData = codec.serialize(node);
        Node nRestored = codec.deserialize(nData, Node.class);
        System.out.println("  Node     → " + nData.length + " 字节");
        System.out.println("  恢复: " + nRestored);
        boolean nodeOk = node.getId() == nRestored.getId()
                && node.getValue().equals(nRestored.getValue())
                && node.getNeighbors().size() == nRestored.getNeighbors().size();
        System.out.println("  结果: " + (nodeOk ? "一致" : "失败"));
        System.out.println();
    }

    // ============================================================
    // 3. ProtobufCodec 测试（支持所有类型）
    // ============================================================
    private static void testProtobuf(Student student, Course course, Node node) throws Exception {
        ProtobufCodec codec = new ProtobufCodec();

        // ---- Student ----
        byte[] sData = codec.serializeStudent(student);
        Student sRestored = codec.deserializeStudent(sData);
        System.out.println("  Student  → " + sData.length + " 字节");
        System.out.println("  恢复: " + sRestored);
        System.out.println("  结果: " + (student.getId() == sRestored.getId()
                && student.getName().equals(sRestored.getName())
                && Double.compare(student.getGpa(), sRestored.getGpa()) == 0 ? "一致" : "失败"));
        System.out.println();

        // ---- Course ----
        byte[] cData = codec.serializeCourse(course);
        Course cRestored = codec.deserializeCourse(cData);
        System.out.println("  Course   → " + cData.length + " 字节");
        System.out.println("  恢复: " + cRestored);
        boolean courseOk = course.getCode().equals(cRestored.getCode())
                && course.getName().equals(cRestored.getName())
                && course.getStudents().size() == cRestored.getStudents().size();
        System.out.println("  结果: " + (courseOk ? "一致" : "失败"));
        System.out.println();

        // ---- Node ----
        byte[] nData = codec.serializeNode(node);
        Node nRestored = codec.deserializeNode(nData);
        System.out.println("  Node     → " + nData.length + " 字节");
        System.out.println("  恢复: " + nRestored);
        boolean nodeOk = node.getId() == nRestored.getId()
                && node.getValue().equals(nRestored.getValue())
                && node.getNeighbors().size() == nRestored.getNeighbors().size();
        System.out.println("  结果: " + (nodeOk ? "一致" : "失败"));
        System.out.println();
    }
}