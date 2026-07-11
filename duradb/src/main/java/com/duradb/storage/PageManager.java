package com.duradb.storage;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * 页管理器
 * 
 * 职责：
 * 1. 管理数据文件中的所有页
 * 2. 分配新页（追加到文件末尾）
 * 3. 读取指定页
 * 4. 写入指定页（将页数据写回文件）
 * 5. 维护空闲页列表（被删除的页可以复用）
 * 
 * 文件结构：
 * +--------+--------+--------+--------+--------+
 * |  Page 0 |  Page 1 |  Page 2 |  Page 3 |  ...  |
 * |  4KB    |  4KB    |  4KB    |  4KB    |       |
 * +--------+--------+--------+--------+--------+
 * 
 * 第 N 页在文件中的位置：N * 4096
 */
public class PageManager {

    // 数据文件路径
    private final Path filePath;
    
    // 文件通道（用于读写）
    private FileChannel fileChannel;
    
    // 当前文件总页数
    private int totalPages;
    
    // 空闲页列表（这些页可以被复用）
    // 以后可以持久化到磁盘，目前先用内存缓存
    private final Map<Integer, Boolean> freePages = new HashMap<>();

    /**
     * 构造函数：打开或创建数据文件
     */
    public PageManager(String fileName) throws IOException {
        this.filePath = Paths.get(fileName);
        
        // 以读写模式打开文件，如果不存在则创建
        RandomAccessFile file = new RandomAccessFile(filePath.toFile(), "rw");
        this.fileChannel = file.getChannel();
        
        // 计算当前文件有多少页
        long fileSize = fileChannel.size();
        this.totalPages = (int) (fileSize / Page.PAGE_SIZE);
        
        System.out.println("PageManager 初始化: " + fileName + 
                           ", 大小: " + fileSize + " 字节, 页数: " + totalPages);
    }

    // ==================== 页的读写 ====================

    /**
     * 读取指定页
     */
    public Page readPage(int pageId) throws IOException {
        if (pageId < 0 || pageId >= totalPages) {
            throw new IllegalArgumentException("页号无效: " + pageId + ", 总页数: " + totalPages);
        }
        
        // 分配 4KB 缓冲区
        ByteBuffer buffer = ByteBuffer.allocate(Page.PAGE_SIZE);
        
        // 定位到该页的起始位置
        long position = (long) pageId * Page.PAGE_SIZE;
        fileChannel.read(buffer, position);
        
        // 从缓冲区取出数据
        byte[] data = buffer.array();
        
        // 用数据构造 Page 对象
        Page page = new Page(data);
        
        // 验证页号是否匹配
        if (page.getPageId() != pageId) {
            System.out.println("警告: 读取的页号 " + page.getPageId() + 
                               " 与请求的 " + pageId + " 不一致");
        }
        
        return page;
    }

    /**
     * 写入指定页（将页数据刷回磁盘）
     */
    public void writePage(Page page) throws IOException {
        int pageId = page.getPageId();
        
        // 更新校验和
        page.updateChecksum();
        
        // 获取页的字节数据
        byte[] data = page.getData();
        ByteBuffer buffer = ByteBuffer.wrap(data);
        
        // 定位到该页的起始位置
        long position = (long) pageId * Page.PAGE_SIZE;
        fileChannel.write(buffer, position);
        
        // 强制刷盘（确保数据真正写入磁盘）
        fileChannel.force(false);
    }

    // ==================== 页的分配 ====================

    /**
     * 分配一个新页
     * 优先复用空闲页，如果没有则追加到文件末尾
     */
    public Page allocatePage() throws IOException {
        int pageId;
        
        // 1. 尝试从空闲列表中复用
        for (Map.Entry<Integer, Boolean> entry : freePages.entrySet()) {
            if (entry.getValue()) {
                pageId = entry.getKey();
                freePages.put(pageId, false);  // 标记为已占用
                
                // 创建一个新页，复用这个 ID
                Page page = new Page(pageId);
                writePage(page);
                System.out.println("复用空闲页: " + pageId);
                return page;
            }
        }
        
        // 2. 没有空闲页，追加到文件末尾
        pageId = totalPages;
        totalPages++;
        
        // 创建新页
        Page page = new Page(pageId);
        writePage(page);
        
        System.out.println("分配新页: " + pageId + ", 总页数: " + totalPages);
        return page;
    }

    /**
     * 释放一个页（标记为空闲）
     */
    public void freePage(int pageId) {
        if (pageId < 0 || pageId >= totalPages) {
            throw new IllegalArgumentException("无效的页号: " + pageId);
        }
        freePages.put(pageId, true);
        System.out.println("释放页: " + pageId);
    }

    // ==================== 数据写入 ====================

    /**
     * 插入一条记录
     * 自动寻找有空间的页，如果当前页满了就分配新页
     */
    public RecordId insert(byte[] record) throws IOException {
        // 1. 从最后一页开始找（优先用最新的页，提高缓存命中率）
        for (int pageId = totalPages - 1; pageId >= 0; pageId--) {
            // 跳过空闲页
            if (freePages.getOrDefault(pageId, false)) {
                continue;
            }
            
            Page page = readPage(pageId);
            
            // 尝试插入
            if (page.insertRecord(record)) {
                // 写入成功，更新校验和并写回磁盘
                page.updateChecksum();
                writePage(page);
                int slotIndex = page.getRecordCount() - 1;
                System.out.println("插入记录: 页 " + pageId + ", 槽 " + slotIndex);
                return new RecordId(pageId, slotIndex);
            }
        }
        
        // 2. 所有现有页都满了，分配新页
        Page newPage = allocatePage();
        boolean success = newPage.insertRecord(record);
        if (!success) {
            throw new IOException("新页也无法插入记录，记录可能太大");
        }
        newPage.updateChecksum();
        writePage(newPage);
        
        int slotIndex = newPage.getRecordCount() - 1;
        System.out.println("插入记录到新页: 页 " + newPage.getPageId() + ", 槽 " + slotIndex);
        return new RecordId(newPage.getPageId(), slotIndex);
    }

    // ==================== 数据读取 ====================

    /**
     * 根据 RecordId 读取一条记录
     */
    public byte[] read(RecordId recordId) throws IOException {
        int pageId = recordId.getPageId();
        int slotIndex = recordId.getSlotIndex();
        
        if (pageId < 0 || pageId >= totalPages) {
            throw new IllegalArgumentException("页号无效: " + pageId);
        }
        
        Page page = readPage(pageId);
        
        // 检查是否已被删除
        if (page.isDeleted(slotIndex)) {
            return null;  // 记录已被删除
        }
        
        return page.readRecord(slotIndex);
    }

    // ==================== 数据删除 ====================

    /**
     * 删除一条记录（标记删除）
     */
    public boolean delete(RecordId recordId) throws IOException {
        int pageId = recordId.getPageId();
        int slotIndex = recordId.getSlotIndex();
        
        if (pageId < 0 || pageId >= totalPages) {
            return false;
        }
        
        Page page = readPage(pageId);
        boolean success = page.deleteRecord(slotIndex);
        
        if (success) {
            page.updateChecksum();
            writePage(page);
            System.out.println("删除记录: 页 " + pageId + ", 槽 " + slotIndex);
        }
        
        return success;
    }

    // ==================== 工具方法 ====================

    /**
     * 获取总页数
     */
    public int getTotalPages() {
        return totalPages;
    }

    /**
     * 获取空闲页数量
     */
    public int getFreePageCount() {
        int count = 0;
        for (boolean isFree : freePages.values()) {
            if (isFree) count++;
        }
        return count;
    }

    /**
     * 关闭文件
     */
    public void close() throws IOException {
        if (fileChannel != null) {
            fileChannel.force(true);
            fileChannel.close();
        }
        System.out.println("PageManager 已关闭");
    }

    /**
     * 打印统计信息
     */
    public void printStats() throws IOException {
        System.out.println("=== PageManager 统计 ===");
        System.out.println("总页数: " + totalPages);
        System.out.println("空闲页: " + getFreePageCount());
        
        int totalRecords = 0;
        for (int i = 0; i < totalPages; i++) {
            if (freePages.getOrDefault(i, false)) continue;
            Page page = readPage(i);
            totalRecords += page.getRecordCount();
        }
        System.out.println("总记录数: " + totalRecords);
        System.out.println("========================");
    }
}