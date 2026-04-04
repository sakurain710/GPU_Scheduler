package com.sakurain.gpuscheduler.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class JacksonConfigPropertyTest {

    @Test
    void shouldDefineGlobalJacksonDateFormatAndTimezone() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application.yaml"));
        Properties properties = factory.getObject();

        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("spring.jackson.date-format")).isEqualTo("yyyy-MM-dd HH:mm:ss");
        assertThat(properties.getProperty("spring.jackson.time-zone")).isEqualTo("Asia/Shanghai");
        assertThat(properties.getProperty("spring.jackson.serialization.write-dates-as-timestamps")).isEqualTo("false");
    }
}
