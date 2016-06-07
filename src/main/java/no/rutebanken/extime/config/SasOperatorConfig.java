package no.rutebanken.extime.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(value = "sas", locations = "classpath:netex.yml")
public class SasOperatorConfig extends OrganisationConfig {
}
