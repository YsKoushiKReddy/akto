package com.akto.gpt.handlers.gpt_prompts;

import com.akto.data_actor.DataActorFactory;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.BasicDBObject;
import javax.validation.ValidationException;
import org.json.JSONObject;

public abstract class PromptHandler {

    private static final LoggerMaker logger = new LoggerMaker(PromptHandler.class, LogDb.DASHBOARD);
    private static final String OLLAMA_MODEL = "llama3:8b";
    private static final Double temperature = 0.1;
    private static final int max_tokens = 4000;
    private static final Object llmLock = new Object();

    /**
     * Process the input query data and return a String response.
     */
    public BasicDBObject handle(BasicDBObject queryData) {
        try {
            validate(queryData);
            String prompt = getPrompt(queryData);
            String rawResponse = call(prompt, OLLAMA_MODEL, temperature, max_tokens);
            BasicDBObject resp = processResponse(rawResponse);
            return resp;
        } catch (ValidationException exception) {
            logger.error("Validation error: " + exception.getMessage());
            BasicDBObject resp = new BasicDBObject();
            resp.put("error", "Invalid input parameters.");
            return resp;
        } catch (Exception e) {
            logger.error("Error while handling request: " + e);
            BasicDBObject resp = new BasicDBObject();
            resp.put("error", "Internal server error" + e.getMessage());
            return resp;
        }
    }

    /**
     * Validate input parameters.
     */
    protected abstract void validate(BasicDBObject queryData) throws ValidationException;

    /**
     * Return the prompt string to be sent to the AI.
     */
    protected abstract String getPrompt(BasicDBObject queryData);

    /**
     * Call the AI model with the provided prompt and parameters
     */
    protected String call(String prompt, String model, Double temperature, int maxTokens) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("model", model);
        payload.put("prompt", prompt);
        payload.put("temperature", temperature);
        payload.put("max_tokens", maxTokens);
        payload.put("top_p", 0.9); // Added top_p
        payload.put("top_k", 50); // Added top_k
        payload.put("repeat_penalty", 1.1); // Penalize repetitions
        payload.put("presence_penalty", 0.6); // Discourage new topic jumps
        payload.put("frequency_penalty", 0.0); // Don't punish frequency
        payload.put("stream", false);

        synchronized (llmLock) {
            return DataActorFactory.fetchInstance().getLLMPromptResponse(payload);
        }
    }

    /**
     * Process the raw response (e.g., clean answer).
     */
    protected abstract BasicDBObject processResponse(String rawResponse);

    static String cleanJSON(String rawResponse) {
        if (rawResponse == null || rawResponse.isEmpty()) {
            return "NOT_FOUND";
        }

        // Truncate at the last closing brace to remove any trailing notes
        int lastBrace = rawResponse.lastIndexOf('}');
        if (lastBrace != -1) {
            rawResponse = rawResponse.substring(0, lastBrace + 1);
        }

        // Start at the first opening brace to remove any forward notes
        int firstBrace = rawResponse.indexOf('{');
        if (firstBrace != -1) {
            rawResponse = rawResponse.substring(firstBrace);
        }
        return rawResponse.trim();
    }

    static String processOutput(String rawResponse) {
        try {

            rawResponse = cleanJSON(rawResponse);

            JSONObject jsonResponse = new JSONObject(rawResponse);
            String cleanResponse = jsonResponse.getString("response");
    
            // Remove <think> tags
            cleanResponse = cleanResponse.replaceAll("(?s)<think>.*?</think>", "").trim();
    
            // If wrapped in escaped quotes, unescape it
            if (cleanResponse.startsWith("\"") && cleanResponse.endsWith("\"")) {
                cleanResponse = cleanResponse.substring(1, cleanResponse.length() - 1)
                                             .replace("\\\"", "");
            }
    
            return cleanResponse.trim();
        } catch (Exception e) {
            logger.error("Failed to clean LLM response: " + rawResponse, e);
            return "NOT_FOUND";
        }
    }
}
