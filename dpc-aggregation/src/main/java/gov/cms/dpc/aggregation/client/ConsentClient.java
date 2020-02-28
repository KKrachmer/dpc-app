package gov.cms.dpc.aggregation.client;

import org.hl7.fhir.dstu3.model.Consent;

import java.util.Optional;

public interface ConsentClient {
    Optional<Consent> fetchConsentByMBI(String patientID);
}
