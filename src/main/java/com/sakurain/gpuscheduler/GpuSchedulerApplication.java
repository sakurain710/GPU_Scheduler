package com.sakurain.gpuscheduler;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.sakurain.gpuscheduler.mapper")
public class GpuSchedulerApplication {

    public static void main(String[] args) {
        SpringApplication.run(GpuSchedulerApplication.class, args);
    }

}
