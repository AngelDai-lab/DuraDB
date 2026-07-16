package com.duradb.index;

import com.duradb.storage.RecordId;
import com.duradb.storage.PageManager;
import com.duradb.storage.Page;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * B+树索引（内存版）
 * 
 * 使用 ConcurrentSkipListMap 实现 O(log N) 查询
 * 重启后从数据文件重建索引
 */
public class BPlusTree {

    private final ConcurrentSkipListMap<Integer, RecordId> indexMap;

    public BPlusTree(PageManager pageManager) throws Exception {
        this.indexMap = new ConcurrentSkipListMap<>();
        rebuildIndex(pageManager);
    }

    private void rebuildIndex(PageManager pageManager) throws Exception {
        System.out.println("正在重建 B+树索引...");
        int count = 0;

        for (int pageId = 0; pageId < pageManager.getTotalPages(); pageId++) {
            try {
                Page page = pageManager.readPage(pageId);
                int recordCount = page.getRecordCount();

                for (int slot = 0; slot < recordCount; slot++) {
                    if (!page.isDeleted(slot)) {
                        byte[] record = page.readRecord(slot);
                        int id = extractId(record);
                        if (id != -1) {
                            indexMap.put(id, new RecordId(pageId, slot));
                            count++;
                        }
                    }
                }
            } catch (Exception e) {
                // 跳过无法读取的页
            }
        }

        System.out.println("索引重建完成: " + count + " 条记录");
    }

    public void insert(int key, RecordId value) {
        indexMap.put(key, value);
    }

    public RecordId search(int key) {
        return indexMap.get(key);
    }

    public void remove(int key) {
        indexMap.remove(key);
    }

    public int size() {
        return indexMap.size();
    }

    public void printTree() {
        System.out.println("=== B+Tree (内存索引) ===");
        System.out.println("大小: " + indexMap.size());
        int count = 0;
        for (java.util.Map.Entry<Integer, RecordId> entry : indexMap.entrySet()) {
            if (count++ < 20) {
                System.out.println("  " + entry.getKey() + " → " + entry.getValue());
            }
        }
        if (indexMap.size() > 20) {
            System.out.println("  ... 还有 " + (indexMap.size() - 20) + " 条");
        }
    }

    private int extractId(byte[] record) {
        if (record == null || record.length < 4) {
            return -1;
        }
        return ByteBuffer.wrap(record).getInt();
    }
}