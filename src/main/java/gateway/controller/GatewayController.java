/**
 * Copyright 2016, RadiantBlue Technologies, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
package gateway.controller;

import gateway.auth.AuthConnector;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import messaging.job.JobMessageFactory;
import messaging.job.KafkaClientFactory;
import model.job.type.GetJob;
import model.job.type.GetResource;
import model.job.type.IngestJob;
import model.request.PiazzaJobRequest;
import model.response.ErrorResponse;
import model.response.PiazzaResponse;

import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.util.StringUtils;
import com.fasterxml.jackson.core.JsonProcessingException;

@RestController
public class GatewayController {
	/**
	 * The Kafka Producer that will send messages from this controller to the
	 * Dispatcher. Initialized upon Controller startup.
	 */
	private Producer<String, String> producer;
	@Value("${kafka.host}")
	private String KAFKA_HOST;
	@Value("${kafka.port}")
	private String KAFKA_PORT;
	@Value("${kafka.group}")
	private String KAFKA_GROUP;
	@Value("${dispatcher.host}")
	private String DISPATCHER_HOST;
	@Value("${dispatcher.port}")
	private String DISPATCHER_PORT;
	@Value("${amazons3.bucketname}")
	private String AMAZONS3_BUCKET_NAME;
	@Value("${amazons3.domain}")
	private String AMAZONS3_DOMAIN;
	
	/**
	 * Initializing the Kafka Producer on Controller startup.
	 */
	@PostConstruct
	public void init() {
		producer = KafkaClientFactory.getProducer(KAFKA_HOST, KAFKA_PORT);
	}

	@PreDestroy
	public void cleanup() {
		producer.close();
	}

	/**
	 * Executes a Piazza Job
	 * 
	 * @param json
	 *            The JSON Payload
	 * @return Response object.
	 */
	@RequestMapping(value = "/job", method = RequestMethod.POST)
	public PiazzaResponse job(@RequestParam(required = true) String body,
			@RequestParam(required = false) final MultipartFile file) {

		// Deserialize the incoming JSON to Request Model objects
		PiazzaJobRequest request;
		try {
			request = JobMessageFactory.parseRequestJson(body);
		} 
		catch (Exception exception) {
			return new ErrorResponse(null, "Error Parsing JSON: " + exception.getMessage(), "Gateway");
		}

		// Authenticate and Authorize the request
		try {
			AuthConnector.verifyAuth(request);
		} 
		catch (SecurityException securityEx) {
			return new ErrorResponse(null, "Authentication Error", "Gateway");
		}

		// Determine if this Job is processed via synchronous REST, or via Kafka message queues.
		if ((request.jobType instanceof GetJob) || (request.jobType instanceof GetResource)) {
			return sendRequestToDispatcherViaRest(request);
		} 
		else {
			return sendRequestToDispatcherViaKafka(request, file);
		}
	}
	
	private PiazzaResponse sendRequestToDispatcherViaRest(PiazzaJobRequest request) {
		// REST GET request to Dispatcher to fetch the status of the Job ID.
		// TODO: I would like a way to normalize this.
		String id = null, serviceName = null;
		if (request.jobType instanceof GetJob) {
			id = ((GetJob) request.jobType).getJobId();
			serviceName = "job";
		} else if (request.jobType instanceof GetResource) {
			id = ((GetResource) request.jobType).getResourceId();
			serviceName = "data";
		}
		try {
			PiazzaResponse dispatcherResponse = new RestTemplate().getForObject(
					String.format("http://%s:%s/%s/%s", DISPATCHER_HOST, DISPATCHER_PORT, serviceName, id),
					PiazzaResponse.class);
			return dispatcherResponse;
		} catch (RestClientException exception) {
			return new ErrorResponse(null, "Error connecting to Dispatcher service: " + exception.getMessage(),
					"Gateway");
		}		
	}
	
	private PiazzaResponse sendRequestToDispatcherViaKafka(PiazzaJobRequest request, MultipartFile file) {
		// Create a GUID for this new Job.
		String jobId = UUID.randomUUID().toString();
		
		// If an ingest job, persist the file to the Amazon S3 filesystem
		if( request.jobType instanceof IngestJob && file != null) {
			try {		
				AmazonS3 client = new AmazonS3Client();
				client.setEndpoint(AMAZONS3_BUCKET_NAME + AMAZONS3_DOMAIN);
				client.setRegion(Region.getRegion(Regions.US_EAST_1));
				client.putObject(AMAZONS3_BUCKET_NAME, file.getOriginalFilename(), file.getInputStream(), new ObjectMetadata());				
	        } 
			catch (AmazonServiceException ase) {
	            System.out.println("Error Message:    " + ase.getMessage());
	            System.out.println("HTTP Status Code: " + ase.getStatusCode());
	            System.out.println("AWS Error Code:   " + ase.getErrorCode());
	            System.out.println("Error Type:       " + ase.getErrorType());
	            System.out.println("Request ID:       " + ase.getRequestId());			
				return new ErrorResponse(null, "Caught an AmazonServiceException, which " +
	            		"means your request made it " +
	                    "to Amazon S3, but was rejected with an error response" +
	                    " for some reason.", "Gateway");
	
	        } 
			catch (AmazonClientException ace) {
	            System.out.println("Error Message: " + ace.getMessage());
	            ace.printStackTrace();
				return new ErrorResponse(null, "Caught an AmazonClientException, which " +
	            		"means the client encountered " +
	                    "an internal error while trying to " +
	                    "communicate with S3, " +
	                    "such as not being able to access the network.", "Gateway");
	        } 
			catch (IllegalStateException | IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		// Create the Kafka Message for an incoming Job to be created.
		final ProducerRecord<String, String> message;
		try {
			message = JobMessageFactory.getRequestJobMessage(request, jobId);
		} catch (JsonProcessingException exception) {
			return new ErrorResponse(jobId, "Error Creating Message for Job", "Gateway");
		}

		System.out.println("Requesting Job topic " + message.topic() + " with key " + message.key());

		// Fire off a Kafka Message and then wait for a ack response from the kafka broker
		try {
			producer.send(message).get();
		} catch (Exception exception) {
			return new ErrorResponse(
					jobId,
					"The Gateway did not receive a response from Kafka; the request could not be forwarded along to Piazza.",
					"Gateway");
		}

		// Respond immediately with the new Job GUID
		return new PiazzaResponse(jobId);		
	}
}