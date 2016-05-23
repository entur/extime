package no.rutebanken.extime;

import no.rutebanken.netex.model.ObjectFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExtimeRouteConfig {

    @Bean
    public ObjectFactory netexObjectFactory() {
        return new ObjectFactory();
    }
	
}
