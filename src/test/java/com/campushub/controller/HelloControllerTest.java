package com.campushub.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

// Full context (not @WebMvcTest) so the real SecurityConfig is used: /api/hello must stay open without a token.
@SpringBootTest
@AutoConfigureMockMvc
class HelloControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void returnsHelloMessageWithoutToken() throws Exception {
		mockMvc.perform(get("/api/hello"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("Hello World from CampusHub!"));
	}

}
