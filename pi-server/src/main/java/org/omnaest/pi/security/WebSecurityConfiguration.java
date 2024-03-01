package org.omnaest.pi.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;

@Configuration
public class WebSecurityConfiguration
{

    @Bean
    protected WebSecurityCustomizer webSecurityCustomizer()
    {
        return web -> web.ignoring()
                         .anyRequest();
    }

}
