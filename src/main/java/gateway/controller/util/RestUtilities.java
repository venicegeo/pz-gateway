package gateway.controller.util;

import model.response.ErrorResponse;
import model.response.PiazzaResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import util.PiazzaLogger;

/**
 * Utility class that defines common procedures for handling requests,
 * responses, and brokered end points to internal Piazza components.
 * 
 * @author Patrick.Doody
 *
 */
@Component
public class RestUtilities {
	@Autowired
	private PiazzaLogger logger;

	/**
	 * Creates a commonly formatted Error Response that indicates the
	 * information of some internal error within the Piazza Gateway.
	 * 
	 * @return Response Entity, containing the details of an ErrorResponse
	 *         object.
	 */
	public static ResponseEntity<PiazzaResponse> generateErrorResponse(String jobId, String message, String origin,
			HttpStatus httpStatus) {
		// Log the Error

		// Create the Error Response model
		ErrorResponse errorResponse = new ErrorResponse(jobId, message, origin);
		// Return the Entity
		return new ResponseEntity<PiazzaResponse>(errorResponse, httpStatus);
	}
}
