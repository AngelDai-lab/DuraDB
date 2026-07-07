package com.duradb;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * 自定义二进制序列化器
 * 
 * 将 Student 对象序列化为紧凑的二进制格式，并能反序列化回来。
 * 
 * 二进制布局：
 * +--------+----------+--------+--------+--------+--------+
 * | id     | nameLen  | name   | name   | ...    | gpa    |
 * | (4B)   | (1B)     | (变长) | (变长) |        | (8B)   |
 * +--------+----------+--------+--------+--------+--------+
 *  字节0-3    字节4     字节5..                        末尾-8..末尾-1
 */
public class BinaryCodec {

    /*序列化：将 Student 对象转为字节数组*/

    public byte[] serialize(Student student) {
        // 1. 获取 name 的 UTF-8 字节
        byte[] nameBytes = student.getName().getBytes(StandardCharsets.UTF_8);
        
        // 2. 检查 name 长度是否超出范围（1字节最大表示127）
        if (nameBytes.length > 127) {
            throw new IllegalArgumentException("Name too long: " + nameBytes.length);
        }
        
        // 3. 计算总长度：id(4) + nameLen(1) + name(变长) + gpa(8)
        int totalLen = 4 + 1 + nameBytes.length + 8;
        
        // 4. 分配 ByteBuffer，使用大端序
        ByteBuffer buffer = ByteBuffer.allocate(totalLen)
                .order(ByteOrder.BIG_ENDIAN);
        
        // 5. 写入各个字段
        buffer.putInt(student.getId());           // 4 字节
        buffer.put((byte) nameBytes.length);      // 1 字节
        buffer.put(nameBytes);                    // N 字节（变长）
        buffer.putDouble(student.getGpa());       // 4 字节（double 是 8 字节）
        
        // 6. 返回字节数组
        return buffer.array();
    }

    /**
     * 反序列化：将字节数组恢复为 Student 对象
     */
    public Student deserialize(byte[] data) {
        // 1. 包装字节数组为 ByteBuffer，使用大端序
        ByteBuffer buffer = ByteBuffer.wrap(data)
                .order(ByteOrder.BIG_ENDIAN);
        
        // 2. 按顺序读取各个字段
        int id = buffer.getInt();                 // 4 字节
        byte nameLen = buffer.get();              // 1 字节
        byte[] nameBytes = new byte[nameLen];     // 准备接收 name 数据
        buffer.get(nameBytes);                   // N 字节
        String name = new String(nameBytes, StandardCharsets.UTF_8);
        double gpa = buffer.getDouble();          // 8 字节（对应序列化时的 putDouble）
        
        // 3. 构造 Student 对象并返回
        return new Student(id, name, gpa);
    }
}