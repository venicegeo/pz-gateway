package gateway.auth;

import java.io.Serializable;

import javax.servlet.http.HttpServletRequest;

public class PiazzaDetails implements Serializable  {

	private static final long serialVersionUID = 1L;
	
	private HttpServletRequest request;
	
	public PiazzaDetails(HttpServletRequest h) {
		request = h;
	}
	
	public HttpServletRequest getRequest() {
		return request;
	}
}