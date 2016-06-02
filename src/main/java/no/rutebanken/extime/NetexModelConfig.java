package no.rutebanken.extime;

import no.rutebanken.netex.model.Codespace;
import no.rutebanken.netex.model.ObjectFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NetexModelConfig {

    @Bean
    public ObjectFactory netexObjectFactory() {
        return new ObjectFactory();
    }

    @Bean
    public Codespace avinorCodespace() {
        return new Codespace()
                .withId("avinor")
                .withXmlns("AVI")
                .withXmlnsUrl("https://avinor.no/");
    }

    @Bean
    public Codespace nhrCodespace() {
        return new Codespace()
                .withId("nhr")
                .withXmlns("NHR")
                .withXmlnsUrl("http://www.rutebanken.no/nasjonaltholdeplassregister");
    }
}
