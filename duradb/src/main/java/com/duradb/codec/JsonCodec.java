package com.duradb.codec;

import com.fasterxml.jackson.databind.ObjectMapper;

/*JSON 序列化器（泛型版本）
支持任意类型的对象序列化/反序列化。
 */
public class JsonCodec {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /*序列化：任意对象 → JSON 字符串 → byte[]*/
    public <T> byte[] serialize(T object) throws Exception {
        String jsonString = objectMapper.writeValueAsString(object);
        return jsonString.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /*反序列化：byte[] → JSON 字符串 → 指定类型的对象
     * @param data 字节数组
     * @param clazz 目标类型（如 Student.class, Course.class）
     */
    public <T> T deserialize(byte[] data, Class<T> clazz) throws Exception {
        String jsonString = new String(data, java.nio.charset.StandardCharsets.UTF_8);
        return objectMapper.readValue(jsonString, clazz);
    }
}