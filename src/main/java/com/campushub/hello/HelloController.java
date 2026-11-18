package com.campushub.hello;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Smoke-test endpoint for checking a deploy end to end (browser → nginx → /api → Spring).
 */
@RestController
@RequestMapping("/api/hello")
public class HelloController {

	public record HelloResponse(String message) {
	}

	@GetMapping
	public HelloResponse hello() {
		return new HelloResponse("Hello World from CampusHub!");
	}

}
