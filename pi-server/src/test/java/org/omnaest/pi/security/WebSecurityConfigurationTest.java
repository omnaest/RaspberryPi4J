package org.omnaest.pi.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.omnaest.pi.security.mock.TestApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithAnonymousUser;

@SpringBootTest(classes = TestApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@EnableWebSecurity
public class WebSecurityConfigurationTest
{
    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @WithAnonymousUser
    public void testFilterChain() throws Exception
    {
        assertEquals(HttpStatus.OK, this.restTemplate.getForEntity("/info", String.class)
                                                     .getStatusCode());
    }

}
