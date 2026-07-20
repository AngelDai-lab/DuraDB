package com.duradb.util;

import java.nio.file.Files;
import java.nio.file.Paths;

/*CSV 加载测试*/
public class CSVGenerator {
    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║              CSV 数据加载测试                                ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        // 清理旧文件
        Files.deleteIfExists(Paths.get("data/csv_test.dat"));
        Files.deleteIfExists(Paths.get("data/students.csv"));
        Files.deleteIfExists(Paths.get("data/restored.csv"));

        System.out.println("=== 逐行存储 ===");
        
        // 1. 生成测试 CSV
        CSVLoader.generateSampleCSV("data/students.csv", 180);
        
        // 2. 加载到 DuraDB
        int count = CSVLoader.loadFromCSV("data/students.csv", "data/csv_test.dat");
        
        System.out.println();
        
    }
}