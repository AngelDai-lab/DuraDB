package com.duradb.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

import com.duradb.index.BPlusTree;

public class PageManager {

    private final Path filePath;
    private FileChannel fileChannel;
    private int totalPages;
    private final BufferPool bufferPool;
    private final Map<Integer, Boolean> freePages = new HashMap<>();
    private BPlusTree bPlusTree;

    public PageManager(String fileName) throws IOException {
        this(fileName, 16);
    }

    public PageManager(String fileName, int cacheSize) throws IOException {
        this.filePath = Paths.get(fileName);

        Path parent = filePath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        this.fileChannel = FileChannel.open(filePath,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE);

        long fileSize = fileChannel.size();
        this.totalPages = (int) (fileSize / Page.PAGE_SIZE);
        this.bufferPool = new BufferPool(cacheSize);

        try {
            this.bPlusTree = new BPlusTree(this);
        } catch (Exception e) {
            System.err.println("B+树初始化失败: " + e.getMessage());
            this.bPlusTree = null;
        }

        System.out.println("PageManager 初始化: " + fileName +
                ", 总页数: " + totalPages +
                ", B+树: " + (bPlusTree != null ? "succeeded" : "failed"));
    }

    public Page readPage(int pageId) throws IOException {
        if (pageId < 0 || pageId >= totalPages) {
            throw new IllegalArgumentException("页号无效: " + pageId);
        }

        Page page = bufferPool.get(pageId);
        if (page != null) {
            return page;
        }

        ByteBuffer buffer = ByteBuffer.allocate(Page.PAGE_SIZE);
        long position = (long) pageId * Page.PAGE_SIZE;
        fileChannel.read(buffer, position);

        byte[] data = buffer.array();
        page = new Page(data);

        bufferPool.put(pageId, page);
        return page;
    }

    public void writePage(Page page) throws IOException {
        int pageId = page.getPageId();
        page.updateChecksum();

        byte[] data = page.getData();
        ByteBuffer buffer = ByteBuffer.wrap(data);
        long position = (long) pageId * Page.PAGE_SIZE;
        fileChannel.write(buffer, position);
        fileChannel.force(false);

        bufferPool.put(pageId, page);
    }

    public Page allocatePage() throws IOException {
        for (Map.Entry<Integer, Boolean> entry : freePages.entrySet()) {
            if (entry.getValue()) {
                int pageId = entry.getKey();
                freePages.put(pageId, false);
                Page page = new Page(pageId);
                writePage(page);
                return page;
            }
        }

        int pageId = totalPages;
        totalPages++;
        Page page = new Page(pageId);
        writePage(page);
        return page;
    }

    public void freePage(int pageId) {
        if (pageId < 0 || pageId >= totalPages) {
            throw new IllegalArgumentException("无效的页号: " + pageId);
        }
        freePages.put(pageId, true);
    }

    public RecordId insert(byte[] record) throws IOException {
        for (int pageId = totalPages - 1; pageId >= 0; pageId--) {
            if (freePages.getOrDefault(pageId, false)) {
                continue;
            }

            Page page = readPage(pageId);
            if (page.insertRecord(record)) {
                page.updateChecksum();
                writePage(page);
                int slotIndex = page.getRecordCount() - 1;
                RecordId recordId = new RecordId(pageId, slotIndex);

                int key = extractId(record);
                if (key != -1 && bPlusTree != null) {
                    bPlusTree.insert(key, recordId);
                }
                return recordId;
            }
        }

        Page newPage = allocatePage();
        if (!newPage.insertRecord(record)) {
            throw new IOException("无法插入记录");
        }
        newPage.updateChecksum();
        writePage(newPage);

        int slotIndex = newPage.getRecordCount() - 1;
        RecordId recordId = new RecordId(newPage.getPageId(), slotIndex);

        int key = extractId(record);
        if (key != -1 && bPlusTree != null) {
            bPlusTree.insert(key, recordId);
        }
        return recordId;
    }

    public byte[] read(RecordId recordId) throws IOException {
        int pageId = recordId.getPageId();
        int slotIndex = recordId.getSlotIndex();

        if (pageId < 0 || pageId >= totalPages) {
            return null;
        }

        Page page = readPage(pageId);
        if (page.isDeleted(slotIndex)) {
            return null;
        }
        return page.readRecord(slotIndex);
    }

    public byte[] selectById(int id) throws Exception {
        if (bPlusTree == null) {
            return null;
        }
        RecordId recordId = bPlusTree.search(id);
        if (recordId == null) {
            return null;
        }
        return read(recordId);
    }

    public BPlusTree getBPlusTree() {
        return bPlusTree;
    }

    public boolean delete(RecordId recordId) throws IOException {
        int pageId = recordId.getPageId();
        int slotIndex = recordId.getSlotIndex();

        if (pageId < 0 || pageId >= totalPages) {
            return false;
        }

        byte[] record = read(recordId);
        int key = record != null ? extractId(record) : -1;

        Page page = readPage(pageId);
        boolean success = page.deleteRecord(slotIndex);

        if (success) {
            page.updateChecksum();
            writePage(page);
            if (key != -1 && bPlusTree != null) {
                bPlusTree.remove(key);
            }
        }
        return success;
    }

    private int extractId(byte[] record) {
        if (record == null || record.length < 4) {
            return -1;
        }
        return ByteBuffer.wrap(record).getInt();
    }

    public int getTotalPages() {
        return totalPages;
    }

    public int getFreePageCount() {
        int count = 0;
        for (boolean isFree : freePages.values()) {
            if (isFree) count++;
        }
        return count;
    }

    public void close() throws IOException {
        if (fileChannel != null) {
            fileChannel.force(true);
            fileChannel.close();
        }
        System.out.println("PageManager 已关闭");
    }

    public void printStats() throws IOException {
        System.out.println("总页数: " + totalPages);
        System.out.println("空闲页: " + getFreePageCount());
        int totalRecords = 0;
        for (int i = 0; i < totalPages; i++) {
            if (freePages.getOrDefault(i, false)) continue;
            Page page = readPage(i);
            totalRecords += page.getRecordCount();
        }
        System.out.println("总记录数: " + totalRecords);
        if (bPlusTree != null) {
            System.out.println("B+树索引大小: " + bPlusTree.size());
        }
    }
}