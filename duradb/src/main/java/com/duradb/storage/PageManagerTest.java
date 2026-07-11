package com.duradb.storage;

import com.duradb.model.Student;
import com.duradb.codec.BinaryCodec;

public class PageManagerTest {
    public static void main(String[] args) throws Exception {
        
        // 先删除旧文件，保证测试环境干净
        java.nio.file.Path path = java.nio.file.Paths.get("test.dat");
        if (java.nio.file.Files.exists(path)) {
            java.nio.file.Files.delete(path);
            System.out.println("已删除旧文件: test.dat");
        }
        
        
        System.out.println("=== PageManager 测试 ===\n");
        
        // 1. 创建 PageManager
        PageManager pm = new PageManager("test.dat");
        
        // 2. 创建序列化器
        BinaryCodec codec = new BinaryCodec();
        
        // 3. 插入 10 个学生
        System.out.println("\n--- 插入 10 个学生 ---");
        for (int i = 1; i <= 10; i++) {
            Student s = new Student(i, "Student_" + i, 3.0 + i * 0.1);
            byte[] data = codec.serializeStudent(s);
            RecordId rid = pm.insert(data);
            System.out.println("插入: " + s + " → " + rid);
        }
        
        // 4. 打印统计
        pm.printStats();
        
        // 5. 读取第 5 个学生
        System.out.println("\n--- 读取第 5 个学生 ---");
        RecordId rid = new RecordId(0, 4);  // 第 0 页的第 4 个槽
        byte[] data = pm.read(rid);
        Student s = codec.deserializeStudent(data);
        System.out.println("读取到: " + s);
        
        // 6. 删除第 5 个学生
        System.out.println("\n--- 删除第 5 个学生 ---");
        pm.delete(rid);
        
        // 7. 验证删除
        byte[] deleted = pm.read(rid);
        System.out.println("删除后读取: " + (deleted == null ? "null（已删除）" : "错误！"));
        
        // 8. 再插一个学生，看是否复用空间
        System.out.println("\n--- 再插一个学生 ---");
        Student newS = new Student(99, "NewStudent", 4.5);
        byte[] newData = codec.serializeStudent(newS);
        RecordId newRid = pm.insert(newData);
        System.out.println("插入新学生: " + newS + " → " + newRid);
        
        // 9. 打印最终统计
        pm.printStats();
        
        // 10. 关闭
        pm.close();
        System.out.println("\nPageManager 测试完成！");
    }
}