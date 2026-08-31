package com.ruoyi;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * Mapper 切片测试用的启动配置
 *
 * 为什么需要这个类：
 * @MybatisTest 会从测试类所在包（com.ruoyi.biz.mapper）向上查找 @SpringBootConfiguration。
 * 真正的启动类 RuoYiApplication 位于 ruoyi-admin 模块，不在 ruoyi-system 的测试 classpath 上，
 * 因此会报 "Unable to find a @SpringBootConfiguration"。
 *
 * 为什么不用 @SpringBootApplication：
 * 它会触发 @ComponentScan，把整个应用的 Bean 都拉起来，那就不是切片测试了。
 * 这里只声明配置 + 自动装配，实际加载哪些自动配置由 @MybatisTest 的切片过滤器决定。
 *
 * @author life
 * @date 2026-08-30
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@MapperScan("com.ruoyi.**.mapper")
public class TestMybatisApplication
{
}
