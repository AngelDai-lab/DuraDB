package com.duradb.storage;

import com.duradb.util.ChecksumUtil;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 数据页（4KB）
 * 
 * 页结构：
 * +------------------+------------------+------------------+
 * |   Page Header    |    Record Data   |   Slot Directory |
 * |   (16 bytes)     |   (变长, 正向)   |   (变长, 反向)   |
 * +------------------+------------------+------------------+
 * 
 * Page Header（16字节）：
 * - pageId (4B)：页编号
 * - recordCount (2B)：本页记录数
 * - freeSpaceOffset (2B)：空闲空间起始位置
 * - checksum (4B)：校验和
 * - reserved (4B)：保留
 * 
 * Slot Directory（从页尾倒序生长）：
 * - 每个槽 4 字节：offset(2B) + length(2B)
 */
public class Page {

    // 页大小：4KB
    public static final int PAGE_SIZE = 4096;

    // 页头大小：16 字节
    public static final int HEADER_SIZE = 16;

    // 每个槽的大小：4 字节（offset 2B + length 2B）
    public static final int SLOT_SIZE = 4;

    // 页头各字段的偏移量
    private static final int OFFSET_PAGE_ID = 0;           // 页编号（第几页）4B
    private static final int OFFSET_RECORD_COUNT = 4;      // 本页存了多少条记录 2B
    private static final int OFFSET_FREE_SPACE = 6;        // 空闲空间从哪开始 2B
    private static final int OFFSET_CHECKSUM = 8;          // 校验和（检测数据是否损坏） 4B
    private static final int OFFSET_RESERVED = 12;         // 预留字段（给未来扩展用） 4B

    // 页数据（在内存中的完整 4KB 字节数组）
    private byte[] data;

    // ==================== 构造方法 ====================

    /* 创建一个新的空页*/
    public Page(int pageId) {
        this.data = new byte[PAGE_SIZE];
        // 初始化页头
        setPageId(pageId);
        setRecordCount(0);
        setFreeSpaceOffset(HEADER_SIZE);  // 从第 16 字节开始写数据
        setChecksum(0);
    }

    /* 从已有的字节数组恢复页*/
    public Page(byte[] data) {
        if (data.length != PAGE_SIZE) {
            throw new IllegalArgumentException("页数据大小必须是 " + PAGE_SIZE + " 字节");
        }
        this.data = data.clone();
    }

    // ==================== 页头读写方法 ====================

    public int getPageId() {
        return ByteBuffer.wrap(data, OFFSET_PAGE_ID, 4).order(ByteOrder.BIG_ENDIAN).getInt();
    }

    public void setPageId(int pageId) {
        ByteBuffer.wrap(data, OFFSET_PAGE_ID, 4).order(ByteOrder.BIG_ENDIAN).putInt(pageId);
    }

    public int getRecordCount() {
        return ByteBuffer.wrap(data, OFFSET_RECORD_COUNT, 2).order(ByteOrder.BIG_ENDIAN).getShort() & 0xFFFF;
    }

    public void setRecordCount(int count) {
        ByteBuffer.wrap(data, OFFSET_RECORD_COUNT, 2).order(ByteOrder.BIG_ENDIAN).putShort((short) count);
    }

    public int getFreeSpaceOffset() {
        return ByteBuffer.wrap(data, OFFSET_FREE_SPACE, 2).order(ByteOrder.BIG_ENDIAN).getShort() & 0xFFFF;
    }

    public void setFreeSpaceOffset(int offset) {
        ByteBuffer.wrap(data, OFFSET_FREE_SPACE, 2).order(ByteOrder.BIG_ENDIAN).putShort((short) offset);
    }

    public int getChecksum() {
        return ByteBuffer.wrap(data, OFFSET_CHECKSUM, 4).order(ByteOrder.BIG_ENDIAN).getInt();
    }

    public void setChecksum(int checksum) {
        ByteBuffer.wrap(data, OFFSET_CHECKSUM, 4).order(ByteOrder.BIG_ENDIAN).putInt(checksum);
    }

    // ==================== 槽目录操作 ====================

    /* 获取某个槽的偏移量*/
    private int getSlotOffset(int slotIndex) {
        // 槽目录从页尾开始倒序生长
        return PAGE_SIZE - (slotIndex + 1) * SLOT_SIZE;
    }

    /* 读取某个槽：返回 {offset, length}*/
    public Slot readSlot(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= getRecordCount()) {
            throw new IndexOutOfBoundsException("槽索引: " + slotIndex + ", 记录数: " + getRecordCount());
        }
        int slotPos = getSlotOffset(slotIndex);
        ByteBuffer buf = ByteBuffer.wrap(data, slotPos, SLOT_SIZE).order(ByteOrder.BIG_ENDIAN);
        int offset = buf.getShort() & 0xFFFF;
        int length = buf.getShort() & 0xFFFF;
        return new Slot(offset, length);
    }

    /* 写入一个槽*/
    private void writeSlot(int slotIndex, int offset, int length) {
        int slotPos = getSlotOffset(slotIndex);
        ByteBuffer buf = ByteBuffer.wrap(data, slotPos, SLOT_SIZE).order(ByteOrder.BIG_ENDIAN);
        buf.putShort((short) offset);
        buf.putShort((short) length);
    }

    // ==================== 记录读写 ====================

    /* 插入一条记录
     * @param record 记录数据（序列化后的字节数组）
     * @return 是否插入成功
     */
    public boolean insertRecord(byte[] record) {
        int recordLen = record.length;

        // 检查是否有足够的空间
        int usedSpace = getFreeSpaceOffset() + getRecordCount() * SLOT_SIZE;
        if (usedSpace + recordLen > PAGE_SIZE) {
            return false;  // 空间不足
        }

        // 在 freeSpaceOffset 位置写入记录
        int offset = getFreeSpaceOffset();
        System.arraycopy(record, 0, data, offset, recordLen);

        // 在槽目录中记录这条记录
        int slotIndex = getRecordCount();
        writeSlot(slotIndex, offset, recordLen);

        // 更新页头
        setRecordCount(slotIndex + 1);
        setFreeSpaceOffset(offset + recordLen);

        return true;
    }

    /* 读取一条记录*/
    public byte[] readRecord(int slotIndex) {
        Slot slot = readSlot(slotIndex);
        byte[] record = new byte[slot.length];
        System.arraycopy(data, slot.offset, record, 0, slot.length);
        return record;
    }

    /* 删除一条记录（标记删除）*/
    public boolean deleteRecord(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= getRecordCount()) {
            return false;
        }
        // 标记删除：把长度设为 -1（即 0xFFFF）
        int slotPos = getSlotOffset(slotIndex);
        ByteBuffer.wrap(data, slotPos + 2, 2).order(ByteOrder.BIG_ENDIAN).putShort((short) 0xFFFF);
        // 注意：这里没有更新 recordCount，只是标记删除
        return true;
    }

    /* 检查某条记录是否已被删除*/
    public boolean isDeleted(int slotIndex) {
        int slotPos = getSlotOffset(slotIndex);
        int length = ByteBuffer.wrap(data, slotPos + 2, 2).order(ByteOrder.BIG_ENDIAN).getShort() & 0xFFFF;
        return length == 0xFFFF;
    }

    // ==================== 工具方法 ====================

    /* 获取页的完整字节数组*/
    public byte[] getData() {
        return data;
    }

    /* 计算页的剩余空间*/
    public int getFreeSpace() {
        int used = getFreeSpaceOffset() + getRecordCount() * SLOT_SIZE;
        return PAGE_SIZE - used;
    }

    /*计算整页的 CRC32 校验和（只计算数据区，不含页头）*/
    public int calculateChecksum() {
        // 只计算数据区（从 HEADER_SIZE 到 PAGE_SIZE）
        // 页头本身的 checksum 字段不参与计算，避免循环依赖
        return (int) ChecksumUtil.calculateCRC32(data, HEADER_SIZE, PAGE_SIZE - HEADER_SIZE);
    }

    /* 验证校验和*/
    public boolean verifyChecksum() {
        int stored = getChecksum();
        int calculated = calculateChecksum();
        if (stored != calculated) {
            System.out.println("⚠️ 校验和失败: 存储=" + stored + ", 计算=" + calculated);
            return false;
        }
        return true;
    }

    /* 更新校验和*/
    public void updateChecksum() {
        setChecksum(calculateChecksum());
    }

    @Override
    public String toString() {
        return "Page{id=" + getPageId() + ", records=" + getRecordCount() + 
               ", free=" + getFreeSpace() + ", freeOffset=" + getFreeSpaceOffset() + "}";
    }

    // ==================== 内部类 ====================

    /* 槽：记录在页内的位置信息*/
    public static class Slot {
        public final int offset;
        public final int length;

        public Slot(int offset, int length) {
            this.offset = offset;
            this.length = length;
        }

        @Override
        public String toString() {
            return "Slot{offset=" + offset + ", length=" + length + "}";
        }
    }
}