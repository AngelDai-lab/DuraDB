package com.duradb.model;

import java.util.ArrayList;
import java.util.List;

/* 课程模型
 * 包含了 List<Student>，序列化时需要递归处理。*/

public class Course {
    private String code;              // 课程编号，如 "CS101"
    private String name;              // 课程名称，如 "数据结构"
    private List<Student> students;   // 选课学生列表

    public Course() {
        this.students = new ArrayList<>();
    }

    public Course(String code, String name, List<Student> students) {
        this.code = code;
        this.name = name;
        this.students = students != null ? students : new ArrayList<>();
    }

    // ---- Getter and Setter ----
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<Student> getStudents() { return students; }
    public void setStudents(List<Student> students) { this.students = students; }

    // 方便添加单个学生
    public void addStudent(Student student) {
        this.students.add(student);
    }

    @Override
    public String toString() {
        return "Course{code='" + code + "', name='" + name + "', students=" + students + "}";
    }
}