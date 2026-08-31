package com.ruoyi.architecture;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.StreamUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MyBatis Mapper XML 约束测试
 *
 * ArchUnit 只能检查字节码，看不到 XML 文件。本测试补齐这一层，覆盖铁律 2：
 * 「Mapper XML 必须以 Mapper.xml 结尾且在 resources/mapper/** 下」，
 * 以及 namespace 写错、XML 文件缺失这两类高频故障。
 *
 * 对应配置：application.yml 中的 mybatis.mapperLocations，
 * 它只扫描 mapper 目录下以 Mapper.xml 结尾的文件。
 *
 * 运行：mvn test -pl ruoyi-admin -Dtest=MapperXmlRulesTest
 *
 * @author life
 * @date 2026-08-30
 */
class MapperXmlRulesTest
{
    private static final String DOC = "详见 docs/architecture/boundaries.md";

    private static final Pattern NAMESPACE_PATTERN =
        Pattern.compile("namespace\\s*=\\s*[\"']([^\"']+)[\"']");

    /**
     * 铁律 2：mapper 目录下的 XML 必须以 Mapper.xml 结尾
     *
     * mapperLocations 只扫 *Mapper.xml，命名成 XxxDao.xml / Xxx.xml 都不会被加载。
     */
    @Test
    @DisplayName("mapper 目录下的 XML 必须以 Mapper.xml 结尾")
    void mapperXmlFilesShouldEndWithMapperXml() throws Exception
    {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        List<String> offenders = new ArrayList<String>();

        Resource[] xmlFiles = resolver.getResources("classpath*:mapper/**/*.xml");
        for (Resource xml : xmlFiles)
        {
            String fileName = xml.getFilename();
            if (fileName != null && !fileName.endsWith("Mapper.xml"))
            {
                offenders.add(fileName);
            }
        }

        assertThat(offenders)
            .as("铁律2：mapper 目录下的 XML 必须以 Mapper.xml 结尾。"
                + "application.yml 的 mybatis.mapperLocations = classpath*:mapper/**/*Mapper.xml，"
                + "命名不规范的文件不会被扫描，运行时才报 BindingException，排查成本极高。"
                + "怎么修：重命名为 <实体名>Mapper.xml。" + DOC)
            .isEmpty();
    }

    /**
     * 每个 Mapper 接口都必须有对应的 XML 映射文件
     *
     * 漏建 XML 是最常见的 BindingException 来源。
     */
    @Test
    @DisplayName("每个 Mapper 接口都必须有对应的 XML 映射文件")
    void everyMapperInterfaceShouldHaveXml() throws Exception
    {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        Set<String> existingXmlNames = new HashSet<String>();
        for (Resource xml : resolver.getResources("classpath*:mapper/**/*.xml"))
        {
            String fileName = xml.getFilename();
            if (fileName != null)
            {
                existingXmlNames.add(fileName);
            }
        }

        List<String> missing = new ArrayList<String>();
        Resource[] mapperClasses =
            resolver.getResources("classpath*:com/ruoyi/**/mapper/**/*Mapper.class");

        assertThat(mapperClasses.length)
            .as("未在 classpath 上找到任何 Mapper 接口，规则会空跑通过").isGreaterThan(0);

        for (Resource mapperClass : mapperClasses)
        {
            String fileName = mapperClass.getFilename();
            if (fileName == null || fileName.contains("$"))
            {
                continue;
            }
            String expectedXml = fileName.replace(".class", ".xml");
            if (!existingXmlNames.contains(expectedXml))
            {
                missing.add(fileName + " → 缺少 mapper 目录下的 " + expectedXml);
            }
        }

        assertThat(missing)
            .as("Mapper 接口必须有同名 XML 映射文件，否则调用时报 "
                + "BindingException: Invalid bound statement (not found)。"
                + "怎么修：在 ruoyi-system/src/main/resources/mapper/<模块>/ 下新建对应 XML。"
                + DOC)
            .isEmpty();
    }

    /**
     * XML 的 namespace 必须能解析成真实存在的 Mapper 接口
     *
     * namespace 写错（包名拼错、类名改了没同步）不会在编译期暴露。
     */
    @Test
    @DisplayName("XML 的 namespace 必须能解析成真实存在的 Mapper 接口")
    void everyMapperXmlNamespaceShouldBeResolvable() throws Exception
    {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        List<String> broken = new ArrayList<String>();

        Resource[] xmlFiles = resolver.getResources("classpath*:mapper/**/*Mapper.xml");
        assertThat(xmlFiles.length)
            .as("未在 classpath 上找到任何 Mapper XML，规则会空跑通过").isGreaterThan(0);

        for (Resource xml : xmlFiles)
        {
            String content = StreamUtils.copyToString(xml.getInputStream(), StandardCharsets.UTF_8);
            Matcher matcher = NAMESPACE_PATTERN.matcher(content);

            if (!matcher.find())
            {
                broken.add(xml.getFilename() + " 未声明 namespace");
                continue;
            }

            String namespace = matcher.group(1);
            try
            {
                Class.forName(namespace);
            }
            catch (ClassNotFoundException e)
            {
                broken.add(xml.getFilename() + " 的 namespace '" + namespace + "' 无法解析为类");
            }
        }

        assertThat(broken)
            .as("Mapper XML 的 namespace 必须等于 Mapper 接口的全限定名。"
                + "写错会导致启动失败或运行时 BindingException，且编译期无任何提示。"
                + "怎么修：把 namespace 改为对应 Mapper 接口的全限定名，"
                + "例如 com.ruoyi.biz.mapper.SysProductMapper。" + DOC)
            .isEmpty();
    }
}
