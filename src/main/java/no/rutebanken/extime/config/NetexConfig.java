package no.rutebanken.extime.config;

import no.rutebanken.extime.util.NetexObjectFactory;
import org.rutebanken.netex.model.ObjectFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NetexConfig {

    @Bean
    public ObjectFactory objectFactory() {return new ObjectFactory();}

    @Bean
    public NetexObjectFactory netexObjectFactory() {return new NetexObjectFactory();}

}
