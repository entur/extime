package no.rutebanken.extime.config;

import org.apache.camel.CamelContext;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spring.boot.CamelContextConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Profile("test")
@Configuration
public class CamelRouteDisabler {

    @Bean
    CamelContextConfiguration contextConfiguration() {
        return new CamelContextConfiguration() {

            @Override
            public void beforeApplicationStart(CamelContext context) {
                RouteDefinition mainRoute = context.getRouteDefinition("AvinorTimetableSchedulerStarter");
                mainRoute.autoStartup(false);
            }

            @Override
            public void afterApplicationStart(CamelContext camelContext) {
                try {
                    camelContext.stop();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
    }
}
