package com.ruoyi.biz.service;

import java.util.List;
import com.ruoyi.biz.domain.SysStudent;

/**
 * 学生管理Service接口
 * 
 * @author life
 * @date 2026-08-07
 */
public interface ISysStudentService 
{
    /**
     * 查询学生管理
     * 
     * @param studentId 学生管理主键
     * @return 学生管理
     */
    public SysStudent selectSysStudentByStudentId(Long studentId);

    /**
     * 查询学生管理列表
     * 
     * @param sysStudent 学生管理
     * @return 学生管理集合
     */
    public List<SysStudent> selectSysStudentList(SysStudent sysStudent);

    /**
     * 新增学生管理
     * 
     * @param sysStudent 学生管理
     * @return 结果
     */
    public int insertSysStudent(SysStudent sysStudent);

    /**
     * 修改学生管理
     * 
     * @param sysStudent 学生管理
     * @return 结果
     */
    public int updateSysStudent(SysStudent sysStudent);

    /**
     * 批量删除学生管理
     * 
     * @param studentIds 需要删除的学生管理主键集合
     * @return 结果
     */
    public int deleteSysStudentByStudentIds(Long[] studentIds);

    /**
     * 删除学生管理信息
     * 
     * @param studentId 学生管理主键
     * @return 结果
     */
    public int deleteSysStudentByStudentId(Long studentId);
}
