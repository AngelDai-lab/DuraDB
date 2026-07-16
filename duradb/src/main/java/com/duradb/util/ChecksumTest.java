package com.duradb.util;

import com.duradb.model.Student;
import com.duradb.codec.BinaryCodec;
import com.duradb.storage.PageManager;
import com.duradb.storage.RecordId;
import com.duradb.storage.Page;

import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * 校验和功能测试
 * 1. 正常写入和读取（校验和通过）
 * 2. 模拟数据损坏（校验和不通过）
 * 3. 手动查看 Page 的校验和值
 */
public class ChecksumTest {

    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║              Checksum 校验和测试                              ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        // 清理旧文件
        Files.deleteIfExists(Paths.get("data/checksum_test.dat"));

        // 1. 正常写入
        System.out.println("--- 第 1 步：正常写入数据 ---");
        PageManager pm = new PageManager("data/checksum_test.dat");
        BinaryCodec codec = new BinaryCodec();

        Student s = new Student(1001, "Zhang San", 3.85);
        byte[] data = codec.serializeStudent(s);
        RecordId rid = pm.insert(data);
        System.out.println("插入: " + s + " → " + rid);

        // 2. 正常读取，查看校验和
        System.out.println();
        System.out.println("--- 第 2 步：正常读取（校验和通过） ---");
        Page page = pm.readPage(0);
        int storedChecksum = page.getChecksum();
        int calculatedChecksum = page.calculateChecksum();
        System.out.println("  存储的校验和: " + storedChecksum);
        System.out.println("  计算的校验和: " + calculatedChecksum);
        System.out.println("  校验和验证: " + (page.verifyChecksum() ? " 通过" : " 失败"));

        // 打印十六进制数据
        System.out.println();
        System.out.println("--- 第 3 步：查看页数据（十六进制） ---");
        byte[] pageData = page.getData();
        System.out.println("  页数据前 64 字节:");
        for (int i = 0; i < 64 && i < pageData.length; i++) {
            System.out.printf("%02X ", pageData[i]);
            if ((i + 1) % 16 == 0) System.out.println();
        }

        System.out.println();
        System.out.println("--- 第 4 步：模拟数据损坏 ---");
        // 损坏数据：把第 30 字节改成 0xFF（原来是某个数据）
        byte[] corruptedData = pageData.clone();
        System.out.println("  原来第 30 字节: 0x" + String.format("%02X", corruptedData[30]));
        corruptedData[30] = (byte) 0xFF;
        System.out.println("  修改后第 30 字节: 0x" + String.format("%02X", corruptedData[30]));

        // 用损坏的数据构造 Page
        Page corruptedPage = new Page(corruptedData);
        int corruptedStored = corruptedPage.getChecksum();
        int corruptedCalculated = corruptedPage.calculateChecksum();
        System.out.println();
        System.out.println("    损坏后重新计算:");
        System.out.println("    存储的校验和: " + corruptedStored);
        System.out.println("    计算的校验和: " + corruptedCalculated);
        System.out.println("    校验和验证: " + (corruptedPage.verifyChecksum() ? " 通过（异常！）" : " 失败（检测到损坏）"));

        System.out.println();
        System.out.println("--- 第 5 步：写入后再读取（校验和自动验证） ---");
        Page freshPage = new Page(pageData);
        freshPage.updateChecksum();
        pm.writePage(freshPage);
        pm.close();

        // 重新打开读取
        PageManager pm2 = new PageManager("data/checksum_test.dat");
        Page readPage = pm2.readPage(0);
        System.out.println("  读取到的校验和: " + readPage.getChecksum());
        System.out.println("  计算出的校验和: " + readPage.calculateChecksum());
        System.out.println("  校验和验证: " + (readPage.verifyChecksum() ? " 通过" : " 失败"));
        pm2.close();

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║            Checksum 测试完成！                                ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
    }
}