package main.java.gateway.auth;

import model.job.PiazzaJob;

public class AuthConnector {

	/**
	 * Determines if the user is able to make the specified request or not.
	 * 
	 * @param apiKey
	 *            API Key of user
	 * @param job
	 *            The job the user wishes to perform.
	 * @throws Exception
	 *             Authorization exception if the user is not allowed.
	 */
	public static void verifyAuth(String apiKey, PiazzaJob job)
			throws SecurityException {
		if (!(isAuthenticated(apiKey)) || !(isAuthorized(apiKey, job))) {
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
	private static Boolean isAuthorized(String apiKey, PiazzaJob job) {
		return true;
	}
}
