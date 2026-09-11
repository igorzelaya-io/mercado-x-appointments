package hn.alturaforge.mercadox.appointments;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// @EnableJpaRepositories/@EntityScan for hn.alturaforge.mercadox.library.jpa/entity are already
// provided by mercado-x-library-jpa's auto-configured MercadoXJpaScanningConfig (see
// META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports). Declaring
// them again here duplicated that config, but unlike an auto-configuration import, annotations
// on this class aren't excluded by Boot's test-slice machinery — so @WebMvcTest slices (e.g.
// GoogleCalendarConnectionControllerTest) picked up 19 JPA repository bean definitions with no
// entityManagerFactory to back them. core/oauth rely solely on the auto-configured scanning and
// don't hit this.
@SpringBootApplication(scanBasePackages = {
        "hn.alturaforge.mercadox.appointments",
        "hn.alturaforge.mercadox.context",
})
@ConfigurationPropertiesScan
public class MercadoXAppointmentsApplication {

    public static void main(String[] args) {
        SpringApplication.run(MercadoXAppointmentsApplication.class, args);
    }
}
