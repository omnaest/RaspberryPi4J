package org.omnaest.pi.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provider of a {@link PiClient} bean instance
 * 
 * @author omnaest
 */
@Configuration
public class PiClientProvider
{
    @Value("${server.host:localhost}")
    private String host;

    @Value("${server.port:8080}")
    private int port;

    @Bean
    public PiClient newPiClient()
    {
        return PIRemoteClient.newInstance(this.host, this.port);
    }
}
