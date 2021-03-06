package gov.cms.dpc.fhir.converters.entities;

import gov.cms.dpc.common.entities.EndpointEntity;
import gov.cms.dpc.common.entities.OrganizationEntity;
import gov.cms.dpc.fhir.FHIRExtractors;
import gov.cms.dpc.fhir.converters.FHIRConverter;
import gov.cms.dpc.fhir.converters.FHIREntityConverter;
import gov.cms.dpc.fhir.validations.profiles.EndpointProfile;
import org.hl7.fhir.dstu3.model.*;

import java.util.UUID;

public class EndpointEntityConverter implements FHIRConverter<Endpoint, EndpointEntity> {

    @Override
    public EndpointEntity fromFHIR(FHIREntityConverter converter, Endpoint resource) {
        final EndpointEntity entity = new EndpointEntity();

        if (resource.getId() == null) {
            entity.setId(UUID.randomUUID());
        } else {
            entity.setId(FHIRExtractors.getEntityUUID(resource.getId()));
        }

        entity.setName(resource.getName());
        entity.setAddress(resource.getAddress());
        entity.setStatus(resource.getStatus());

        final EndpointEntity.ConnectionType connectionType = new EndpointEntity.ConnectionType();
        connectionType.setSystem(resource.getConnectionType().getSystem());
        connectionType.setCode(resource.getConnectionType().getCode());
        entity.setConnectionType(connectionType);

        final OrganizationEntity org = new OrganizationEntity();
        if (resource.hasManagingOrganization()) {
            org.setId(FHIRExtractors.getEntityUUID(resource.getManagingOrganization().getReference()));
        }
        entity.setOrganization(org);

        return entity;
    }

    @Override
    public Endpoint toFHIR(FHIREntityConverter converter, EndpointEntity entity) {
        final Endpoint endpoint = new Endpoint();
        // Add profile
        final Meta meta = new Meta();
        meta.addProfile(EndpointProfile.PROFILE_URI);
        endpoint.setMeta(meta);

        endpoint.setId(new IdType("Endpoint", entity.getId().toString()));
        endpoint.setName(entity.getName());
        endpoint.setAddress(entity.getAddress());

        endpoint.setManagingOrganization(new Reference(new IdType("Organization", entity.getOrganization().getId().toString())));
        endpoint.setStatus(entity.getStatus());
        endpoint.setConnectionType(converter.toFHIR(Coding.class, entity.getConnectionType()));

        return endpoint;
    }

    @Override
    public Class<Endpoint> getFHIRResource() {
        return Endpoint.class;
    }

    @Override
    public Class<EndpointEntity> getJavaClass() {
        return EndpointEntity.class;
    }
}
