package gov.cms.dpc.attribution.service;

import ca.uhn.fhir.context.FhirContext;
import gov.cms.dpc.attribution.exceptions.DataRetrievalException;
import gov.cms.dpc.attribution.exceptions.DataRetrievalRetryException;
import gov.cms.dpc.common.annotations.ExportPath;
import gov.cms.dpc.queue.IJobQueue;
import gov.cms.dpc.queue.JobStatus;
import gov.cms.dpc.queue.models.JobQueueBatch;
import gov.cms.dpc.queue.models.JobQueueBatchFile;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.OperationOutcome;
import org.hl7.fhir.dstu3.model.Resource;
import org.hl7.fhir.dstu3.model.ResourceType;

import javax.inject.Inject;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class DataService {

    private static final int JOB_POLLING_TIMEOUT = 3 * 5;

    private IJobQueue queue;
    private String exportPath;
    private FhirContext fhirContext;

    @Inject
    public DataService(IJobQueue queue, FhirContext fhirContext, @ExportPath String exportPath) {
        this.queue = queue;
        this.fhirContext = fhirContext;
        this.exportPath = exportPath;
    }

    public Resource retrieveData(UUID organizationID, UUID providerID, List<String> patientIDs, ResourceType... resourceTypes) {
        UUID jobID = this.queue.createJob(organizationID, providerID.toString(), patientIDs, List.of(resourceTypes));
        Optional<List<JobQueueBatch>> optionalBatches = waitForJobToComplete(jobID, organizationID, this.queue);

        if (optionalBatches.isEmpty()) {
            throw new DataRetrievalException("Failed to retrieve data");
        }

        List<JobQueueBatch> batches = optionalBatches.get();
        List<JobQueueBatchFile> files = batches.stream().map(JobQueueBatch::getJobQueueBatchFiles).flatMap(List::stream).collect(Collectors.toList());
        if (files.size() == 1 && files.get(0).getResourceType() == ResourceType.OperationOutcome) {
            return assembleOperationOutcome(batches);
        } else {
            return assembleBundleFromBatches(batches, Arrays.asList(resourceTypes));
        }
    }

    Optional<List<JobQueueBatch>> waitForJobToComplete(UUID jobID, UUID organizationID, IJobQueue queue) {
        CompletableFuture<Optional<List<JobQueueBatch>>> finalStatusFuture = new CompletableFuture<>();
        final ScheduledExecutorService poller = Executors.newSingleThreadScheduledExecutor();
        final ScheduledFuture<?> task = poller.scheduleAtFixedRate(() -> {
            try {
                List<JobQueueBatch> batches = getJobBatch(jobID, organizationID, queue);
                finalStatusFuture.complete(Optional.of(batches));
            } catch (DataRetrievalRetryException e) {
                //retrying
            }
        }, 0, 250, TimeUnit.MILLISECONDS);

        // this timeout value should probably be adjusted according to the number of types being requested
        finalStatusFuture.completeOnTimeout(Optional.empty(), JOB_POLLING_TIMEOUT, TimeUnit.SECONDS);

        try {
            return finalStatusFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            return Optional.empty();
        } finally {
            task.cancel(true);
            poller.shutdown();
        }
    }

    List<JobQueueBatch> getJobBatch(UUID jobID, UUID organizationId, IJobQueue queue) throws DataRetrievalRetryException {
        final List<JobQueueBatch> batches = queue.getJobBatches(jobID);
        if (batches.isEmpty()) {
            throw new DataRetrievalRetryException();
        }

        Set<JobStatus> jobStatusSet = batches
                .stream()
                .filter(b -> b.getOrgID().equals(organizationId))
                .filter(JobQueueBatch::isValid)
                .map(JobQueueBatch::getStatus).collect(Collectors.toSet());

        if (jobStatusSet.size() == 1 && jobStatusSet.contains(JobStatus.COMPLETED)) {
            return batches;
        } else if (jobStatusSet.contains(JobStatus.FAILED)) {
            throw new DataRetrievalException("Failed to retrieve batches");
        } else {
            throw new DataRetrievalRetryException();
        }
    }

    private Bundle assembleBundleFromBatches(List<JobQueueBatch> batches, List<ResourceType> resourceTypes) {
        final Bundle bundle = new Bundle().setType(Bundle.BundleType.SEARCHSET);

        batches.stream()
                .map(JobQueueBatch::getJobQueueBatchFiles)
                .flatMap(List::stream)
                .filter(bf -> resourceTypes.contains(bf.getResourceType()))
                .forEach(batchFile -> {
                    java.nio.file.Path path = Paths.get(String.format("%s/%s.ndjson", exportPath, batchFile.getFileName()));
                    addResourceEntries(Resource.class, path, bundle);
                });


        // set a bundle id here? anything else?
        bundle.setId(UUID.randomUUID().toString());
        return bundle.setTotal(bundle.getEntry().size());
    }

    private void addResourceEntries(Class<? extends Resource> clazz, java.nio.file.Path path, Bundle bundle) {
        try (BufferedReader br = Files.newBufferedReader(path)) {
            br.lines().forEach(line -> {
                Resource r = fhirContext.newJsonParser().parseResource(clazz, line);
                bundle.addEntry().setResource(r);
            });
        } catch (IOException e) {
            throw new DataRetrievalException(String.format("Unable to read resource because %s", e.getMessage()));
        }
    }

    OperationOutcome assembleOperationOutcome(List<JobQueueBatch> batches) {
        // There is only ever 1 OperationOutcome file
        final Optional<JobQueueBatchFile> batchFile = batches.stream()
                .map(b -> b.getJobQueueFileLatest(ResourceType.OperationOutcome))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();


        if (batchFile.isEmpty()) {
            throw new DataRetrievalException("Failed to retrieve operationOutcome");
        }

        OperationOutcome outcome = new OperationOutcome();
        java.nio.file.Path path = Paths.get(String.format("%s/%s.ndjson", exportPath, batchFile.get().getFileName()));
        try (BufferedReader br = Files.newBufferedReader(path)) {
            br.lines()
                    .map(line -> fhirContext.newJsonParser().parseResource(OperationOutcome.class, line))
                    .map(OperationOutcome::getIssue)
                    .flatMap(List::stream)
                    .forEach(outcome::addIssue);
        } catch (IOException e) {
            throw new DataRetrievalException(String.format("Unable to read OperationOutcome because %s", e.getMessage()));
        }

        return outcome;
    }

}