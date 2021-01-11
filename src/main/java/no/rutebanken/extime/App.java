package no.rutebanken.extime;


import org.entur.pubsub.base.config.GooglePubSubConfig;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(GooglePubSubConfig.class)

public class App {
    public static void main(String[] args) {
        new SpringApplicationBuilder(App.class).properties("spring.config.name=application,netex-static-data").run(args);
    }
}

