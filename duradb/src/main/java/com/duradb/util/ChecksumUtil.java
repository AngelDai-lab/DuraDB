package com.duradb.util;

import java.util.zip.CRC32;

/**
 * 校验和工具类
 * 
 * 使用 CRC32 算法计算校验和，检测数据完整性。
 * CRC32 是工业级标准，比简单累加更可靠，被 MySQL InnoDB、PostgreSQL 等主流数据库广泛使用。
 * 
 * CRC32 特点：
 * - 检测能力强：能检测到数据中任何一位的改变
 * - 速度快：硬件级支持，计算高效
 * - 冲突率低：2^32 分之一，实际使用中几乎不可能冲突
 */
public class ChecksumUtil {

    /**
     * 计算整个字节数组的 CRC32 校验和
     */
    public static long calculateCRC32(byte[] data) {
        return calculateCRC32(data, 0, data.length);
    }

    /**
     * 计算字节数组指定范围的 CRC32 校验和
     * 
     * @param data   数据
     * @param offset 起始偏移
     * @param length 长度
     * @return CRC32 校验和（32 位无符号整数）
     */
    public static long calculateCRC32(byte[] data, int offset, int length) {
        if (data == null || data.length == 0) {
            return 0;
        }
        // 边界检查
        if (offset < 0 || offset >= data.length) {
            throw new IllegalArgumentException("offset 越界: " + offset);
        }
        if (offset + length > data.length) {
            throw new IllegalArgumentException("offset + length 越界");
        }
        CRC32 crc = new CRC32();
        crc.update(data, offset, length);
        return crc.getValue();
    }

    /**
     * 验证整个数据的 CRC32
     * 
     * @param data         数据
     * @param expectedCRC  期望的 CRC32 值
     * @return true 表示数据完整，false 表示数据已损坏
     */
    public static boolean verifyCRC32(byte[] data, long expectedCRC) {
        return calculateCRC32(data) == expectedCRC;
    }

    /**
     * 验证指定范围数据的 CRC32
     */
    public static boolean verifyCRC32(byte[] data, int offset, int length, long expectedCRC) {
        return calculateCRC32(data, offset, length) == expectedCRC;
    }

    /**
     * 判断数据是否被修改
     * 
     * @param oldData 原始数据
     * @param newData 新数据
     * @return true 表示数据被修改，false 表示未修改
     */
    public static boolean isDataModified(byte[] oldData, byte[] newData) {
        if (oldData == null || newData == null) {
            return oldData != newData;
        }
        if (oldData.length != newData.length) {
            return true;
        }
        return calculateCRC32(oldData) != calculateCRC32(newData);
    }

    /**
     * 打印校验和信息（调试用）
     */
    public static void printChecksum(byte[] data, String label) {
        System.out.println(label + " - CRC32: " + calculateCRC32(data));
    }

    /**
     * 打印指定范围的校验和信息（调试用）
     */
    public static void printChecksum(byte[] data, int offset, int length, String label) {
        System.out.println(label + " - CRC32: " + calculateCRC32(data, offset, length));
    }
}