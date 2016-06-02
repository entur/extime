package no.rutebanken.extime;

import no.rutebanken.netex.model.*;
import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import javax.xml.bind.JAXBElement;
import java.util.Arrays;
import java.util.List;

@Configuration
public class NetexModelConfig {

    @Bean(name = "netexObjectFactory")
    @Qualifier(value = "netexObjectFactory")
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

    @Bean(name = "SK")
    public Operator sasOperator() {
        Operator operator = new Operator()
                .withVersion("1")
                .withId("SAS:Company:2");
        return operator;
    }

    @Bean(name = "WF")
    public Operator wideroeOperator() {
        Operator operator = new Operator()
                .withVersion("1")
                .withId("Wideroe:Company:2");
        return operator;
    }

    @Bean(name = "DY")
    public Operator norwegianOperator() {
        JAXBElement<String> companyNumber = netexObjectFactory().createOrganisation_VersionStructureCompanyNumber("912084949");
        JAXBElement<MultilingualString> name = netexObjectFactory().createOrganisation_VersionStructureName(new MultilingualString().withValue("Norwegian"));
        JAXBElement<MultilingualString> legalName = netexObjectFactory().createOrganisation_VersionStructureLegalName(new MultilingualString().withValue("NORWEGIAN AIR NORWAY AS"));
        JAXBElement<List<OrganisationTypeEnumeration>> organisationTypes = netexObjectFactory().createOrganisation_VersionStructureOrganisationType(Arrays.asList(OrganisationTypeEnumeration.OPERATOR));

        ContactStructure contactStructure = new ContactStructure()
                .withPhone("0047 815 21 815")
                .withUrl("http://www.norwegian.no")
                .withFurtherDetails(new MultilingualString().withValue("Kontaktskjema på websider"));

        JAXBElement<ContactStructure> contactDetails = netexObjectFactory().createOrganisation_VersionStructureContactDetails(contactStructure);
        List<JAXBElement<?>> jaxbElements = Arrays.asList(companyNumber, name, legalName, organisationTypes, contactDetails);

        return new Operator()
                .withVersion("1")
                .withId("Norwegian:Company:2")
                .withRest(jaxbElements);
    }

/*
    @Bean
    public Foo foo(@Qualifier("bar1") Bar bar1) {
        return new Foo(bar1);
    }
*/
}
