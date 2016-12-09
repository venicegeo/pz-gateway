package gateway.auth;

import java.io.Serializable;

import javax.servlet.http.HttpServletRequest;

/**
 * Details that are attached to an Authorization request processed by the PiazzaBasicAuthenticationProvider. This allows
 * us to inject information into the authorize() call processed by that component, such as the HTTP Request information,
 * that would not normally be included in the default details object.
 * 
 * @author Patrick.Doody
 *
 */
public class ExtendedRequestDetails implements Serializable {
	private static final long serialVersionUID = 1L;

	private HttpServletRequest request;

	public ExtendedRequestDetails(HttpServletRequest request) {
		this.request = request;
	}

	/**
	 * Gets the Request Details
	 * 
	 * @return The request details.
	 */
	public HttpServletRequest getRequest() {
		return request;
	}
}