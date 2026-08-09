package com.duradb;

import com.duradb.model.Student;
import com.duradb.model.Course;
import com.duradb.model.Node;
import com.duradb.codec.BinaryCodec;
import com.duradb.codec.JsonCodec;
import com.duradb.codec.ProtobufCodec;
import com.duradb.storage.Page;
import com.duradb.storage.PageManager;
import com.duradb.storage.RecordId;
import com.duradb.storage.WALManager;
import com.duradb.storage.WALEntry;
import com.duradb.index.BPlusTree;
import com.duradb.util.ChecksumUtil;
import com.duradb.util.CSVLoader;


import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * DuraDB 统一测试入口
 * 
 * 一键运行所有模块测试，生成 HTML 测试报告
 */
public class AllTests {

    private static final List<TestResult> results = new ArrayList<>();
    private static int passCount = 0;
    private static int failCount = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║              DuraDB 统一测试                                 ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        long totalStart = System.currentTimeMillis();

        // ==================== 运行所有测试 ====================
        testDataModel();
        testCodec();
        testPage();
        testPageManager();
        testWAL();
        testBPlusTree();
        testChecksum();
        testCSV();

        long totalTime = System.currentTimeMillis() - totalStart;

        // ==================== 生成报告 ====================
        generateReport(totalTime);

        // ==================== 打印汇总 ====================
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                     测试汇总                                 ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println("  总测试项: " + results.size());
        System.out.println("  通过: " + passCount);
        System.out.println("  失败: " + failCount);
        System.out.println("  总耗时: " + totalTime + " ms");
        System.out.println();
        System.out.println(" 测试报告已生成: test_report.html");
        System.out.println("   用浏览器打开即可查看");
    }

    // ==================== 1. 数据模型测试 ====================

    private static void testDataModel() {
        System.out.println("--- 1. 数据模型测试 ---");
        boolean ok = true;

        try {
            Student s = new Student(1, "Test", 3.5);
            ok &= s.getId() == 1 && s.getName().equals("Test") && s.getGpa() == 3.5;

            Course c = new Course("CS101", "Test", Arrays.asList(s));
            ok &= c.getCode().equals("CS101") && c.getStudents().size() == 1;

            Node n = new Node(1, "A");
            Node n2 = new Node(2, "B");
            n.addNeighbor(n2);
            ok &= n.getNeighbors().size() == 1;

            addResult("数据模型", ok, "Student/Course/Node 创建成功");
            System.out.println();
        } catch (Exception e) {
            addResult("数据模型", false, e.getMessage());
        }
    }
    

    // ==================== 2. 序列化测试 ====================

    private static void testCodec() {
        System.out.println("--- 2. 序列化测试 ---");

        try {
            Student s = new Student(1001, "Zhang San", 3.85);

            // Binary
            BinaryCodec binary = new BinaryCodec();
            byte[] bData = binary.serializeStudent(s);
            Student s1 = binary.deserializeStudent(bData);
            boolean bOk = s.getId() == s1.getId() && s.getName().equals(s1.getName());

            // JSON
            JsonCodec json = new JsonCodec();
            byte[] jData = json.serialize(s);
            Student s2 = json.deserialize(jData, Student.class);
            boolean jOk = s.getId() == s2.getId() && s.getName().equals(s2.getName());

            // Protobuf
            ProtobufCodec proto = new ProtobufCodec();
            byte[] pData = proto.serializeStudent(s);
            Student s3 = proto.deserializeStudent(pData);
            boolean pOk = s.getId() == s3.getId() && s.getName().equals(s3.getName());

            boolean allOk = bOk && jOk && pOk;
            addResult("序列化", allOk, "Binary: " + bData.length + "B, JSON: " + jData.length + "B, Protobuf: " + pData.length + "B");
            System.out.println();
        } catch (Exception e) {
            addResult("序列化", false, e.getMessage());
        }
    }
    

    // ==================== 3. Page 测试 ====================

    private static void testPage() {
        System.out.println("--- 3. Page 测试 ---");

        try {
            Page page = new Page(0);
            page.insertRecord("Hello".getBytes());
            page.insertRecord("World".getBytes());

            boolean ok = page.getRecordCount() == 2;
            byte[] data = page.readRecord(0);
            ok &= new String(data).equals("Hello");

            page.deleteRecord(0);
            ok &= page.isDeleted(0);

            page.updateChecksum();
            ok &= page.verifyChecksum();

            addResult("Page", ok, "记录数: " + page.getRecordCount() + ", 空闲: " + page.getFreeSpace() + "B");
            System.out.println();
        } catch (Exception e) {
            addResult("Page", false, e.getMessage());
        }
    }
    

    // ==================== 4. PageManager 测试 ====================

    private static void testPageManager() {
        System.out.println("--- 4. PageManager 测试 ---");

        try {
            Files.deleteIfExists(Paths.get("data/test_pm.dat"));

            PageManager pm = new PageManager("data/test_pm.dat");
            BinaryCodec codec = new BinaryCodec();

            for (int i = 1; i <= 10; i++) {
                Student s = new Student(i, "Student_" + i, 3.0 + i * 0.1);
                byte[] data = codec.serializeStudent(s);
                pm.insert(data);
            }

            // 读取第 5 条
            RecordId rid = new RecordId(0, 4);
            byte[] data = pm.read(rid);
            Student s = codec.deserializeStudent(data);
            boolean ok = s.getId() == 5;

            pm.close();

            // 重新打开验证持久化
            PageManager pm2 = new PageManager("data/test_pm.dat");
            ok &= pm2.getTotalPages() > 0;
            pm2.close();

            Files.deleteIfExists(Paths.get("data/test_pm.dat"));

            addResult("PageManager", ok, "总页数: " + pm.getTotalPages());
            System.out.println();
        } catch (Exception e) {
            addResult("PageManager", false, e.getMessage());
        }
    }
    

    // ==================== 5. WAL 测试 ====================

    private static void testWAL() {
        System.out.println("--- 5. WAL 测试 ---");

        try {
            Files.deleteIfExists(Paths.get("data/test_wal.dat"));
            Files.deleteIfExists(Paths.get("data/test_wal.wal"));

            PageManager pm = new PageManager("data/test_wal.dat");
            WALManager wal = new WALManager("data/test_wal.wal");
            BinaryCodec codec = new BinaryCodec();

            for (int i = 1; i <= 5; i++) {
                Student s = new Student(i, "Student_" + i, 3.0 + i * 0.1);
                byte[] data = codec.serializeStudent(s);
                WALEntry entry = new WALEntry(WALEntry.OP_INSERT, i - 1, data);
                wal.writeEntry(entry);
                pm.insert(data);
            }

            pm.close();
            wal.close();

            // 重新打开恢复
            PageManager pm2 = new PageManager("data/test_wal.dat");
            WALManager wal2 = new WALManager("data/test_wal.wal");

            // 恢复（跳过已存在的记录）
            List<WALEntry> entries = wal2.readAllEntries();
            int recovered = 0;
            for (WALEntry entry : entries) {
                int pageId = entry.getRecordId() >> 16;
                int slotIndex = entry.getRecordId() & 0xFFFF;
                RecordId rid = new RecordId(pageId, slotIndex);
                if (pm2.read(rid) == null) {
                    pm2.insert(entry.getData());
                    recovered++;
                }
            }

            boolean ok = recovered == 0;

            pm2.close();
            wal2.close();

            Files.deleteIfExists(Paths.get("data/test_wal.dat"));
            Files.deleteIfExists(Paths.get("data/test_wal.wal"));

            addResult("WAL", ok, "恢复成功，跳过 " + entries.size() + " 条已存在记录");
            System.out.println();
        } catch (Exception e) {
            addResult("WAL", false, e.getMessage());
        }
    }
    

    // ==================== 6. B+树索引测试 ====================

    private static void testBPlusTree() {
        System.out.println("--- 6. B+树索引测试 ---");

        try {
            Files.deleteIfExists(Paths.get("data/test_btree.dat"));

            PageManager pm = new PageManager("data/test_btree.dat");
            BinaryCodec codec = new BinaryCodec();

            for (int i = 1; i <= 20; i++) {
                Student s = new Student(i, "Student_" + i, 3.0 + i * 0.1);
                byte[] data = codec.serializeStudent(s);
                pm.insert(data);
            }

            BPlusTree tree = pm.getBPlusTree();
            boolean ok = tree != null && tree.size() == 20;

            // 测试查询
            byte[] result = pm.selectById(10);
            if (result != null) {
                Student s = codec.deserializeStudent(result);
                ok &= s.getId() == 10;
            } else {
                ok = false;
            }

            pm.close();
            Files.deleteIfExists(Paths.get("data/test_btree.dat"));

            addResult("B+树索引", ok, "索引大小: " + (tree != null ? tree.size() : 0));
            System.out.println();
        } catch (Exception e) {
            addResult("B+树索引", false, e.getMessage());
        }
    }
    

    // ==================== 7. Checksum 测试 ====================

    private static void testChecksum() {
        System.out.println("--- 7. Checksum 测试 ---");

        try {
            byte[] data = "Hello World".getBytes();
            long crc1 = ChecksumUtil.calculateCRC32(data, 0, data.length);

            // 修改一个字节
            byte[] corrupted = data.clone();
            corrupted[6] = 'X';
            long crc2 = ChecksumUtil.calculateCRC32(corrupted, 0, corrupted.length);

            boolean ok = crc1 != crc2;

            addResult("Checksum", ok, "CRC32 检测到数据损坏。");
            System.out.println();
        } catch (Exception e) {
            addResult("Checksum", false, e.getMessage());
        }
    }
    

    // ==================== 8. CSV 完整流程测试 ====================

    private static void testCSV() {
        System.out.println("--- 8. CSV 完整流程测试 ---");

        try {
            // 清理旧文件
            Files.deleteIfExists(Paths.get("data/students.csv"));
            Files.deleteIfExists(Paths.get("data/csv_test.dat"));
            Files.deleteIfExists(Paths.get("data/exported.csv"));

            // ========== Step 1: 生成用户 CSV ==========
            CSVLoader.generateSampleCSV("data/students.csv", 20);

            // ========== Step 2: 导入 DuraDB ==========
            int count = CSVLoader.loadFromCSV("data/students.csv", "data/csv_test.dat");
            boolean ok = count == 20;

            // ========== Step 3: 查询验证 ==========
            PageManager pm = new PageManager("data/csv_test.dat");
            BinaryCodec codec = new BinaryCodec();

            // 查询 id=10 的学生
            byte[] data = pm.selectById(10);
            if (data != null) {
                Student s = codec.deserializeStudent(data);
                ok &= s.getId() == 10;
                System.out.println("   查询 id=10 → " + s);
            } else {
                ok = false;
                System.out.println("   查询 id=10 失败");
            }

            // ========== Step 4: 增加记录 ==========
            Student newStudent = new Student(99, "NewStudent", 4.5);
            byte[] newData = codec.serializeStudent(newStudent);
            RecordId newRid = pm.insert(newData);
            ok &= newRid != null;
            System.out.println("   增加学生: " + newStudent + " → " + newRid);

            // ========== Step 5: 查询新增加的学生 ==========
            byte[] queried = pm.selectById(99);
            if (queried != null) {
                Student s = codec.deserializeStudent(queried);
                ok &= s.getId() == 99 && s.getName().equals("NewStudent");
                System.out.println("   查询 id=99 → " + s);
            } else {
                ok = false;
                System.out.println("   查询 id=99 失败");
            }

            // ========== Step 6: 删除记录 ==========
            BPlusTree tree = pm.getBPlusTree();
            if (tree != null) {
                RecordId rid5 = tree.search(5);
                if (rid5 != null) {
                    pm.delete(rid5);
                    System.out.println("   删除 id=5 成功");
                    byte[] deleted = pm.selectById(5);
                    ok &= deleted == null;
                    System.out.println("   查询 id=5 → " + (deleted == null ? "已删除" : "删除失败"));
                }
            }

            // ========== Step 7: 修改记录 ==========
            RecordId rid10 = tree != null ? tree.search(10) : null;
            if (rid10 != null) {
                Student updated = new Student(10, "UpdatedStudent", 4.9);
                byte[] updateData = codec.serializeStudent(updated);
                pm.delete(rid10);
                pm.insert(updateData);
                System.out.println("   修改 id=10 → " + updated);

                byte[] verified = pm.selectById(10);
                if (verified != null) {
                    Student s = codec.deserializeStudent(verified);
                    ok &= s.getName().equals("UpdatedStudent") && s.getGpa() == 4.9;
                    System.out.println("   验证修改 → " + s);
                }
            }

            // ========== Step 8: 导出 CSV ==========
            int exportedCount = exportToCSV(pm, codec, "data/exported.csv");
            System.out.println("   导出 CSV: " + exportedCount + " 条记录 → data/exported.csv");

            // ========== Step 9: 验证记录数 ==========
            ok &= exportedCount == 20;
            System.out.println("   记录数: 原始 20 + 新增 1 - 删除 1 = 20");

            pm.close();

            // 清理
            Files.deleteIfExists(Paths.get("data/students.csv"));
            Files.deleteIfExists(Paths.get("data/csv_test.dat"));
            Files.deleteIfExists(Paths.get("data/exported.csv"));

            addResult("CSV完整流程", ok, "导入20条, 增删改查全部通过");

        } catch (Exception e) {
            addResult("CSV完整流程", false, e.getMessage());
        }
    }

    // ==================== 导出 CSV 辅助方法 ====================

    private static int exportToCSV(PageManager pm, BinaryCodec codec, String csvPath) throws Exception {
        try (PrintWriter writer = new PrintWriter(new FileWriter(csvPath))) {
            writer.println("id,name,gpa");

            int count = 0;
            for (int pageId = 0; pageId < pm.getTotalPages(); pageId++) {
                try {
                    Page page = pm.readPage(pageId);
                    for (int slot = 0; slot < page.getRecordCount(); slot++) {
                        if (!page.isDeleted(slot)) {
                            byte[] data = page.readRecord(slot);
                            Student s = codec.deserializeStudent(data);
                            writer.println(s.getId() + "," + s.getName() + "," + s.getGpa());
                            count++;
                        }
                    }
                } catch (Exception e) {
                    // 跳过非数据页
                }
            }
            return count;
        }
    }

    // ==================== 工具方法 ====================

    private static void addResult(String module, boolean passed, String detail) {
        TestResult r = new TestResult();
        r.module = module;
        r.passed = passed;
        r.detail = detail;
        r.timestamp = System.currentTimeMillis();
        results.add(r);

        if (passed) {
            passCount++;
            System.out.println("  succeeded " + module + " - " + detail);
        } else {
            failCount++;
            System.out.println("  failed " + module + " - " + detail);
        }
    }

    // ==================== 生成 HTML 报告 ====================

    private static void generateReport(long totalTime) throws Exception {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String now = sdf.format(new Date());

        try (PrintWriter w = new PrintWriter(new FileWriter("test_report.html"))) {
            w.println("<!DOCTYPE html>");
            w.println("<html>");
            w.println("<head><meta charset='UTF-8'><title>DuraDB 测试报告</title>");
            w.println("<style>");
            w.println("body { font-family: Arial, sans-serif; max-width: 900px; margin: 40px auto; padding: 20px; }");
            w.println("h1 { color: #2c3e50; border-bottom: 3px solid #3498db; padding-bottom: 10px; }");
            w.println(".summary { background: #f8f9fa; padding: 20px; border-radius: 8px; margin: 20px 0; }");
            w.println(".pass { color: #27ae60; font-weight: bold; }");
            w.println(".fail { color: #e74c3c; font-weight: bold; }");
            w.println("table { width: 100%; border-collapse: collapse; margin: 20px 0; }");
            w.println("th { background: #3498db; color: white; padding: 12px; text-align: left; }");
            w.println("td { padding: 10px; border-bottom: 1px solid #ddd; }");
            w.println(".status-icon { font-size: 20px; }");
            w.println(".footer { margin-top: 30px; color: #7f8c8d; font-size: 14px; text-align: center; }");
            w.println("</style>");
            w.println("</head>");
            w.println("<body>");

            w.println("<h1>📊 DuraDB 统一测试报告</h1>");
            w.println("<p><strong>生成时间:</strong> " + now + "</p>");

            w.println("<div class='summary'>");
            w.println("<h3>📈 测试汇总</h3>");
            w.println("<p>总测试项: <strong>" + results.size() + "</strong></p>");
            w.println("<p class='pass'>✅ 通过: " + passCount + "</p>");
            w.println("<p class='fail'>❌ 失败: " + failCount + "</p>");
            w.println("<p>总耗时: <strong>" + totalTime + " ms</strong></p>");
            w.println("</div>");

            w.println("<table>");
            w.println("<tr><th>#</th><th>模块</th><th>状态</th><th>详情</th></tr>");

            int idx = 1;
            for (TestResult r : results) {
                String status = r.passed ? "<span class='status-icon pass'>✅</span>" :
                        "<span class='status-icon fail'>❌</span>";
                w.println("<tr>");
                w.println("<td>" + idx++ + "</td>");
                w.println("<td><strong>" + r.module + "</strong></td>");
                w.println("<td>" + status + "</td>");
                w.println("<td>" + r.detail + "</td>");
                w.println("</tr>");
            }

            w.println("</table>");

            w.println("<div class='footer'>");
            w.println("DuraDB 测试报告 · 自动生成");
            w.println("</div>");

            w.println("</body></html>");
        }
    }

    // ==================== 内部类 ====================

    private static class TestResult {
        String module;
        boolean passed;
        String detail;
        long timestamp;
    }
}