package com.ruoyi.biz.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.biz.mapper.SysStudentMapper;
import com.ruoyi.biz.domain.SysStudent;
import com.ruoyi.biz.service.ISysStudentService;

/**
 * 学生管理Service业务层处理
 * 
 * @author life
 * @date 2026-08-07
 */
@Service
public class SysStudentServiceImpl implements ISysStudentService 
{
    @Autowired
    private SysStudentMapper sysStudentMapper;

    /**
     * 查询学生管理
     * 
     * @param studentId 学生管理主键
     * @return 学生管理
     */
    @Override
    public SysStudent selectSysStudentByStudentId(Long studentId)
    {
        return sysStudentMapper.selectSysStudentByStudentId(studentId);
    }

    /**
     * 查询学生管理列表
     * 
     * @param sysStudent 学生管理
     * @return 学生管理
     */
    @Override
    public List<SysStudent> selectSysStudentList(SysStudent sysStudent)
    {
        return sysStudentMapper.selectSysStudentList(sysStudent);
    }

    /**
     * 新增学生管理
     * 
     * @param sysStudent 学生管理
     * @return 结果
     */
    @Override
    public int insertSysStudent(SysStudent sysStudent)
    {
        return sysStudentMapper.insertSysStudent(sysStudent);
    }

    /**
     * 修改学生管理
     * 
     * @param sysStudent 学生管理
     * @return 结果
     */
    @Override
    public int updateSysStudent(SysStudent sysStudent)
    {
        return sysStudentMapper.updateSysStudent(sysStudent);
    }

    /**
     * 批量删除学生管理
     * 
     * @param studentIds 需要删除的学生管理主键
     * @return 结果
     */
    @Override
    public int deleteSysStudentByStudentIds(Long[] studentIds)
    {
        return sysStudentMapper.deleteSysStudentByStudentIds(studentIds);
    }

    /**
     * 删除学生管理信息
     * 
     * @param studentId 学生管理主键
     * @return 结果
     */
    @Override
    public int deleteSysStudentByStudentId(Long studentId)
    {
        return sysStudentMapper.deleteSysStudentByStudentId(studentId);
    }
}
