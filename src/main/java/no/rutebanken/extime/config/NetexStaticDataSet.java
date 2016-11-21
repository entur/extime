package no.rutebanken.extime.config;

import com.google.common.base.MoreObjects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "netex-data-sets", locations = "classpath:netex-static-data.yml")
public class NetexStaticDataSet {

    private Map<String, OrganisationDataSet> organisations = new HashMap<>();
    private Map<String, StopPlaceDataSet> stopPlaces = new HashMap<>();

    public NetexStaticDataSet() {}

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
        private LocationDataSet location;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public LocationDataSet getLocation() {
            return location;
        }

        public void setLocation(LocationDataSet location) {
            this.location = location;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("name", name)
                    .add("location", location)
                    .toString();
        }
    }

    public static class LocationDataSet {

        private BigDecimal latitude;
        private BigDecimal longitude;

        public BigDecimal getLatitude() {
            return latitude;
        }

        public void setLatitude(BigDecimal latitude) {
            this.latitude = latitude;
        }

        public BigDecimal getLongitude() {
            return longitude;
        }

        public void setLongitude(BigDecimal longitude) {
            this.longitude = longitude;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("latitude", latitude)
                    .add("longitude", longitude)
                    .toString();
        }
    }

}
