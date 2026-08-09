package com.duradb.api;

import com.duradb.model.Student;
import com.duradb.model.Course;
import com.duradb.codec.BinaryCodec;
import com.duradb.storage.PageManager;
import com.duradb.storage.RecordId;
import com.duradb.index.BPlusTree;
import com.duradb.util.CSVLoader;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;


/* DuraDB 教务管理系统
 * 
 * 支持：
 *  1. 学生管理（增删改查）
 *  2. 课程管理（增删改查 + 选课/退课 + 查看选课名单）
 */
public class AcademicSystem {

    private PageManager pm;
    private BinaryCodec codec;
    private boolean isRunning;
    private String dbPath = "data/academic.db";

    public AcademicSystem() {
        this.codec = new BinaryCodec();
        this.isRunning = true;
    }

    public static void main(String[] args) throws Exception {
        Files.createDirectories(Paths.get("data"));

        String csvPath = "data/students.csv";
        String dbPath = "data/academic.db";

        if (!Files.exists(Paths.get(dbPath))) {
            System.out.println(" 数据库不存在，请使用 load 命令加载数据");
            System.out.println("    示例: load " + csvPath);
            System.out.println();
        }

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║              DuraDB 教务管理系统                             ║");
        System.out.println("║              学生管理 · 课程管理                             ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        AcademicSystem db = new AcademicSystem();

        if (Files.exists(Paths.get(dbPath))) {
            System.out.println(" 检测到已有数据库，自动加载...");
            try {
                db.pm = new PageManager(dbPath);
                int count = db.pm.getBPlusTree() != null ? db.pm.getBPlusTree().size() : 0;
                System.out.println("    加载成功，当前 " + count + " 条学生记录");
            } catch (Exception e) {
                System.out.println("   加载失败: " + e.getMessage());
            }
        } else {
            System.out.println("   输入 load " + csvPath + " 加载数据");
        }

        db.run();
    }

    public void run() {
        printHelp();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            while (isRunning) {
                System.out.println();
                System.out.println(" 可用命令:");
                System.out.println("  【学生管理】");
                System.out.println("  load <csv文件>          - 加载学生 CSV 数据");
                System.out.println("  list                    - 列出所有学生");
                System.out.println("  find <id>               - 按 id 查询学生");
                System.out.println("  add <id> <name> <gpa>   - 添加学生");
                System.out.println("  delete <id>             - 删除学生");
                System.out.println("  update <id> <name> <gpa> - 修改学生");
                System.out.println();
                System.out.println("  【课程管理】");
                System.out.println("  course list             - 列出所有课程及选课人数");
                System.out.println("  course find <code>      - 查看课程详情（含选课学生名单）");
                System.out.println("  course add <code> <name>- 添加课程");
                System.out.println("  course delete <code>    - 删除课程");
                System.out.println("  enroll <code> <studentId> - 学生选课");
                System.out.println("  drop <code> <studentId>  - 学生退课");
                System.out.println();
                System.out.println("  【系统】");
                System.out.println("  stats                   - 查看统计");
                System.out.println("  export <csv文件>        - 导出学生数据到 CSV");
                System.out.println("  exit / quit             - 退出程序");
                System.out.println();
                System.out.print("[load | list | find | add | delete | update | course | enroll | drop | stats | export | help | exit]\n> ");
                String line = reader.readLine();
                if (line == null) break;

                String[] parts = line.trim().split("\\s+");
                if (parts.length == 0) continue;

                String command = parts[0].toLowerCase();

                try {
                    switch (command) {
                        // ==================== 系统命令 ====================
                        case "help": printHelp(); break;
                        case "load": handleLoad(parts); break;
                        case "stats": printStats(); break;
                        case "export": handleExport(parts); break;
                        case "exit":
                        case "quit": exit(); break;

                        // ==================== 学生管理 ====================
                        case "list": handleList(); break;
                        case "find": handleFind(parts); break;
                        case "add": handleAdd(parts); break;
                        case "delete": handleDelete(parts); break;
                        case "update": handleUpdate(parts); break;

                        // ==================== 课程管理 ====================
                        case "course": handleCourse(parts); break;
                        case "enroll": handleEnroll(parts); break;
                        case "drop": handleDrop(parts); break;

                        default:
                            System.out.println("    未知命令: " + command + "，输入 help 查看帮助");
                    }
                } catch (NumberFormatException e) {
                    System.out.println("    数字格式错误，请检查输入");
                } catch (Exception e) {
                    System.out.println("    操作失败: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.out.println("  系统错误: " + e.getMessage());
        }
    }

    // ==================== 帮助 ====================

    private void printHelp() {
        System.out.println();
        System.out.println("命令示例:");
        System.out.println("    load data/students.csv          - 加载数据");
        System.out.println("    list                            - 查看所有学生");
        System.out.println("    add 99 Alice 4.5                - 添加学生");
        System.out.println("    course add CS101 DataStructures - 添加课程");
        System.out.println("    enroll CS101 10                 - 学生选课");
        System.out.println("    stats                           - 查看统计");
        System.out.println("    exit                            - 退出");
        System.out.println();
    }

    // ==================== 系统命令 ====================

    private void handleLoad(String[] parts) throws Exception {
        if (parts.length < 2) {
            System.out.println("    用法: load <csv文件路径>");
            return;
        }
        String csvPath = parts[1];
        if (!Files.exists(Paths.get(csvPath))) {
            System.out.println("    文件不存在: " + csvPath);
            return;
        }

        if (pm != null) {
            pm.close();
        }
        Files.deleteIfExists(Paths.get(dbPath));

        System.out.println("   正在加载: " + csvPath);
        long start = System.currentTimeMillis();

        int count = CSVLoader.loadFromCSV(csvPath, dbPath);
        long elapsed = System.currentTimeMillis() - start;

        pm = new PageManager(dbPath);

        System.out.println("    加载完成: " + count + " 条学生记录, 耗时 " + elapsed + " ms");
        System.out.println("   数据已永久保存在: " + dbPath);
    }

    private void printStats() throws Exception {
        if (pm == null) {
            System.out.println("    请先加载数据");
            return;
        }

        int studentCount = pm.getBPlusTree() != null ? pm.getBPlusTree().size() : 0;

        // 统计课程数
        int courseCount = 0;
        for (int pageId = 0; pageId < pm.getTotalPages(); pageId++) {
            try {
                com.duradb.storage.Page page = pm.readPage(pageId);
                for (int slot = 0; slot < page.getRecordCount(); slot++) {
                    if (!page.isDeleted(slot)) {
                        byte[] data = page.readRecord(slot);
                        try {
                            codec.deserializeCourse(data);
                            courseCount++;
                        } catch (Exception e) {
                            // 不是 Course，跳过
                        }
                    }
                }
            } catch (Exception e) {}
        }

        System.out.println("   教务系统统计:");
        System.out.println("    数据文件: " + dbPath);
        System.out.println("    总页数: " + pm.getTotalPages());
        System.out.println("  ─────────────────────");
        System.out.println("    学生数: " + studentCount);
        System.out.println("    课程数: " + courseCount);
        System.out.println("    空闲页: " + pm.getFreePageCount());
    }

    private void handleExport(String[] parts) throws Exception {
        if (pm == null) {
            System.out.println("    请先加载数据");
            return;
        }
        if (parts.length < 2) {
            System.out.println("    用法: export <csv文件路径>");
            return;
        }

        String csvPath = parts[1];
        int count = exportStudentsToCSV(csvPath);
        System.out.println("    导出成功: " + count + " 条学生记录 → " + csvPath);
    }

    private void exit() {
        System.out.println("   正在退出...");
        if (pm != null) {
            try {
                pm.close();
            } catch (Exception e) {}
        }
        isRunning = false;
        System.out.println("   数据已保存到: " + dbPath);
        System.out.println("  再见！");
    }

    // ==================== 学生管理 ====================

    private void handleList() throws Exception {
        if (pm == null) {
            System.out.println(" 请先加载数据 (load <csv文件>)");
            return;
        }

        BPlusTree tree = pm.getBPlusTree();
        if (tree == null || tree.size() == 0) {
            System.out.println(" 数据库为空");
            return;
        }

        System.out.println(" 学生列表:");
        int count = 0;
        for (int pageId = 0; pageId < pm.getTotalPages(); pageId++) {
            try {
                com.duradb.storage.Page page = pm.readPage(pageId);
                for (int slot = 0; slot < page.getRecordCount(); slot++) {
                    if (!page.isDeleted(slot)) {
                        byte[] data = page.readRecord(slot);
                        Student s = codec.deserializeStudent(data);
                        System.out.println("    " + (++count) + ". " + s);
                    }
                }
            } catch (Exception e) {}
        }
        System.out.println(" 共 " + count + " 条记录");
    }

    private void handleFind(String[] parts) throws Exception {
        if (pm == null) {
            System.out.println("    请先加载数据");
            return;
        }
        if (parts.length < 2) {
            System.out.println("    用法: find <id>");
            return;
        }

        int id = Integer.parseInt(parts[1]);
        byte[] data = pm.selectById(id);
        if (data == null) {
            System.out.println("    未找到 id=" + id);
            return;
        }

        Student s = codec.deserializeStudent(data);
        System.out.println("    找到: " + s);
    }

    private void handleAdd(String[] parts) throws Exception {
        if (pm == null) {
            System.out.println("    请先加载数据");
            return;
        }
        if (parts.length < 4) {
            System.out.println("    用法: add <id> <name> <gpa>");
            return;
        }

        int id = Integer.parseInt(parts[1]);
        if (pm.selectById(id) != null) {
            System.out.println("    id=" + id + " 已存在");
            return;
        }

        Student s = new Student(id, parts[2], Double.parseDouble(parts[3]));
        byte[] data = codec.serializeStudent(s);
        RecordId rid = pm.insert(data);
        System.out.println("    添加成功: " + s + " → " + rid);
    }

    private void handleDelete(String[] parts) throws Exception {
        if (pm == null) {
            System.out.println("    请先加载数据");
            return;
        }
        if (parts.length < 2) {
            System.out.println("    用法: delete <id>");
            return;
        }

        int id = Integer.parseInt(parts[1]);
        BPlusTree tree = pm.getBPlusTree();
        if (tree == null) {
            System.out.println("    索引未初始化");
            return;
        }

        RecordId rid = tree.search(id);
        if (rid == null) {
            System.out.println("    未找到 id=" + id);
            return;
        }

        pm.delete(rid);
        System.out.println("    删除成功: id=" + id);
    }

    private void handleUpdate(String[] parts) throws Exception {
        if (pm == null) {
            System.out.println("    请先加载数据");
            return;
        }
        if (parts.length < 4) {
            System.out.println("    用法: update <id> <name> <gpa>");
            return;
        }

        int id = Integer.parseInt(parts[1]);
        BPlusTree tree = pm.getBPlusTree();
        if (tree == null) {
            System.out.println("    索引未初始化");
            return;
        }

        RecordId rid = tree.search(id);
        if (rid == null) {
            System.out.println("    未找到 id=" + id);
            return;
        }

        pm.delete(rid);
        Student s = new Student(id, parts[2], Double.parseDouble(parts[3]));
        pm.insert(codec.serializeStudent(s));
        System.out.println("    修改成功: " + s);
    }

    // ==================== 课程管理 ====================

    private void handleCourse(String[] parts) throws Exception {
        if (pm == null) {
            System.out.println("    请先加载数据");
            return;
        }
        if (parts.length < 2) {
            System.out.println("    用法: course <list|find|add|delete> [参数]");
            return;
        }

        String subCmd = parts[1].toLowerCase();

        switch (subCmd) {
            case "list":
                listCourses();
                break;
            case "find":
                if (parts.length < 3) {
                    System.out.println("    用法: course find <code>");
                } else {
                    findCourse(parts[2]);
                }
                break;
            case "add":
                if (parts.length < 4) {
                    System.out.println("    用法: course add <code> <name>");
                } else {
                    addCourse(parts[2], parts[3]);
                }
                break;
            case "delete":
                if (parts.length < 3) {
                    System.out.println("    用法: course delete <code>");
                } else {
                    deleteCourse(parts[2]);
                }
                break;
            default:
                System.out.println("    未知课程命令: " + subCmd);
        }
    }

    private void listCourses() throws Exception {
        System.out.println("   课程列表:");
        int count = 0;
        for (int pageId = 0; pageId < pm.getTotalPages(); pageId++) {
            try {
                com.duradb.storage.Page page = pm.readPage(pageId);
                for (int slot = 0; slot < page.getRecordCount(); slot++) {
                    if (!page.isDeleted(slot)) {
                        byte[] data = page.readRecord(slot);
                        try {
                            Course c = codec.deserializeCourse(data);
                            System.out.println("    " + (++count) + ". " + c.getCode() + " - " + c.getName() + " (选课人数: " + c.getStudents().size() + ")");
                        } catch (Exception e) {
                            // 不是 Course，跳过
                        }
                    }
                }
            } catch (Exception e) {}
        }
        if (count == 0) {
            System.out.println("   暂无课程");
        } else {
            System.out.println("  共 " + count + " 门课程");
        }
    }

    private void findCourse(String code) throws Exception {
        for (int pageId = 0; pageId < pm.getTotalPages(); pageId++) {
            try {
                com.duradb.storage.Page page = pm.readPage(pageId);
                for (int slot = 0; slot < page.getRecordCount(); slot++) {
                    if (!page.isDeleted(slot)) {
                        byte[] data = page.readRecord(slot);
                        try {
                            Course c = codec.deserializeCourse(data);
                            if (c.getCode().equals(code)) {
                                System.out.println("   课程详情:");
                                System.out.println("    课程编号: " + c.getCode());
                                System.out.println("    课程名称: " + c.getName());
                                System.out.println("    选课人数: " + c.getStudents().size() + " 人");
                                if (!c.getStudents().isEmpty()) {
                                    System.out.println("    选课学生名单:");
                                    for (Student s : c.getStudents()) {
                                        System.out.println("      - " + s);
                                    }
                                } else {
                                    System.out.println("    （暂无学生选课）");
                                }
                                return;
                            }
                        } catch (Exception e) {}
                    }
                }
            } catch (Exception e) {}
        }
        System.out.println("    未找到课程: " + code);
    }

    private void addCourse(String code, String name) throws Exception {
        if (findCourseRecordId(code) != null) {
            System.out.println("    课程 " + code + " 已存在");
            return;
        }

        Course c = new Course(code, name, new ArrayList<>());
        byte[] data = codec.serializeCourse(c);
        RecordId rid = pm.insert(data);
        System.out.println("    课程添加成功: " + code + " - " + name);
    }

    private void deleteCourse(String code) throws Exception {
        RecordId rid = findCourseRecordId(code);
        if (rid == null) {
            System.out.println("    未找到课程: " + code);
            return;
        }

        pm.delete(rid);
        System.out.println("    删除课程成功: " + code);
    }

    private RecordId findCourseRecordId(String code) throws Exception {
        for (int pageId = 0; pageId < pm.getTotalPages(); pageId++) {
            try {
                com.duradb.storage.Page page = pm.readPage(pageId);
                for (int slot = 0; slot < page.getRecordCount(); slot++) {
                    if (!page.isDeleted(slot)) {
                        byte[] data = page.readRecord(slot);
                        try {
                            Course c = codec.deserializeCourse(data);
                            if (c.getCode().equals(code)) {
                                return new RecordId(pageId, slot);
                            }
                        } catch (Exception e) {}
                    }
                }
            } catch (Exception e) {}
        }
        return null;
    }

    // ==================== 选课/退课 ====================

    private void handleEnroll(String[] parts) throws Exception {
        if (pm == null) {
            System.out.println("    请先加载数据");
            return;
        }
        if (parts.length < 3) {
            System.out.println("    用法: enroll <课程编号> <学生id>");
            return;
        }

        String code = parts[1];
        int studentId = Integer.parseInt(parts[2]);

        // 检查学生是否存在
        if (pm.selectById(studentId) == null) {
            System.out.println("    学生 id=" + studentId + " 不存在");
            return;
        }

        RecordId rid = findCourseRecordId(code);
        if (rid == null) {
            System.out.println("    课程 " + code + " 不存在");
            return;
        }

        byte[] data = pm.read(rid);
        Course c = codec.deserializeCourse(data);

        for (Student s : c.getStudents()) {
            if (s.getId() == studentId) {
                System.out.println("    学生 " + studentId + " 已选该课程");
                return;
            }
        }

        Student student = codec.deserializeStudent(pm.selectById(studentId));
        c.getStudents().add(student);

        pm.delete(rid);
        pm.insert(codec.serializeCourse(c));
        System.out.println("    选课成功: 学生 " + studentId + " → " + code);
    }

    private void handleDrop(String[] parts) throws Exception {
        if (pm == null) {
            System.out.println("    请先加载数据");
            return;
        }
        if (parts.length < 3) {
            System.out.println("    用法: drop <课程编号> <学生id>");
            return;
        }

        String code = parts[1];
        int studentId = Integer.parseInt(parts[2]);

        RecordId rid = findCourseRecordId(code);
        if (rid == null) {
            System.out.println("   课程 " + code + " 不存在");
            return;
        }

        byte[] data = pm.read(rid);
        Course c = codec.deserializeCourse(data);

        boolean removed = c.getStudents().removeIf(s -> s.getId() == studentId);
        if (!removed) {
            System.out.println("    学生 " + studentId + " 未选该课程");
            return;
        }

        pm.delete(rid);
        pm.insert(codec.serializeCourse(c));
        System.out.println("   退课成功: 学生 " + studentId + " → " + code);
    }

    // ==================== 导出 ====================

    private int exportStudentsToCSV(String csvPath) throws Exception {
        int count = 0;
        try (PrintWriter writer = new PrintWriter(new FileWriter(csvPath))) {
            writer.println("id,name,gpa");

            for (int pageId = 0; pageId < pm.getTotalPages(); pageId++) {
                try {
                    com.duradb.storage.Page page = pm.readPage(pageId);
                    for (int slot = 0; slot < page.getRecordCount(); slot++) {
                        if (!page.isDeleted(slot)) {
                            byte[] data = page.readRecord(slot);
                            try {
                                Student s = codec.deserializeStudent(data);
                                writer.println(s.getId() + "," + s.getName() + "," + s.getGpa());
                                count++;
                            } catch (Exception e) {}
                        }
                    }
                } catch (Exception e) {}
            }
        }
        return count;
    }
}