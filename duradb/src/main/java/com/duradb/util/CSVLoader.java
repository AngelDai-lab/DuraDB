package com.duradb.util;

import com.duradb.model.Student;
import com.duradb.codec.BinaryCodec;
import com.duradb.storage.PageManager;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/* CSV 数据加载器（逐条插入）
 * 从 CSV 文件读取学生数据，批量插入 DuraDB
 * CSV 格式：id,name,gpa
 */
public class CSVLoader {

    /**
     * 从 CSV 文件加载数据并插入 DuraDB
     * 
     * @param csvPath CSV 文件路径
     * @param dbPath  DuraDB 数据文件路径
     * @return 插入记录数
     */
    public static int loadFromCSV(String csvPath, String dbPath) throws Exception {
        System.out.println(" 读取 CSV 文件: " + csvPath);
        
        List<Student> students = readCSV(csvPath);
        System.out.println("  读取到 " + students.size() + " 条记录");
        
        // 创建 DuraDB PageManager
        PageManager pm = new PageManager(dbPath);
        BinaryCodec codec = new BinaryCodec();
        
        long start = System.currentTimeMillis();
        
        int count = 0;
        for (Student s : students) {
            byte[] data = codec.serializeStudent(s);
            pm.insert(data);
            count++;
            
            // 每 100 条打印进度
            if (count % 100 == 0) {
                System.out.println("  已插入: " + count + " 条");
            }
        }
        
        long elapsed = System.currentTimeMillis() - start;
        pm.close();
        
        System.out.println("  插入完成: " + count + " 条, 耗时 " + elapsed + " ms");
        System.out.println("  数据文件: " + dbPath);
        System.out.println("  文件大小: " + Files.size(Paths.get(dbPath)) + " 字节");
        
        return count;
    }
    
    /* 读取 CSV 文件 */
    private static List<Student> readCSV(String csvPath) throws Exception {
        List<Student> students = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(csvPath))) {
            // 读取表头（跳过）
            String header = reader.readLine();
            if (header == null) {
                System.out.println("   CSV 文件为空");
                return students;
            }
            System.out.println("  表头: " + header);
            
            String line;
            int lineNum = 1;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                
                // 跳过空行
                if (line.trim().isEmpty()) {
                    continue;
                }
                
                String[] parts = line.split(",");
                if (parts.length < 3) {
                    System.out.println("   第 " + lineNum + " 行格式错误，跳过: " + line);
                    continue;
                }
                
                try {
                    int id = Integer.parseInt(parts[0].trim());
                    String name = parts[1].trim();
                    
                    // 跳过表头行（如果 CSV 没有表头，或者表头被误读）
                    if (name.equalsIgnoreCase("name") || name.equalsIgnoreCase("姓名")) {
                        System.out.println("   第 " + lineNum + " 行是表头，跳过");
                        continue;
                    }
                    
                    double gpa = Double.parseDouble(parts[2].trim());
                    
                    // 验证数据有效性
                    if (id <= 0 || name.isEmpty() || gpa < 0 || gpa > 5.0) {
                        System.out.println("   第 " + lineNum + " 行数据无效，跳过: " + line);
                        continue;
                    }
                    
                    students.add(new Student(id, name, gpa));
                } catch (NumberFormatException e) {
                    System.out.println("   第 " + lineNum + " 行数据格式错误，跳过: " + line);
                }
            }
        }
        
        return students;
    }
    
    /* 生成测试 CSV 文件 */
    public static void generateSampleCSV(String csvPath, int count) throws Exception {
        try (java.io.PrintWriter writer = new java.io.PrintWriter(csvPath)) {
            writer.println("id,name,gpa");
            for (int i = 1; i <= count; i++) {
                double gpa = 2.0 + Math.random() * 3.0;
                writer.println(i + ",Student_" + i + "," + String.format("%.2f", gpa));
            }
        }
        System.out.println(" 生成测试 CSV 文件: " + csvPath + " (" + count + " 条)");
    }
}