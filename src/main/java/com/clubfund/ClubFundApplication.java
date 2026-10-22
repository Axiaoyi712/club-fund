package com.clubfund;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 项目启动类：运行这个 main 方法，整个系统就启动了。
 * @MapperScan 告诉框架去哪找操作数据库的 Mapper 接口
 */
@SpringBootApplication
@MapperScan("com.clubfund.mapper")
public class ClubFundApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClubFundApplication.class, args);
    }
}
