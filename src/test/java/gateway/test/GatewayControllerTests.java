package gateway.test;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;
import gateway.controller.GatewayController;
import model.job.Job;
import model.job.JobProgress;
import model.job.type.GetJob;
import model.request.PiazzaJobRequest;
import model.response.JobStatusResponse;
import model.response.PiazzaResponse;
import model.status.StatusUpdate;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.client.RestTemplate;

import util.PiazzaLogger;
import util.UUIDFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Tests the Gateway Controller.
 * 
 * @author Patrick.Doody
 * 
 */
public class GatewayControllerTests {
	@Mock
	private PiazzaLogger logger;
	@Mock
	private UUIDFactory uuidFactory;
	@Mock
	private RestTemplate restTemplate;
	@InjectMocks
	private GatewayController gatewayController;

	private Job mockIngestJob;

	@Before
	public void setup() {
		MockitoAnnotations.initMocks(this);

		// Mock an Ingest Job
		mockIngestJob = new Job();
		mockIngestJob.jobId = "Test-Job-ID";
		mockIngestJob.status = StatusUpdate.STATUS_RUNNING;
		mockIngestJob.progress = new JobProgress(50);
	}

	/**
	 * Tests the fetching of a Job Status for our Mock Ingest Job.
	 * 
	 * @throws JsonProcessingException
	 */
	@Test
	public void jobStatusTest() throws JsonProcessingException {
		// Mocking some Data inputs for fetching the Status Response of a Job.
		JobStatusResponse mockResponse = new JobStatusResponse(mockIngestJob);
		PiazzaJobRequest mockRequest = new PiazzaJobRequest();
		mockRequest.apiKey = "Api-Key";
		mockRequest.jobType = new GetJob(mockIngestJob.jobId);
		String request = new ObjectMapper().writeValueAsString(mockRequest);

		// Injecting mock values
		when(restTemplate.getForObject(anyString(), eq(PiazzaResponse.class))).thenReturn(mockResponse);

		// Testing the Job Status Response to Ensure equality with our Mock Data
		PiazzaResponse response = gatewayController.job(request, null);
		assertTrue(response.getType().equals(mockResponse.getType()));
		JobStatusResponse jobResponse = (JobStatusResponse) response;
		assertTrue(jobResponse.jobId.equals(mockIngestJob.jobId));
		assertTrue(jobResponse.progress.getPercentComplete().equals(mockIngestJob.progress.getPercentComplete()));
		assertTrue(jobResponse.status.equals(StatusUpdate.STATUS_RUNNING));
	}
}
