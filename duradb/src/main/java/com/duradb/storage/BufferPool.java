package com.duradb.storage;

import java.util.LinkedHashMap;
import java.util.Map;

/* 缓冲池（LRU 缓存）
 * 缓存最近使用的 Page，减少磁盘 I/O。
 * 
 * 核心思想：
 * - 读 Page 时先查缓存，命中则直接返回，未命中则从磁盘读取并放入缓存
 * - 写 Page 时更新缓存，保证缓存和磁盘一致
 * - 缓存满时，自动淘汰最久未使用的 Page（LRU）
 * 
 * 使用 LinkedHashMap 的 accessOrder 模式，自动实现 LRU。
 */
public class BufferPool {

    private final int maxSize;
    private final Map<Integer, Page> cache;

    /**
     * 创建缓冲池
     * @param maxSize 最大缓存页数（默认 16 页 = 64KB）
     */
    public BufferPool(int maxSize) {
        this.maxSize = maxSize;
        // accessOrder = true：按访问顺序排序（最近访问的在尾部）
        this.cache = new LinkedHashMap<Integer, Page>(maxSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Integer, Page> eldest) {
                // 当缓存大小超过最大限制时，淘汰最老的条目
                return size() > maxSize;
            }
        };
        System.out.println("BufferPool 初始化: 最大缓存 " + maxSize + " 页");
    }

    /**
     * 获取页（从缓存中读取）
     * @param pageId 页号
     * @return 如果缓存中有则返回 Page，否则返回 null
     */
    public Page get(int pageId) {
        Page page = cache.get(pageId);
        if (page != null) {
            System.out.println("  [缓存命中] 页 " + pageId);
        }
        return page;
    }

    /**
     * 放入页（写入缓存）
     * @param pageId 页号
     * @param page 页对象
     */
    public void put(int pageId, Page page) {
        cache.put(pageId, page);
        System.out.println("  [缓存放入] 页 " + pageId + " (当前缓存: " + cache.size() + "/" + maxSize + ")");
    }

    /**
     * 移除页（从缓存中删除）
     * @param pageId 页号
     */
    public void remove(int pageId) {
        if (cache.remove(pageId) != null) {
            System.out.println("  [缓存移除] 页 " + pageId);
        }
    }

    /**
     * 清空缓存
     */
    public void clear() {
        cache.clear();
        System.out.println("  [缓存清空]");
    }

    /**
     * 获取当前缓存大小
     */
    public int size() {
        return cache.size();
    }

    /**
     * 判断缓存中是否包含某页
     */
    public boolean contains(int pageId) {
        return cache.containsKey(pageId);
    }

    /**
     * 获取最大缓存容量
     */
    public int getMaxSize() {
        return maxSize;
    }

    /**
     * 打印缓存状态
     */
    public void printStats() {
        System.out.println("  BufferPool: " + cache.size() + "/" + maxSize + " 页");
        if (!cache.isEmpty()) {
            System.out.println("  缓存页号: " + cache.keySet());
        }
    }
}