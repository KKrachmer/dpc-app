package gov.cms.dpc.fhir.converters.entities;

import ca.uhn.fhir.context.FhirContext;
import gov.cms.dpc.common.consent.entities.ConsentEntity;
import org.hl7.fhir.dstu3.model.Consent;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class ConsentEntityConverterTest {

    private static final String TEST_FHIR_URL = "http://test-fhir-url";
    private static final String TEST_DPC_URL = "https://dpc.cms.gov";
    private static final String TEST_HICN = "fake_hicn";
    private static final String TEST_MBI = "fake_mbi";

    private ConsentEntityConverterTest() { }

    @Test
    final void convert_correctlyConverts_fromDefaultEntity() {
        ConsentEntity ce = ConsentEntity.defaultConsentEntity(Optional.empty(), Optional.of(TEST_HICN), Optional.of(TEST_MBI));
        final Consent result = ConsentEntityConverter.toFHIR(ce, TEST_DPC_URL, TEST_FHIR_URL);

        assertNotNull(result);
        assertEquals(Consent.ConsentState.ACTIVE, result.getStatus());
        assertEquals(ConsentEntity.CATEGORY_LOINC_CODE, result.getCategoryFirstRep().getCodingFirstRep().getCode());
        assertEquals(ConsentEntity.CATEGORY_DISPLAY, result.getCategoryFirstRep().getCodingFirstRep().getDisplay());
        assertEquals(TEST_FHIR_URL + "/Patient?identity=|" + TEST_MBI, result.getPatient().getReference());
        assertEquals(TEST_DPC_URL, result.getOrganization().get(0).getReference());
        assertEquals(ConsentEntityConverter.OPT_IN_MAGIC, result.getPolicyRule());
        assertTrue(result.getPolicy().isEmpty());
        assertDoesNotThrow(() -> FhirContext.forDstu3().newJsonParser().encodeResourceToString(result));
    }

    @Test
    final void convert_correctlyConverts_fromOptOutEntity() {
        ConsentEntity ce = ConsentEntity.defaultConsentEntity(Optional.empty(), Optional.of(TEST_HICN), Optional.of(TEST_MBI));
        ce.setPolicyCode(ConsentEntity.OPT_OUT);
        final Consent result = ConsentEntityConverter.toFHIR(ce, TEST_DPC_URL, TEST_FHIR_URL);

        assertEquals(ConsentEntityConverter.OPT_OUT_MAGIC, result.getPolicyRule());
        assertDoesNotThrow(() -> {
            FhirContext.forDstu3().newJsonParser().encodeResourceToString(result);
        });
    }

    @Test
    final void convert_correctlyConverts_fromCustodianEntity() {
        ConsentEntity ce = ConsentEntity.defaultConsentEntity(Optional.empty(), Optional.of(TEST_HICN), Optional.of(TEST_MBI));
        UUID uuid = UUID.randomUUID();
        ce.setCustodian(uuid);
        final Consent result = ConsentEntityConverter.toFHIR(ce, TEST_DPC_URL, TEST_FHIR_URL);

        assertEquals("Organization/" + uuid.toString(), result.getOrganizationFirstRep().getReference());
        assertDoesNotThrow(() -> {
            FhirContext.forDstu3().newJsonParser().encodeResourceToString(result);
        });
    }

    @Test
    final void convert_correctlyThrows_whenNoMbi() {
        ConsentEntity ce = ConsentEntity.defaultConsentEntity(Optional.empty(), Optional.of(TEST_HICN), Optional.of(TEST_MBI));
        ce.setMbi(null);

        assertThrows(IllegalArgumentException.class, () -> ConsentEntityConverter.toFHIR(ce, TEST_DPC_URL, TEST_FHIR_URL), "should throw an error with invalid data");
    }

    @Test
    final void convert_correctlyThrows_whenEntityHasInvalidData() {
        ConsentEntity ce = ConsentEntity.defaultConsentEntity(Optional.empty(), Optional.of(TEST_HICN), Optional.of(TEST_MBI));
        ce.setPolicyCode("BANANA");

        assertThrows(IllegalArgumentException.class, () -> ConsentEntityConverter.toFHIR(ce, TEST_DPC_URL, TEST_FHIR_URL), "should throw an error with invalid data");
    }

    @Test
    void testSimpleRoundTrip() {
        final ConsentEntity ce = ConsentEntity.defaultConsentEntity(Optional.empty(), Optional.of(TEST_HICN), Optional.of(TEST_MBI));
        final Consent consent = ConsentEntityConverter.toFHIR(ce, TEST_DPC_URL, TEST_FHIR_URL);
        final ConsentEntity ce2 = ConsentEntityConverter.fromFHIR(consent);
        assertAll(() -> assertEquals(ce.getId(), ce2.getId(), "Should have ID"),
                () -> assertEquals(ce.getMbi(), ce2.getMbi(), "Should have MBI"),
                () -> assertEquals(ce.getEffectiveDate(), ce2.getEffectiveDate(), "Should have correct date"),
                () -> assertNull(ce2.getCustodian(), "Should not have organization"));
    }

    @Test
    void testRoundTripWithId() {
        final ConsentEntity ce = ConsentEntity.defaultConsentEntity(Optional.empty(), Optional.of(TEST_HICN), Optional.of(TEST_MBI));
        final UUID orgID = UUID.randomUUID();
        ce.setId(orgID);
        final Consent consent = ConsentEntityConverter.toFHIR(ce, TEST_DPC_URL, TEST_FHIR_URL);
        final ConsentEntity ce2 = ConsentEntityConverter.fromFHIR(consent);
        assertAll(() -> assertEquals(ce.getId(), ce2.getId(), "Should have ID"),
                () -> assertEquals(ce.getMbi(), ce2.getMbi(), "Should have MBI"),
                () -> assertEquals(ce.getEffectiveDate(), ce2.getEffectiveDate(), "Should have correct date"),
                () -> assertEquals(orgID, ce2.getId(), "Should have matching organization"),
                () -> assertNull(ce2.getCustodian(), "Should not have organization"));
    }

    @Test
    void testRoundTripWithOrganization() {
        final ConsentEntity ce = ConsentEntity.defaultConsentEntity(Optional.empty(), Optional.of(TEST_HICN), Optional.of(TEST_MBI));
        final UUID orgID = UUID.randomUUID();
        ce.setCustodian(orgID);
        final Consent consent = ConsentEntityConverter.toFHIR(ce, TEST_DPC_URL, TEST_FHIR_URL);
        final ConsentEntity ce2 = ConsentEntityConverter.fromFHIR(consent);
        assertAll(() -> assertEquals(ce.getId(), ce2.getId(), "Should have ID"),
                () -> assertEquals(ce.getMbi(), ce2.getMbi(), "Should have MBI"),
                () -> assertEquals(ce.getEffectiveDate(), ce2.getEffectiveDate(), "Should have correct date"),
                () -> assertEquals(orgID, ce2.getCustodian(), "Should not have organization"));
    }
}
