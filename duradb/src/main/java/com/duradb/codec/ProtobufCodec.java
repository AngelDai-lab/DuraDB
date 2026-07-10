package com.duradb.codec;

import com.duradb.model.Student;
import com.duradb.model.Course;
import com.duradb.model.Node;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProtobufCodec {

    // ==================== Student ====================
    
    public byte[] serializeStudent(Student student) {
        com.duradb.proto.Student.StudentProto.Builder builder = 
                com.duradb.proto.Student.StudentProto.newBuilder();
        builder.setId(student.getId());
        builder.setName(student.getName());
        builder.setGpa(student.getGpa());
        return builder.build().toByteArray();
    }

    public Student deserializeStudent(byte[] data) throws InvalidProtocolBufferException {
        com.duradb.proto.Student.StudentProto proto = 
                com.duradb.proto.Student.StudentProto.parseFrom(data);
        return new Student(proto.getId(), proto.getName(), proto.getGpa());
    }

    // ==================== Course ====================
    
    public byte[] serializeCourse(Course course) {
        com.duradb.proto.Student.CourseProto.Builder builder = 
                com.duradb.proto.Student.CourseProto.newBuilder();
        builder.setCode(course.getCode());
        builder.setName(course.getName());
        for (Student s : course.getStudents()) {
            com.duradb.proto.Student.StudentProto studentProto = 
                    com.duradb.proto.Student.StudentProto.newBuilder()
                    .setId(s.getId())
                    .setName(s.getName())
                    .setGpa(s.getGpa())
                    .build();
            builder.addStudents(studentProto);
        }
        return builder.build().toByteArray();
    }

    public Course deserializeCourse(byte[] data) throws InvalidProtocolBufferException {
        com.duradb.proto.Student.CourseProto proto = 
                com.duradb.proto.Student.CourseProto.parseFrom(data);
        List<Student> students = new ArrayList<>();
        for (com.duradb.proto.Student.StudentProto sp : proto.getStudentsList()) {
            students.add(new Student(sp.getId(), sp.getName(), sp.getGpa()));
        }
        return new Course(proto.getCode(), proto.getName(), students);
    }

    // ==================== Node ====================
    
    public byte[] serializeNode(Node root) {
    // 1. 收集所有节点（使用 Node 自身的 ID）
    List<Node> allNodes = new ArrayList<>();
    collectNodes(root, allNodes);

    // 2. 用 Node 自身的 ID 建立映射
    Map<Node, Integer> nodeIdMap = new HashMap<>();
    for (Node node : allNodes) {
        nodeIdMap.put(node, node.getId());
    }

    com.duradb.proto.Student.GraphProto.Builder graphBuilder = 
            com.duradb.proto.Student.GraphProto.newBuilder();
    for (Node node : allNodes) {
        com.duradb.proto.Student.NodeProto.Builder nodeBuilder = 
                com.duradb.proto.Student.NodeProto.newBuilder();
        nodeBuilder.setId(node.getId());  // 直接用 Node 自己的 ID
        nodeBuilder.setValue(node.getValue());
        for (Node neighbor : node.getNeighbors()) {
            nodeBuilder.addNeighborIds(neighbor.getId());  // 直接存邻居的 ID
        }
        graphBuilder.addNodes(nodeBuilder.build());
    }
    graphBuilder.setRootId(root.getId());  // 直接用根节点的 ID
    return graphBuilder.build().toByteArray();
}

    private void collectNodes(Node node, List<Node> list) {
        if (list.contains(node)) return;
        list.add(node);
        for (Node neighbor : node.getNeighbors()) {
            collectNodes(neighbor, list);
        }
    }


    public Node deserializeNode(byte[] data) throws InvalidProtocolBufferException {
    com.duradb.proto.Student.GraphProto graph = 
            com.duradb.proto.Student.GraphProto.parseFrom(data);

    List<Node> nodes = new ArrayList<>();
    for (com.duradb.proto.Student.NodeProto np : graph.getNodesList()) {
        Node node = new Node(np.getId(), np.getValue());
        nodes.add(node);
    }

    Map<Integer, Node> nodeMap = new HashMap<>();
    for (Node node : nodes) {
        nodeMap.put(node.getId(), node);
    }

    for (int i = 0; i < graph.getNodesCount(); i++) {
        com.duradb.proto.Student.NodeProto np = graph.getNodes(i);
        Node node = nodes.get(i);
        for (int neighborId : np.getNeighborIdsList()) {
            Node neighbor = nodeMap.get(neighborId);
            if (neighbor != null) {
                node.addNeighbor(neighbor);
            }
        }
    }

    Node result = nodeMap.get(graph.getRootId());
    return result;
}
}