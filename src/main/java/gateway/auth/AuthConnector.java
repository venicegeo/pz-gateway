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
package gateway.auth;

import model.request.PiazzaJobRequest;

public class AuthConnector {

	/**
	 * Determines if the user is able to make the specified request or not.
	 * 
	 * @param jobRequest
	 *            The job the user wishes to perform.
	 * @throws Exception
	 *             Authorization exception if the user is not allowed.
	 */
	public static void verifyAuth(PiazzaJobRequest jobRequest) throws SecurityException {
		if (!(isAuthenticated(jobRequest.apiKey)) || !(isAuthorized(jobRequest))) {
			throw new SecurityException("Not authorized.");
		}
	}

	/**
	 * Determines if the user is valid (authN)
	 * 
	 * @param apiKey
	 *            API Key of user
	 * @return True if the user is valid, false if not
	 */
	private static Boolean isAuthenticated(String apiKey) {
		return true;
	}

	/**
	 * Determines if the user has the permissions to make the specified request
	 * (authZ)
	 * 
	 * @param job
	 *            The job the user (with api key) wishes to perform
	 * @param request
	 *            True if the user is able to make the specified request job,
	 *            false if not
	 * @return
	 */
	private static Boolean isAuthorized(PiazzaJobRequest jobRequest) {
		return true;
	}
}
