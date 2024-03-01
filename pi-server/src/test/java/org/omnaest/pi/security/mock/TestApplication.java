package org.omnaest.pi.security.mock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

@SpringBootApplication
@EnableScheduling
@EnableWebSecurity
public class TestApplication
{
    public static void main(String[] args)
    {
        SpringApplication.run(TestApplication.class, args);
    }
}
