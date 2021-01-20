package no.rutebanken.extime;


import org.entur.pubsub.camel.config.GooglePubSubCamelComponentConfig;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(GooglePubSubCamelComponentConfig.class)

public class App {
    public static void main(String[] args) {
        new SpringApplicationBuilder(App.class).run(args);
    }
}

