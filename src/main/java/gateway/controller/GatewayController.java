package main.java.gateway.controller;

import java.io.IOException;
import java.util.Properties;
import java.util.UUID;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import main.java.gateway.auth.AuthConnector;
import messages.job.JobMessageFactory;
import model.job.type.GetJob;
import model.request.PiazzaRequest;
import model.response.ErrorResponse;
import model.response.JobStatusResponse;
import model.response.PiazzaResponse;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
public class GatewayController {
	/**
	 * The Kafka Producer that will send messages from this controller to the
	 * Dispatcher. Initialized upon Controller startup.
	 */
	private Producer<String, String> producer;

	/**
	 * Initializing the Kafka Producer on Controller startup.
	 */
	@PostConstruct
	public void init() {
		// Initialize the Kafka Producer
		Properties props = new Properties();
		props.put("bootstrap.servers", "kafka.dev:9092");
		props.put("acks", "all");
		props.put("retries", 0);
		props.put("batch.size", 16384);
		props.put("linger.ms", 1);
		props.put("buffer.memory", 33554432);
		props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
		props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

		producer = new KafkaProducer<String, String>(props);
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
		String guid = UUID.randomUUID().toString();
		request.job.setJobId(guid);

		// Determine if this Job is processed via synchronous REST, or via Kafka
		// message queues.
		if (request.job instanceof GetJob) {
			// REST GET request to Dispatcher. Block until fulfilled.

			// TODO
			return new JobStatusResponse(guid);
		} else {
			// Create the Kafka Message for an incoming Job to be created.
			ProducerRecord<String, String> message;
			try {
				message = JobMessageFactory.getJobMessage(request.job);
			} catch (JsonProcessingException exception) {
				return new ErrorResponse(null, "Error Creating Message for Job", "Gateway");
			}
			// Dispatch the Kafka Message
			producer.send(message);

			// Respond immediately with the new Job GUID
			return new PiazzaResponse(guid);
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