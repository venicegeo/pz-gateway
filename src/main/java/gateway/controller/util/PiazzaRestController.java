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
package gateway.controller.util;

import org.springframework.web.bind.annotation.RestController;

/**
 * This controller manages controller errors, including missing endpoints and
 * general spring exceptions which may occur during
 * serialization/deserialization. Controllers will extend this class in order to
 * get standardized error handling automatically injected into their endpoints.
 * 
 * @author Sonny.Saniev
 *
 */
@RestController
public class PiazzaRestController {
	/**
	 * Defines a generic error handler that Spring will use to form a REST
	 * response to the user when some servlet processing exception has occurred.
	 * This enables Piazza controllers to wrap generic Spring errors in our
	 * PiazzaResponse interface; so that all responses from Piazza become
	 * standardized.
	 * 
	 * @param exception
	 *            The exception that has occurred
	 * @return Appropriate Piazza Error response
	 */
	/*
	@ExceptionHandler(Exception.class)
	@ResponseStatus(value = HttpStatus.BAD_REQUEST)
	@ResponseBody
	public ResponseEntity<PiazzaResponse> genericHandler(Exception exception) {
		String message = String.format("Piazza Exception occurred: %s", exception.getMessage());
		return new ResponseEntity<PiazzaResponse>(new ErrorResponse(null, message, "Gateway"), HttpStatus.BAD_REQUEST);
	}*/
}
