package no.rutebanken.extime.config;

import com.google.common.base.MoreObjects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "netex-data-sets")
public class NetexStaticDataSet {

    private Map<String, OrganisationDataSet> organisations = new HashMap<>();
    private Map<String, StopPlaceDataSet> stopPlaces = new HashMap<>();

    public NetexStaticDataSet() {
        toString();}

    public Map<String, OrganisationDataSet> getOrganisations() {
        return organisations;
    }

    public void setOrganisations(Map<String, OrganisationDataSet> organisations) {
        this.organisations = organisations;
    }

    public Map<String, StopPlaceDataSet> getStopPlaces() {
        return stopPlaces;
    }

    public void setStopPlaces(Map<String, StopPlaceDataSet> stopPlaces) {
        this.stopPlaces = stopPlaces;
    }

    public static class OrganisationDataSet {

        private String companyNumber;
        private String name;
        private String legalName;
        private String phone;
        private String url;
        private String details;

        public String getCompanyNumber() {
            return companyNumber;
        }

        public void setCompanyNumber(String companyNumber) {
            this.companyNumber = companyNumber;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getLegalName() {
            return legalName;
        }

        public void setLegalName(String legalName) {
            this.legalName = legalName;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getDetails() {
            return details;
        }

        public void setDetails(String details) {
            this.details = details;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("companyNumber", companyNumber)
                    .add("name", name)
                    .add("legalName", legalName)
                    .add("phone", phone)
                    .add("url", url)
                    .add("details", details)
                    .toString();
        }
    }

    public static class StopPlaceDataSet {

        private String name;
        private String shortName;
        private String nsrId;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getShortName() {
            return shortName;
        }

        public void setShortName(String shortName) {
            this.shortName = shortName;
        }

        public String getNsrId() {
            return nsrId;
        }

        public void setNsrId(String nsrId) {
            this.nsrId = nsrId;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("name", name)
                    .add("shortName", shortName)
                    .add("nsrId", nsrId)
                    .toString();
        }
    }



}
