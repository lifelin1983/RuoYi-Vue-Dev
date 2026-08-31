package com.ruoyi.biz.mapper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.biz.domain.SysStudent;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SysStudentMapper 数据访问测试
 *
 * 覆盖 selectSysStudentList 的全部动态条件分支（名称 / 性别 / 状态 / 生日 / 年龄区间），
 * 其中「年龄区间」走的是 BaseEntity.params，是最容易写错的一条。
 *
 * 运行：mvn test -pl ruoyi-system -am -Dtest=SysStudentMapperTest
 *
 * @author life
 * @date 2026-08-30
 */
@MybatisTest
@ActiveProfiles("test")
@Sql(scripts = { "/sql/schema.sql", "/sql/data.sql" })
@Transactional
class SysStudentMapperTest
{
    @Autowired
    private SysStudentMapper sysStudentMapper;

    private SysStudent buildStudent(String name, int age)
    {
        SysStudent student = new SysStudent();
        student.setStudentName(name);
        student.setStudentAge(age);
        student.setStudentHobby("0");
        student.setStudentSex("0");
        student.setStudentStatus("0");
        return student;
    }

    @Test
    @DisplayName("selectSysStudentList - 无条件返回全部 3 条")
    void selectSysStudentList_noCondition_returnsAll()
    {
        assertThat(sysStudentMapper.selectSysStudentList(new SysStudent())).hasSize(3);
    }

    @Test
    @DisplayName("selectSysStudentList - 按名称模糊匹配")
    void selectSysStudentList_byNameLike_returnsMatched()
    {
        SysStudent query = new SysStudent();
        query.setStudentName("张");

        List<SysStudent> list = sysStudentMapper.selectSysStudentList(query);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getStudentName()).isEqualTo("张三");
    }

    @Test
    @DisplayName("selectSysStudentList - 按性别精确过滤")
    void selectSysStudentList_bySex_returnsMatched()
    {
        SysStudent query = new SysStudent();
        query.setStudentSex("1");

        List<SysStudent> list = sysStudentMapper.selectSysStudentList(query);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getStudentName()).isEqualTo("李四");
    }

    @Test
    @DisplayName("selectSysStudentList - 按状态过滤（0 正常 / 1 停用）")
    void selectSysStudentList_byStatus_returnsMatched()
    {
        SysStudent query = new SysStudent();
        query.setStudentStatus("1");

        List<SysStudent> list = sysStudentMapper.selectSysStudentList(query);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getStudentName()).isEqualTo("王五");
    }

    /**
     * 验证 BaseEntity.params 透传：XML 中写的是
     * <if test="params.beginStudentAge != null and params.endStudentAge != null">
     *     and student_age between #{params.beginStudentAge} and #{params.endStudentAge}
     * </if>
     * 这是全项目最依赖「params 懒初始化」的一条 SQL。
     */
    @Test
    @DisplayName("selectSysStudentList - 按 params 中的年龄区间过滤")
    void selectSysStudentList_byAgeRange_returnsMatched()
    {
        SysStudent query = new SysStudent();
        query.getParams().put("beginStudentAge", "18");
        query.getParams().put("endStudentAge", "25");

        List<SysStudent> list = sysStudentMapper.selectSysStudentList(query);

        assertThat(list).hasSize(2);
        assertThat(list).extracting(SysStudent::getStudentName)
            .containsExactlyInAnyOrder("张三", "李四");
    }

    @Test
    @DisplayName("selectSysStudentByStudentId - 存在时生日字段映射正确")
    void selectSysStudentByStudentId_mapsBirthday()
    {
        SysStudent student = sysStudentMapper.selectSysStudentByStudentId(100L);

        assertThat(student).isNotNull();
        assertThat(student.getStudentName()).isEqualTo("张三");
        assertThat(student.getStudentAge()).isEqualTo(20);
        assertThat(new SimpleDateFormat("yyyy-MM-dd").format(student.getStudentBirthday()))
            .isEqualTo("2006-01-15");
    }

    @Test
    @DisplayName("selectSysStudentByStudentId - 不存在时返回 null")
    void selectSysStudentByStudentId_notExist_returnsNull()
    {
        assertThat(sysStudentMapper.selectSysStudentByStudentId(999L)).isNull();
    }

    @Test
    @DisplayName("insertSysStudent - 主键回填且生日可正确回读")
    void insertSysStudent_generatesKeyAndPersistsBirthday() throws Exception
    {
        SysStudent student = buildStudent("赵六", 26);
        Date birthday = new SimpleDateFormat("yyyy-MM-dd").parse("2000-03-09");
        student.setStudentBirthday(birthday);

        int rows = sysStudentMapper.insertSysStudent(student);

        assertThat(rows).isEqualTo(1);
        assertThat(student.getStudentId()).isNotNull();

        SysStudent saved = sysStudentMapper.selectSysStudentByStudentId(student.getStudentId());
        assertThat(saved.getStudentName()).isEqualTo("赵六");
        assertThat(new SimpleDateFormat("yyyy-MM-dd").format(saved.getStudentBirthday()))
            .isEqualTo("2000-03-09");
    }

    @Test
    @DisplayName("updateSysStudent - 更新后字段生效")
    void updateSysStudent_changesField()
    {
        SysStudent student = sysStudentMapper.selectSysStudentByStudentId(100L);
        student.setStudentName("张三改名");
        student.setStudentAge(21);

        int rows = sysStudentMapper.updateSysStudent(student);

        assertThat(rows).isEqualTo(1);
        SysStudent updated = sysStudentMapper.selectSysStudentByStudentId(100L);
        assertThat(updated.getStudentName()).isEqualTo("张三改名");
        assertThat(updated.getStudentAge()).isEqualTo(21);
    }

    @Test
    @DisplayName("updateSysStudent - 主键不存在时影响 0 行")
    void updateSysStudent_notExist_affectsZeroRows()
    {
        SysStudent student = buildStudent("不存在", 18);
        student.setStudentId(999L);

        assertThat(sysStudentMapper.updateSysStudent(student)).isZero();
    }

    @Test
    @DisplayName("deleteSysStudentByStudentId - 删除后查不到")
    void deleteSysStudentByStudentId_removesRow()
    {
        int rows = sysStudentMapper.deleteSysStudentByStudentId(100L);

        assertThat(rows).isEqualTo(1);
        assertThat(sysStudentMapper.selectSysStudentByStudentId(100L)).isNull();
    }

    @Test
    @DisplayName("deleteSysStudentByStudentIds - 批量删除")
    void deleteSysStudentByStudentIds_removesMultipleRows()
    {
        int rows = sysStudentMapper.deleteSysStudentByStudentIds(new Long[] { 100L, 101L, 102L });

        assertThat(rows).isEqualTo(3);
        assertThat(sysStudentMapper.selectSysStudentList(new SysStudent())).isEmpty();
    }
}
