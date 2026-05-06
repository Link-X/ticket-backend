package com.ticket.common.config;

import com.ticket.common.util.SnowflakeIdGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SnowflakeConfig {

    @Value("${snowflake.worker-id:1}")
    private long workerId;

    @Value("${snowflake.data-center-id:1}")
    private long dataCenterId;

    @Bean
    public SnowflakeIdGenerator snowflakeIdGenerator() {
        return new SnowflakeIdGenerator(workerId, dataCenterId);
    }
}
