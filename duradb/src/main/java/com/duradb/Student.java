package com.duradb;

/*学生数据模型
 *这是我们 DuraDB 支持的核心数据结构之一
 */
public class Student {
    // 成员变量（属性）
    private int id;
    private String name;
    private double gpa;

    // 构造方法（用来创建一个 Student 对象）
    public Student(int id, String name, double gpa) {
        this.id = id;
        this.name = name;
        this.gpa = gpa;
    }

    // 为了方便，再提供一个无参构造方法
    public Student() {
    }

    // ---------- Getter 和 Setter 方法（用来访问和修改属性） ----------
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getGpa() {
        return gpa;
    }

    public void setGpa(double gpa) {
        this.gpa = gpa;
    }

    // 重写 toString 方法，方便我们打印查看对象内容
    @Override
    public String toString() {
        return "Student{id=" + id + ", name='" + name + "', gpa=" + gpa + "}";
    }
}