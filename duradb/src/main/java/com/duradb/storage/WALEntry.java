package com.duradb.storage;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.CRC32;

/*  WAL 日志条目
 * 一条 WAL 记录，代表一个写操作。
 * 格式：
 * +--------+--------+--------+--------+------------------+
 * | CRC32  |  操作   |  key   | value  |   ...            |
 * | (4B)   |  类型   |  长度  |  数据  |                   |
 * |        |  (1B)   |  (2B)  | (变长) |                   |
 * +--------+--------+--------+--------+------------------+
 * 
 * 操作类型：
 * - 0x01: INSERT（插入）
 * - 0x02: DELETE（删除）
 * - 0x03: UPDATE（更新）
 */
public class WALEntry {

    // 操作类型常量
    public static final byte OP_INSERT = 0x01;
    public static final byte OP_DELETE = 0x02;
    public static final byte OP_UPDATE = 0x03;

    private final byte operation;      // 操作类型
    private final int recordId;        // 记录 ID（用于定位）
    private final byte[] data;         // 数据（如果是 DELETE，data 为空）

    // ==================== 构造方法 ====================

    public WALEntry(byte operation, int recordId, byte[] data) {
        this.operation = operation;
        this.recordId = recordId;
        this.data = data != null ? data.clone() : new byte[0];
    }

    // ==================== Getter ====================

    public byte getOperation() {
        return operation;
    }

    public int getRecordId() {
        return recordId;
    }

    public byte[] getData() {
        return data.clone();
    }

    public boolean isDelete() {
        return operation == OP_DELETE;
    }

    public boolean isInsert() {
        return operation == OP_INSERT;
    }

    public boolean isUpdate() {
        return operation == OP_UPDATE;
    }

    // ==================== 序列化 / 反序列化 ====================

    /**
     * 将 WAL 条目序列化为字节数组（用于写入文件）
     */
    public byte[] serialize() {
        // 计算总长度：CRC(4) + op(1) + recordId(4) + dataLen(2) + data(变长)
        int totalLen = 4 + 1 + 4 + 2 + data.length;
        ByteBuffer buf = ByteBuffer.allocate(totalLen)
                .order(ByteOrder.BIG_ENDIAN);

        // 先预留 CRC 位置（后面再填）
        buf.putInt(0);

        // 写入操作类型
        buf.put(operation);

        // 写入 recordId
        buf.putInt(recordId);

        // 写入数据长度
        buf.putShort((short) data.length);

        // 写入数据
        if (data.length > 0) {
            buf.put(data);
        }

        // 计算 CRC32 并填到开头
        byte[] fullData = buf.array();
        CRC32 crc = new CRC32();
        crc.update(fullData, 4, fullData.length - 4);  // 从第 4 字节开始算
        int crcValue = (int) crc.getValue();

        ByteBuffer result = ByteBuffer.allocate(totalLen)
                .order(ByteOrder.BIG_ENDIAN);
        result.putInt(crcValue);
        result.put(operation);
        result.putInt(recordId);
        result.putShort((short) data.length);
        if (data.length > 0) {
            result.put(data);
        }

        return result.array();
    }

    /**
     * 从字节数组反序列化 WAL 条目
     */
    public static WALEntry deserialize(byte[] bytes) {
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        @SuppressWarnings("unused")
        int storedCrc = buf.getInt();
        byte operation = buf.get();
        int recordId = buf.getInt();
        short dataLen = buf.getShort();
        byte[] data = new byte[dataLen];
        buf.get(data);

        // 验证 CRC（可选，调用者可以做）
        // 这里只做基础解析，不抛异常

        return new WALEntry(operation, recordId, data);
    }

    /**
     * 验证 CRC 校验和
     */
    public static boolean verifyCRC(byte[] bytes) {
        if (bytes.length < 4) return false;
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        int storedCrc = buf.getInt();

        CRC32 crc = new CRC32();
        crc.update(bytes, 4, bytes.length - 4);
        int calculated = (int) crc.getValue();

        return storedCrc == calculated;
    }

    @Override
    public String toString() {
        String opName = operation == OP_INSERT ? "INSERT" :
                        operation == OP_DELETE ? "DELETE" :
                        operation == OP_UPDATE ? "UPDATE" : "UNKNOWN";
        return "WALEntry{op=" + opName + ", recordId=" + recordId + 
               ", dataLen=" + data.length + "}";
    }
}