package gov.uk.justice.digital.offenderpollpush.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;

/**
 * Models a very simple subset of the OffenderDetail as supplied by the Community API.
 * The IDs field is a component in the source data structure and we need the same concept
 * here so that the incoming JSON string is parsed properly but we flatten when deserialising
 * to a new JSON string.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OffenderDetail {

    @JsonProperty
    private Long offenderId;

    @JsonProperty(access = Access.WRITE_ONLY)
    private IDs otherIds;

    @SuppressWarnings("unused")
    private OffenderDetail() {
        // Required by Jackson
    }

    public OffenderDetail(final Long offenderId, final IDs otherIds) {
        this.offenderId = offenderId;
        this.otherIds = otherIds;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IDs {
        @JsonProperty
        private String crn;

        @JsonProperty
        private String nomsNumber;

        @SuppressWarnings("unused")
        private IDs() {
            // Required by Jackson
        }

        public IDs(final String crn, final String nomsNumber) {
            this.crn = crn;
            this.nomsNumber = nomsNumber;
        }

        public String getCrn() {
            return crn;
        }
        public String getNomsNumber() {
            return nomsNumber;
        }
    }

    public Long getOffenderId() {
        return offenderId;
    }

    @JsonProperty("crn")
    public String getCrn() {
        return otherIds.getCrn();
    }

    @JsonProperty("nomsNumber")
    public String getNomsNumber() {
        return otherIds.getNomsNumber();
    }
}
