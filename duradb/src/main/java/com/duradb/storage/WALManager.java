package com.duradb.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.nio.ByteOrder;

/**
 * WAL 管理器
 * 
 * 职责：
 * 1. 写入 WAL 日志（追加 + 强制落盘）
 * 2. 读取 WAL 日志（用于恢复）
 * 3. 清空 WAL（Checkpoint 后）
 * 4. 崩溃恢复（重放日志）
 */
public class WALManager {

    private final Path walPath;
    private FileChannel walChannel;
    private long lastLogPosition = 0;  // 当前日志文件大小

    /**
     * 构造函数：打开或创建 WAL 文件
     */
    public WALManager(String walFile) throws IOException {
        this.walPath = Paths.get(walFile);
        
        // 确保父目录存在
        Path parent = walPath.getParent();
        if (parent != null && !java.nio.file.Files.exists(parent)) {
            java.nio.file.Files.createDirectories(parent);
        }
        
        this.walChannel = FileChannel.open(walPath,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE);
        
        this.lastLogPosition = walChannel.size();
        System.out.println("WALManager 初始化: " + walFile + 
                           ", 大小: " + lastLogPosition + " 字节");
    }

    // ==================== 写入 ====================

    /**
     * 写入一条 WAL 日志（立即落盘）
     */
    public void writeEntry(WALEntry entry) throws IOException {
        byte[] data = entry.serialize();
        
        // 写入文件（追加）
        ByteBuffer buffer = ByteBuffer.wrap(data);
        walChannel.write(buffer, lastLogPosition);
        
        // 强制落盘！这是 WAL 最关键的步骤
        walChannel.force(true);
        
        // 更新位置
        lastLogPosition += data.length;
        
        System.out.println("WAL 写入: " + entry);
    }

    // ==================== 读取 ====================

    /**
     * 读取所有 WAL 条目（用于恢复）
     */
    public List<WALEntry> readAllEntries() throws IOException {
        List<WALEntry> entries = new ArrayList<>();
        
        if (lastLogPosition == 0) {
            return entries;  // 空文件
        }
        
        // 读取整个 WAL 文件
        ByteBuffer buffer = ByteBuffer.allocate((int) lastLogPosition);
        walChannel.read(buffer, 0);
        byte[] allData = buffer.array();
        
        // 逐条解析
        int pos = 0;
        while (pos < allData.length) {
            // 至少需要 4+1+4+2 = 11 字节才能解析
            if (pos + 11 > allData.length) {
                System.out.println("WAL 文件可能损坏，剩余 " + (allData.length - pos) + " 字节无法解析");
                break;
            }
            
            ByteBuffer headerBuf = ByteBuffer.wrap(allData, pos + 4 + 1 + 4, 2)
                    .order(ByteOrder.BIG_ENDIAN);
            short dataLen = headerBuf.getShort();
            
            // 计算整条记录长度：CRC(4) + op(1) + recordId(4) + dataLen(2) + data
            int entryLen = 4 + 1 + 4 + 2 + dataLen;
            
            if (pos + entryLen > allData.length) {
                System.out.println("WAL 文件不完整，从 " + pos + " 处截断");
                break;
            }
            
            // 提取整条记录
            byte[] entryData = new byte[entryLen];
            System.arraycopy(allData, pos, entryData, 0, entryLen);
            
            // 验证 CRC
            if (!WALEntry.verifyCRC(entryData)) {
                System.out.println("CRC 校验失败，从 " + pos + " 处丢弃");
                pos += entryLen;
                continue;
            }
            
            // 解析条目
            WALEntry entry = WALEntry.deserialize(entryData);
            entries.add(entry);
            
            pos += entryLen;
        }
        
        System.out.println("读取 WAL: " + entries.size() + " 条有效日志");
        return entries;
    }

    // ==================== 恢复 ====================

    /* 恢复数据：重放所有 WAL 日志
     * @param pageManager 页管理器（用于执行重放）
     */
    public void recover(PageManager pageManager) throws IOException {
        List<WALEntry> entries = readAllEntries();
        
        if (entries.isEmpty()) {
            System.out.println("WAL 为空，无需恢复");
            return;
        }
        
        System.out.println("开始恢复 " + entries.size() + " 条操作...");
        
        int recovered = 0;
        int skipped = 0;
        
        for (WALEntry entry : entries) {
            // 从 recordId 还原 PageId 和 SlotIndex
            int pageId = entry.getRecordId() >> 16;
            int slotIndex = entry.getRecordId() & 0xFFFF;
            RecordId rid = new RecordId(pageId, slotIndex);
            
            if (entry.isInsert()) {
                // 先检查这条记录是否已经存在
                byte[] existing = pageManager.read(rid);
                if (existing == null) {
                    // 不存在 → 插入
                    pageManager.insert(entry.getData());
                    recovered++;
                    System.out.println("重放 INSERT: " + rid);
                } else {
                    // 已存在 → 跳过
                    skipped++;
                    System.out.println("跳过已存在的 INSERT: " + rid );
                }
            } else if (entry.isDelete()) {
                // 删除操作：直接执行（删除是幂等的，删多次也没事）
                pageManager.delete(rid);
                recovered++;
                System.out.println("重放 DELETE: " + rid);
            }
        }
        
        System.out.println("恢复完成！成功恢复 " + recovered + " 条，跳过 " + skipped + " 条已存在的记录");
    }

    // ==================== Checkpoint ====================

    /**
     * Checkpoint：清空 WAL
     * 
     * 在数据页已经安全写入磁盘后调用，表示之前的日志已经不需要了。
     */
    public void checkpoint() throws IOException {
        // 清空文件
        walChannel.truncate(0);
        lastLogPosition = 0;
        walChannel.force(true);
        System.out.println("Checkpoint 完成，WAL 已清空");
    }

    /**
     * 获取 WAL 文件大小
     */
    public long getLogSize() throws IOException {
        return walChannel.size();
    }

    /**
     * 关闭 WAL
     */
    public void close() throws IOException {
        if (walChannel != null) {
            walChannel.force(true);
            walChannel.close();
        }
        System.out.println("WALManager 已关闭");
    }
}