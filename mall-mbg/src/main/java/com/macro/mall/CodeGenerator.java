package com.macro.mall;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

public class CodeGenerator {
    public static void main(String[] args) {
        // 动态定位 mall-mbg 模块的 src/main 目录，兼容从 mall 根目录或 mall-mbg 目录启动
        String srcMain = locateSrcMainDir();
        String outputJavaDir = srcMain + "/java";
        String outputXmlDir = srcMain + "/resources/mapper";

        FastAutoGenerator.create(
                "jdbc:mysql://192.168.31.128:3306/mall?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai",
                "root", "123456")
            .globalConfig(builder -> {
                builder.author("cyh")
                       .outputDir(outputJavaDir)
                       .disableOpenDir();
            })
            .packageConfig(builder -> {
                builder.parent("com.macro.mall")
                       .entity("model")
                       .mapper("mapper")
                       .pathInfo(Collections.singletonMap(OutputFile.xml,
                               outputXmlDir));
            })
            //.templateConfig(builder -> builder
            //        // 实体使用自定义模板：输出 OpenAPI3 的 @Schema 注解
            //        // （默认 enableSwagger() 输出的是 Swagger2 的 @ApiModel，与本项目 springdoc 体系不匹配）
            //        .entity("/templates/entity.java.ftl")
            //)
            .strategyConfig(builder -> {
                // 不指定 addInclude，默认生成数据库全部表
                builder.entityBuilder()
                       .enableLombok() //开启lombok
                       .enableTableFieldAnnotation() //开启生成实体时生成字段注解
                       .enableFileOverride() //已存在文件强制覆盖
                       .mapperBuilder()
                       .enableBaseResultMap()
                       .enableFileOverride() //mapper 接口与 xml 强制覆盖
                       .serviceBuilder()
                       .disable() //禁用 service 与 serviceImpl 生成
                       .controllerBuilder()
                       .disable() //禁用 controller 生成
                ;
            })
            .templateEngine(new FreemarkerTemplateEngine())
            .execute();
    }

    /**
     * 动态定位 mall-mbg 模块的 src/main 目录，不写死绝对路径。
     * 无论从 mall 根目录还是 mall-mbg 模块目录启动，都能正确找到。
     */
    private static String locateSrcMainDir() {
        String userDir = System.getProperty("user.dir");
        Path path = Paths.get(userDir, "mall-mbg", "src", "main");
        return path.toString();
    }
}
