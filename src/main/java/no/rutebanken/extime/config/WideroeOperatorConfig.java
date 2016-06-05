package no.rutebanken.extime.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(value = "wideroe", locations = "classpath:netex.yml")
public class WideroeOperatorConfig extends OrganisationConfig {
}
