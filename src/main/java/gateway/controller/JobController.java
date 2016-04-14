package gateway.controller;

import model.response.PiazzaResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller that defines REST end points dealing with Job interactions such as
 * retrieving Job status, or executing Jobs.
 * 
 * @author Patrick.Doody
 *
 */
@CrossOrigin
@RestController
public class JobController {
	/**
	 * Returns the Status of a Job.
	 * 
	 * @see https://github.com/venicegeo/venice/wiki/Pz-Gateway#job-status
	 * 
	 * @param jobId
	 *            The ID of the Job.
	 * @return The response. Contains Job Status, or an appropriate Error.
	 */
	@RequestMapping(value = "/job/{jobId}", method = RequestMethod.GET)
	public ResponseEntity<PiazzaResponse> getJobStatus(@PathVariable(value = "jobId") String jobId) {
		try {

		} catch (Exception exception) {
			
		}
	}
}
