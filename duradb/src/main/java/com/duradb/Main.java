package com.duradb;

public class Main {
    public static void main(String[] args) {
        // 1. 创建一个 Student 对象
        Student original = new Student(1001, "Zhang San", 3.85);
        System.out.println("原始对象: " + original);

        // 2. 序列化：对象 → 字节数组
        BinaryCodec codec = new BinaryCodec();
        byte[] binaryData = codec.serialize(original);
        System.out.println("序列化后的字节数: " + binaryData.length + " 字节");
        System.out.println("十六进制: " + bytesToHex(binaryData));

        // 3. 反序列化：字节数组 → 对象
        Student restored = codec.deserialize(binaryData);
        System.out.println("恢复后的对象: " + restored);

        // 4. 验证是否一致
        boolean isSame = original.getId() == restored.getId()
                && original.getName().equals(restored.getName())
                && Double.compare(original.getGpa(), restored.getGpa()) == 0;
        System.out.println("序列化/反序列化是否一致? " + (isSame ? "成功！" : "❌ 失败！"));
    }

    // 辅助方法：字节数组转十六进制字符串（方便查看二进制内容）
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString();
    }
}