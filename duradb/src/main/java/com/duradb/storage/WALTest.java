package com.duradb.storage;

import com.duradb.model.Student;
import com.duradb.codec.BinaryCodec;

import java.nio.file.Files;
import java.nio.file.Paths;

public class WALTest {
    public static void main(String[] args) throws Exception {
        
         // ==================== 清理旧文件 ====================
        System.out.println("=== WAL 测试 ===\n");
        Files.deleteIfExists(Paths.get("data/duradb.db"));
        Files.deleteIfExists(Paths.get("data/duradb.wal"));
        
        // 确保 data 目录存在
        java.nio.file.Path dataDir = Paths.get("data");
        if (!Files.exists(dataDir)) {
            Files.createDirectories(dataDir);
        }
        
        // ==================== 初始化 ====================
        // 1. 创建 PageManager
        PageManager pm = new PageManager("data/duradb.db");
        
        // 2. 创建 WALManager
        WALManager wal = new WALManager("data/duradb.wal");
        
        // 3. 创建序列化器
        BinaryCodec codec = new BinaryCodec();
        
        // 4. 插入 5 个学生（先写 WAL，再写数据）
        System.out.println("\n--- 插入 5 个学生 ---");
        for (int i = 1; i <= 5; i++) {
            Student s = new Student(i, "Student_" + i, 3.0 + i * 0.1);
            byte[] data = codec.serializeStudent(s);
            
            // 先写 WAL
            WALEntry entry = new WALEntry(WALEntry.OP_INSERT, i-1, data);
            wal.writeEntry(entry);
            
            // 再写数据
            RecordId rid = pm.insert(data);
            System.out.println("插入: " + s + " → " + rid);
        }
        
        // 5. 打印统计
        pm.printStats();
        System.out.println("WAL 大小: " + wal.getLogSize() + " 字节");
        
        // 6. 模拟崩溃恢复
        System.out.println("\n--- 模拟崩溃重启 ---");
        
        // 关闭所有资源
        pm.close();
        wal.close();
        
        // 重新打开
        System.out.println("\n--- 重新打开 PageManager + WALManager ---");

        PageManager pm2 = new PageManager("data/duradb.db");
        WALManager wal2 = new WALManager("data/duradb.wal");
        
        // 恢复数据
        wal2.recover(pm2);
        
        // 验证数据
        System.out.println("\n--- 验证数据 ---");
        for (int i = 1; i <= 5; i++) {
            RecordId rid = new RecordId(0, i - 1);
            byte[] data = pm2.read(rid);
            if (data != null) {
                Student s = codec.deserializeStudent(data);
                System.out.println("读取: " + s);
            }
        }
        
        pm2.printStats();
        
        // Checkpoint
        wal2.checkpoint();
        
        pm2.close();
        wal2.close();
        
        System.out.println("\nWAL 测试完成！");
    }
}