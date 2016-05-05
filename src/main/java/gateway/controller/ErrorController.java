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

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import model.response.ErrorResponse;
import model.response.PiazzaResponse;

/**
 * This rest controller manages controller errors, including missing endpoints 
 * and general spring exceptions which may occur during serialization/deserialization. 
 * 
 * @author Sonny.Saniev
 *
 */
@RestController
public class ErrorController {

	@ExceptionHandler(Exception.class)
	@ResponseStatus(value = HttpStatus.BAD_REQUEST)
	@ResponseBody
	public ResponseEntity<PiazzaResponse> genericHandler(Exception e) {
		String message = String.format("%s%s", "Piazza exception occurred. ", e.toString());
		return new ResponseEntity<PiazzaResponse>(new ErrorResponse(null, message, "Gateway"), HttpStatus.BAD_REQUEST);
	}
}
