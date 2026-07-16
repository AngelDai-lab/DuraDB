package com.duradb.index;

import com.duradb.model.Student;
import com.duradb.codec.BinaryCodec;
import com.duradb.storage.PageManager;


import java.nio.file.Files;
import java.nio.file.Paths;

public class BPlusTreeTest {
    public static void main(String[] args) throws Exception {
        System.out.println("=== B+树索引测试 ===\n");

        Files.deleteIfExists(Paths.get("data/bplustree_test.dat"));

        PageManager pm = new PageManager("data/bplustree_test.dat");
        BinaryCodec codec = new BinaryCodec();

        System.out.println("总页数: " + pm.getTotalPages());
        // ⭐ 改成 getTotalPages()
        System.out.println("B+树: " + (pm.getBPlusTree() != null ? "✅" : "❌"));

        System.out.println("\n--- 插入 50 条数据 ---");
        for (int i = 1; i <= 50; i++) {
            int id = i * 2;
            Student s = new Student(id, "Student_" + id, 3.0 + (i % 30) * 0.1);
            byte[] data = codec.serializeStudent(s);
            pm.insert(data);
        }
        System.out.println("插入完成！");

        System.out.println("\n--- 按 id 查询 ---");
        int[] testIds = {10, 50, 100, 99};
        for (int id : testIds) {
            byte[] result = pm.selectById(id);
            if (result != null) {
                Student s = codec.deserializeStudent(result);
                System.out.println("  id=" + id + " → " + s);
            } else {
                System.out.println("  id=" + id + " → 未找到");
            }
        }

        System.out.println("\n--- B+树结构 ---");
        if (pm.getBPlusTree() != null) {
            pm.getBPlusTree().printTree();
            System.out.println("索引大小: " + pm.getBPlusTree().size());
        }

        System.out.println("\n--- 统计 ---");
        pm.printStats();

        pm.close();
        System.out.println("\n测试完成！");
    }
}