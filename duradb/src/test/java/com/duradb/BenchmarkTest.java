package com.duradb;


import com.duradb.model.Student;
import com.duradb.codec.BinaryCodec;
import com.duradb.codec.JsonCodec;
import com.duradb.codec.ProtobufCodec;
import com.duradb.storage.PageManager;
import com.duradb.storage.WALManager;
import com.duradb.storage.WALEntry;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/* DuraDB 性能基准测试 */
public class BenchmarkTest {

    private static final int[] SIZES = {100, 1000, 5000};
    private static final String RESULT_FILE = "results/benchmark_results.csv";

    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║              DuraDB 性能基准测试                            ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        new File("results").mkdirs();

        try (PrintWriter writer = new PrintWriter(new FileWriter(RESULT_FILE))) {
            writer.println("数据量,格式,写入耗时(ms),文件大小(bytes)");
        }

        System.out.println(" 准备测试数据...");
        List<Student> students = generateTestData(5000);
        System.out.println(" 测试数据已生成 (" + students.size() + " 条)");
        System.out.println();

        for (int size : SIZES) {
            System.out.println("════════════════════════════════════════════════════════════");
            System.out.println("  测试数据量: " + size + " 条");
            System.out.println("════════════════════════════════════════════════════════════");

            List<Student> testData = students.subList(0, size);

            testBinary(testData, size);
            testJson(testData, size);
            testProtobuf(testData, size);
            testBinaryWithWAL(testData, size);

            System.out.println();
        }

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║            所有测试完成！结果已保存到 results/              ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
    }

    private static List<Student> generateTestData(int count) {
        List<Student> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            list.add(new Student(i, "Student_" + i, 2.0 + (i % 30) * 0.1));
        }
        return list;
    }

    // ==================== Binary ====================

    private static void testBinary(List<Student> data, int size) throws Exception {
        BinaryCodec codec = new BinaryCodec();
        String filePath = "data/benchmark_binary_" + size + ".dat";

        // 清理旧文件
        Files.deleteIfExists(Paths.get(filePath));

        PageManager pm = new PageManager(filePath);

        long start = System.currentTimeMillis();
        for (Student s : data) {
            byte[] bytes = codec.serializeStudent(s);
            pm.insert(bytes);
        }
        long writeTime = System.currentTimeMillis() - start;

        long fileSize = new File(filePath).length();
        pm.close();

        System.out.println("   Binary      : 写入 " + writeTime + "ms, " + fileSize + " 字节");
        appendResult(size, "Binary", writeTime, fileSize);

        Files.deleteIfExists(Paths.get(filePath));
    }

    // ==================== JSON ====================

    private static void testJson(List<Student> data, int size) throws Exception {
        JsonCodec codec = new JsonCodec();
        String filePath = "data/benchmark_json_" + size + ".dat";

        Files.deleteIfExists(Paths.get(filePath));

        PageManager pm = new PageManager(filePath);

        long start = System.currentTimeMillis();
        for (Student s : data) {
            byte[] bytes = codec.serialize(s);
            pm.insert(bytes);
        }
        long writeTime = System.currentTimeMillis() - start;

        long fileSize = new File(filePath).length();
        pm.close();

        System.out.println("   JSON        : 写入 " + writeTime + "ms, " + fileSize + " 字节");
        appendResult(size, "JSON", writeTime, fileSize);

        Files.deleteIfExists(Paths.get(filePath));
    }

    // ==================== Protobuf ====================

    private static void testProtobuf(List<Student> data, int size) throws Exception {
        ProtobufCodec codec = new ProtobufCodec();
        String filePath = "data/benchmark_protobuf_" + size + ".dat";

        Files.deleteIfExists(Paths.get(filePath));

        PageManager pm = new PageManager(filePath);

        long start = System.currentTimeMillis();
        for (Student s : data) {
            byte[] bytes = codec.serializeStudent(s);
            pm.insert(bytes);
        }
        long writeTime = System.currentTimeMillis() - start;

        long fileSize = new File(filePath).length();
        pm.close();

        System.out.println("   Protobuf   : 写入 " + writeTime + "ms, " + fileSize + " 字节");
        appendResult(size, "Protobuf", writeTime, fileSize);

        Files.deleteIfExists(Paths.get(filePath));
    }

    // ==================== Binary + WAL ====================

    private static void testBinaryWithWAL(List<Student> data, int size) throws Exception {
        BinaryCodec codec = new BinaryCodec();
        String dataFile = "data/benchmark_wal_" + size + ".dat";
        String walFile = "data/benchmark_wal_" + size + ".wal";

        Files.deleteIfExists(Paths.get(dataFile));
        Files.deleteIfExists(Paths.get(walFile));

        PageManager pm = new PageManager(dataFile);
        WALManager wal = new WALManager(walFile);

        long start = System.currentTimeMillis();
        for (int i = 0; i < data.size(); i++) {
            Student s = data.get(i);
            byte[] bytes = codec.serializeStudent(s);
            WALEntry entry = new WALEntry(WALEntry.OP_INSERT, i, bytes);
            wal.writeEntry(entry);
            pm.insert(bytes);
        }
        long writeTime = System.currentTimeMillis() - start;

        long fileSize = new File(dataFile).length();
        pm.close();
        wal.close();

        System.out.println("   Binary+WAL : 写入 " + writeTime + "ms, " + fileSize + " 字节");
        appendResult(size, "Binary+WAL", writeTime, fileSize);

        Files.deleteIfExists(Paths.get(dataFile));
        Files.deleteIfExists(Paths.get(walFile));
    }

    private static void appendResult(int size, String format, long writeTime, long fileSize) throws Exception {
        try (PrintWriter writer = new PrintWriter(new FileWriter(RESULT_FILE, true))) {
            writer.println(size + "," + format + "," + writeTime + "," + fileSize);
        }
    }
}