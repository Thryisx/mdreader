package com.mdreader;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.mdreader.mapper")
@SpringBootApplication
public class MdReaderApplication {

    public static void main(String[] args) {
        SpringApplication.run(MdReaderApplication.class, args);
    }
}
