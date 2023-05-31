package com.github.hakenadu.javalangchains.chains.llm.openai.chat;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonCreator.Mode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.hakenadu.javalangchains.chains.llm.openai.OpenAiResponse;

/**
 * Model class for the response body of an OpenAI /chat/completions request
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class OpenAiChatCompletionsResponse extends OpenAiResponse<OpenAiChatCompletionsChoice> {

	/**
	 * @param choices {@link #getChoices()}
	 */
	@JsonCreator(mode = Mode.PROPERTIES)
	public OpenAiChatCompletionsResponse(final @JsonProperty("choices") List<OpenAiChatCompletionsChoice> choices) {
		super(choices);
	}
}
