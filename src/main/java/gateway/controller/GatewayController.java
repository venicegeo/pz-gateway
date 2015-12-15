package main.java.gateway.controller;

import java.io.IOException;
import java.util.Properties;

import javax.annotation.PostConstruct;

import main.java.gateway.auth.AuthConnector;
import model.job.type.GetJob;
import model.request.PiazzaRequest;
import model.response.ErrorResponse;
import model.response.JobStatusResponse;
import model.response.PiazzaResponse;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
public class GatewayController {
	/**
	 * The Kafka Producer that will send messages from this controller to the
	 * Dispatcher. Initialized upon Controller startup.
	 */
	Producer<String, String> producer;

	/**
	 * Initializing the Kafka Producer on Controller startup.
	 */
	@PostConstruct
	public void init() {
		// Initialize the Kafka Producer
		Properties props = new Properties();
		props.put("bootstrap.servers", "localhost:4242");
		props.put("acks", "all");
		props.put("retries", 0);
		props.put("batch.size", 16384);
		props.put("linger.ms", 1);
		props.put("buffer.memory", 33554432);
		props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
		props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

		producer = new KafkaProducer<String, String>(props);
	}

	/**
	 * Executes a Piazza Job
	 * 
	 * @param json
	 *            The JSON Payload
	 * @return Response object.
	 */
	@RequestMapping(value = "/job", method = RequestMethod.POST)
	public PiazzaResponse job(@RequestBody String json) {

		// Deserialize the incoming JSON to Request Model objects
		PiazzaRequest request;
		try {
			request = parseRequestJson(json);
		} catch (Exception exception) {
			return new ErrorResponse(null, "Error Parsing JSON: " + exception.getMessage(), "Gateway");
		}

		// Authenticate and Authorize the request
		try {
			AuthConnector.verifyAuth(request.apiKey, request.job);
		} catch (SecurityException securityEx) {
			return new ErrorResponse(null, "Authentication Error", "Gateway");
		}

		// Create a GUID for this Job.

		// Determine if this Job is processed via synchronous REST, or via Kafka
		// message queues.
		if (request.job instanceof GetJob) {
			// REST GET request to Dispatcher. Block until fulfilled.
			return new JobStatusResponse("TestJobID");
		} else {
			// Dispatch Kafka Message of Incoming Job
			return new PiazzaResponse("TestJobID");
		}
	}

	/**
	 * Parses the raw JSON Payload into the PiazzaRest backing models. No value
	 * validation done here, only syntax.
	 * 
	 * @param json
	 *            JSON Payload from POST RequestBody
	 * @return PiazzaRequest object for the JSON Payload.
	 * @throws Exception
	 */
	private PiazzaRequest parseRequestJson(String json) throws IOException, JsonParseException, JsonMappingException {
		PiazzaRequest request = new ObjectMapper().readValue(json, PiazzaRequest.class);
		return request;
	}
}