package gov.cms.dpc.attribution.resources.v1;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import gov.cms.dpc.attribution.jdbi.EndpointDAO;
import gov.cms.dpc.attribution.jdbi.OrganizationDAO;
import gov.cms.dpc.attribution.resources.AbstractOrganizationResource;
import gov.cms.dpc.common.entities.EndpointEntity;
import gov.cms.dpc.common.entities.OrganizationEntity;
import gov.cms.dpc.fhir.FHIRExtractors;
import gov.cms.dpc.fhir.annotations.FHIR;
import gov.cms.dpc.fhir.annotations.FHIRParameter;
import gov.cms.dpc.fhir.converters.FHIREntityConverter;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.*;
import org.eclipse.jetty.http.HttpStatus;
import org.hl7.fhir.dstu3.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static gov.cms.dpc.attribution.utils.RESTUtils.parseTokenTag;

@Api(value = "Organization")
public class OrganizationResource extends AbstractOrganizationResource {

    private static final Logger logger = LoggerFactory.getLogger(OrganizationResource.class);

    private final OrganizationDAO dao;
    private final EndpointDAO endpointDAO;
    private final FHIREntityConverter converter;

    @Inject
    OrganizationResource(FHIREntityConverter converter, OrganizationDAO dao, EndpointDAO endpointDAO) {
        this.converter = converter;
        this.dao = dao;
        this.endpointDAO = endpointDAO;
    }

    @Override
    @GET
    @FHIR
    @UnitOfWork
    @ApiOperation(value = "Search for an Organization",
            notes = "FHIR Endpoint to find an Organization resource based on the given Identifier.", response = Bundle.class)
    public List<Organization> searchOrganizations(
            @ApiParam(value = "NPI of Organization")
            @QueryParam("identifier") String identifier) {

        if (identifier == null) {
            return this.dao.listOrganizations()
                    .stream()
                    .map(o -> this.converter.toFHIR(Organization.class, o))
                    .collect(Collectors.toList());
        }
        // Pull out the NPI, keeping it as a string.
        final List<OrganizationEntity> queryList = this.dao.searchByIdentifier(parseTokenTag((tag) -> tag, identifier));

        return queryList
                .stream()
                .map(o -> this.converter.toFHIR(Organization.class, o))
                .collect(Collectors.toList());
    }

    @Override
    @FHIR
    @UnitOfWork
    @Timed
    @ExceptionMetered
    @ApiOperation(value = "Create Organization", notes = "FHIR endpoint that accepts a Bundle resource containing an Organization and a list of Endpoint resources to register with the application")
    @ApiResponses(value = {
            @ApiResponse(code = 422, message = "Must provide a single Organization resource to register", response = OperationOutcome.class),
            @ApiResponse(code = 201, message = "Organization was successfully registered")
    })
    public Response submitOrganization(@FHIRParameter(name = "resource") Bundle transactionBundle) {

        final Optional<Organization> organization = transactionBundle
                .getEntry()
                .stream()
                .filter(entry -> entry.hasResource() && entry.getResource().getResourceType() == ResourceType.Organization)
                .map(entry -> (Organization) entry.getResource())
                .findFirst();

        if (organization.isEmpty()) {
            return Response.status(HttpStatus.UNPROCESSABLE_ENTITY_422).entity("Must provide organization to register").build();
        }

        final OrganizationEntity entity = this.converter.fromFHIR(OrganizationEntity.class, organization.get());
        final List<EndpointEntity> endpoints = extractEndpoints(transactionBundle);
        endpoints.forEach(endpointEntity -> endpointEntity.setOrganization(entity));
        entity.setEndpoints(endpoints);

        try {
            final OrganizationEntity persistedOrg = this.dao.registerOrganization(entity);
            return Response.status(Response.Status.CREATED).entity(this.converter.toFHIR(Organization.class, persistedOrg)).build();
        } catch (Exception e) {
            logger.error("Error: ", e);
            throw e;
        }
    }

    @GET
    @Path("/{organizationID}")
    @FHIR
    @UnitOfWork
    @Override
    @ApiOperation(value = "Fetch organization", notes = "FHIR endpoint to fetch an Organization with the given Resource ID")
    @ApiResponses(value = @ApiResponse(code = 404, message = "Could not find Organization", response = OperationOutcome.class))
    public Organization getOrganization(
            @ApiParam(value = "Organization resource ID", required = true)
            @PathParam("organizationID") UUID organizationID) {
        final Optional<OrganizationEntity> orgOptional = this.dao.fetchOrganization(organizationID);
        final OrganizationEntity organizationEntity = orgOptional.orElseThrow(() -> new WebApplicationException(String.format("Cannot find organization '%s'", organizationID), Response.Status.NOT_FOUND));
        return this.converter.toFHIR(Organization.class, organizationEntity);
    }

    @PUT
    @Path("/{organizationID}")
    @FHIR
    @UnitOfWork
    @ApiOperation(value = "Update Organization record", notes = "Update specific Organization record.")
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Unable to find Organization to update"),
            @ApiResponse(code = 422, message = "The provided Organization bundle is invalid")
    })
    @Override
    public Response updateOrganization(@ApiParam(value = "Organization resource ID", required = true) @PathParam("organizationID") UUID organizationID, Organization organization) {
        try {
            OrganizationEntity orgEntity = this.converter.fromFHIR(OrganizationEntity.class, organization);
            Organization original = getOrganization(organizationID);
            List<EndpointEntity> endpointEntities = original.getEndpoint().stream().map(
                    r -> {
                        UUID endpointID = FHIRExtractors.getEntityUUID(r.getReference());
                        return endpointDAO.fetchEndpoint(endpointID);
                    }
            ).filter(Optional::isPresent).map(Optional::get).collect(Collectors.toList());
            orgEntity.setEndpoints(endpointEntities);
            orgEntity = this.dao.updateOrganization(organizationID, orgEntity);
            return Response.status(Response.Status.OK).entity(this.converter.toFHIR(Organization.class, orgEntity)).build();
        } catch (Exception e) {
            logger.error("Error: ", e);
            throw e;
        }
    }

    @DELETE
    @Path("/{organizationID}")
    @FHIR
    @UnitOfWork
    @ApiOperation(value = "Delete organization", notes = "FHIR endpoint to delete an Organization with the given Resource ID." +
            "<p>This also drops ALL resources associated to the given entity.")
    @ApiResponses(value = @ApiResponse(code = 404, message = "Could not find Organization", response = OperationOutcome.class))
    @Override
    public Response deleteOrganization(@ApiParam(value = "Organization resource ID", required = true) @PathParam("organizationID") UUID organizationID) {
        final OrganizationEntity organizationEntity = this.dao.fetchOrganization(organizationID)
                .orElseThrow(() -> new WebApplicationException("Cannot find organization.", Response.Status.NOT_FOUND));

        this.dao.deleteOrganization(organizationEntity);
        return Response.ok().build();
    }

    private List<EndpointEntity> extractEndpoints(Bundle transactionBundle) {
        return transactionBundle
                .getEntry()
                .stream()
                .filter(entry -> entry.hasResource() && entry.getResource().getResourceType() == ResourceType.Endpoint)
                .map(entry -> (Endpoint) entry.getResource())
                .map(e -> this.converter.fromFHIR(EndpointEntity.class, e))
                .collect(Collectors.toList());
    }
}
