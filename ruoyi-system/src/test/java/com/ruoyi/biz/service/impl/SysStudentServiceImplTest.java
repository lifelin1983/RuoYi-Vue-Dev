package com.ruoyi.biz.service.impl;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.biz.domain.SysStudent;
import com.ruoyi.biz.mapper.SysStudentMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * SysStudentServiceImpl 单元测试
 *
 * 纯 Mockito，不启动 Spring 容器。学生是平表（非树形），
 * 不存在产品模块那种「删除父节点产生孤儿数据」的缺陷。
 *
 * 运行：mvn test -pl ruoyi-system -am -Dtest=SysStudentServiceImplTest
 *
 * @author life
 * @date 2026-08-30
 */
@ExtendWith(MockitoExtension.class)
class SysStudentServiceImplTest
{
    @Mock
    private SysStudentMapper sysStudentMapper;

    @InjectMocks
    private SysStudentServiceImpl sysStudentService;

    private SysStudent buildStudent(Long studentId, String name)
    {
        SysStudent student = new SysStudent();
        student.setStudentId(studentId);
        student.setStudentName(name);
        student.setStudentAge(20);
        student.setStudentHobby("0");
        student.setStudentSex("0");
        student.setStudentStatus("0");
        return student;
    }

    @Test
    @DisplayName("按主键查询 - 存在时返回学生")
    void selectSysStudentByStudentId_exist_returnsStudent()
    {
        // given
        SysStudent expected = buildStudent(100L, "张三");
        given(sysStudentMapper.selectSysStudentByStudentId(100L)).willReturn(expected);

        // when
        SysStudent actual = sysStudentService.selectSysStudentByStudentId(100L);

        // then
        assertThat(actual).isNotNull();
        assertThat(actual.getStudentName()).isEqualTo("张三");
        assertThat(actual.getStudentAge()).isEqualTo(20);
        verify(sysStudentMapper).selectSysStudentByStudentId(100L);
    }

    @Test
    @DisplayName("按主键查询 - 不存在时返回 null")
    void selectSysStudentByStudentId_notExist_returnsNull()
    {
        // given
        given(sysStudentMapper.selectSysStudentByStudentId(999L)).willReturn(null);

        // when
        SysStudent actual = sysStudentService.selectSysStudentByStudentId(999L);

        // then
        assertThat(actual).isNull();
    }

    @Test
    @DisplayName("条件查询 - 按性别过滤时透传给 Mapper")
    void selectSysStudentList_withSexCondition_delegatesToMapper()
    {
        // given
        SysStudent query = new SysStudent();
        query.setStudentSex("1");
        SysStudent liSi = buildStudent(101L, "李四");
        liSi.setStudentSex("1");
        List<SysStudent> expected = Collections.singletonList(liSi);
        given(sysStudentMapper.selectSysStudentList(query)).willReturn(expected);

        // when
        List<SysStudent> actual = sysStudentService.selectSysStudentList(query);

        // then
        assertThat(actual).hasSize(1);
        assertThat(actual.get(0).getStudentSex()).isEqualTo("1");
        verify(sysStudentMapper).selectSysStudentList(query);
    }

    @Test
    @DisplayName("条件查询 - 无命中返回空列表")
    void selectSysStudentList_noMatch_returnsEmptyList()
    {
        // given
        given(sysStudentMapper.selectSysStudentList(any(SysStudent.class)))
            .willReturn(Collections.emptyList());

        // when
        List<SysStudent> actual = sysStudentService.selectSysStudentList(new SysStudent());

        // then
        assertThat(actual).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("新增 - 返回影响行数 1")
    void insertSysStudent_success_returnsOne()
    {
        // given
        SysStudent student = buildStudent(null, "王五");
        given(sysStudentMapper.insertSysStudent(student)).willReturn(1);

        // when
        int rows = sysStudentService.insertSysStudent(student);

        // then
        assertThat(rows).isEqualTo(1);
        verify(sysStudentMapper).insertSysStudent(student);
    }

    @Test
    @DisplayName("修改 - 影响行数 0 时返回 0（调用方 toAjax 会判为操作失败）")
    void updateSysStudent_zeroRows_returnsZero()
    {
        // given
        SysStudent student = buildStudent(999L, "不存在");
        given(sysStudentMapper.updateSysStudent(student)).willReturn(0);

        // when
        int rows = sysStudentService.updateSysStudent(student);

        // then
        assertThat(rows).isZero();
    }

    @Test
    @DisplayName("批量删除 - 返回影响行数")
    void deleteSysStudentByStudentIds_returnsAffectedRows()
    {
        // given
        Long[] ids = new Long[] { 100L, 101L, 102L };
        given(sysStudentMapper.deleteSysStudentByStudentIds(ids)).willReturn(3);

        // when
        int rows = sysStudentService.deleteSysStudentByStudentIds(ids);

        // then
        assertThat(rows).isEqualTo(3);
        verify(sysStudentMapper).deleteSysStudentByStudentIds(ids);
    }

    @Test
    @DisplayName("单个删除 - 返回影响行数")
    void deleteSysStudentByStudentId_returnsAffectedRows()
    {
        // given
        given(sysStudentMapper.deleteSysStudentByStudentId(100L)).willReturn(1);

        // when
        int rows = sysStudentService.deleteSysStudentByStudentId(100L);

        // then
        assertThat(rows).isEqualTo(1);
        verify(sysStudentMapper).deleteSysStudentByStudentId(100L);
    }
}
