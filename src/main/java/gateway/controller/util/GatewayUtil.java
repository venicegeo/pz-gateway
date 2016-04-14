package gateway.controller.util;

import java.security.Principal;

import javax.annotation.PostConstruct;

import messaging.job.KafkaClientFactory;
import model.response.ErrorResponse;
import model.response.PiazzaResponse;

import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import util.PiazzaLogger;
import util.UUIDFactory;

/**
 * Utility class that defines common procedures for handling requests,
 * responses, and brokered end points to internal Piazza components.
 * 
 * @author Patrick.Doody
 *
 */
@Component
public class GatewayUtil {
	@Autowired
	private UUIDFactory uuidFactory;
	@Autowired
	PiazzaLogger logger;
	@Value("${vcap.services.pz-kafka.credentials.host}")
	private String KAFKA_ADDRESS;
	@Value("${kafka.group}")
	private String KAFKA_GROUP;

	private Producer<String, String> producer;

	/**
	 * Initializing the Kafka Producer on Controller startup.
	 */
	@PostConstruct
	public void init() {
		// Kafka Producer.
		producer = KafkaClientFactory.getProducer(KAFKA_ADDRESS.split(":")[0], KAFKA_ADDRESS.split(":")[1]);
	}

	/**
	 * Sends a message to Kafka. This will additionally invoke .get() on the
	 * message sent, which will block until the acknowledgement from Kafka has
	 * been received that the message entered the Kafka queue.
	 * 
	 * @param message
	 *            The message to send.
	 * @throws Exception
	 *             Any exceptions encountered with the send.
	 */
	public void sendKafkaMessage(ProducerRecord<String, String> message) throws Exception {
		producer.send(message).get();
	}

	/**
	 * Gets a UUID from the Piazza UUID Factory.
	 * 
	 * @return UUID
	 */
	public String getUuid() throws Exception {
		try {
			return uuidFactory.getUUID();
		} catch (Exception exception) {
			throw new Exception(String.format("Could not connect to UUID Service for UUID: %s", exception.getMessage()));
		}
	}

	/**
	 * Safely returns the name of the user who has performed a request to a
	 * Gateway endpoint.
	 * 
	 * @param user
	 *            The principal
	 * @return The username. If the request was not authenticated, then that
	 *         will be returned.
	 */
	public String getPrincipalName(Principal user) {
		return user != null ? user.getName() : "UNAUTHENTICATED";
	}
}
