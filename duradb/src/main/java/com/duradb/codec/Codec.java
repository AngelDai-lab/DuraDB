package com.duradb.codec;

/* 序列化器统一接口
 * 所有序列化格式（Binary / JSON / Protobuf）都实现这个接口，
 * 方便后面做 Benchmark 对比测试时统一调用。
 */
public interface Codec<T> {
    
    /* 序列化：对象 → 字节数组*/
    byte[] serialize(T obj) throws Exception;
    
    /* 反序列化：字节数组 → 对象*/
    T deserialize(byte[] data) throws Exception;
}