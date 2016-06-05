package no.rutebanken.extime;

import org.apache.camel.spring.boot.FatJarRouter;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class App extends FatJarRouter {
    public static void main(String[] args) {
        FatJarRouter.main(args);
    }
}
