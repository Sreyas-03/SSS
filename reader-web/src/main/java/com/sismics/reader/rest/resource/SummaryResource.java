package com.sismics.reader.rest.resource;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import com.sismics.rest.exception.ForbiddenClientException;
import com.sismics.security.IPrincipal;
import com.sismics.util.filter.SecurityFilter;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import java.security.Principal;

@Path("/summary")
public class SummaryResource {
    @Context
    private ServletContext context;

    /**
     * Injects the HTTP request.
     */
    @Context
    protected HttpServletRequest request;

    /**
     * Principal of the authenticated user.
     */
    protected IPrincipal principal;

    // Replace with your Google Gemini API key
    private static final String GEMINI_API_KEY = "AIzaSyBnVWHl1mPL7bkJUX5KL9v1oR6hPXn7Czo";
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + GEMINI_API_KEY;

    private static final String MODEL = "models/embedding-001";

    /**
     * This method is used to check if the user is authenticated.
     *
     * @return True if the user is authenticated and not anonymous
     */
    private boolean authenticate() {
        Principal principal = (Principal) request.getAttribute(SecurityFilter.PRINCIPAL_ATTRIBUTE);
        if (principal != null && principal instanceof IPrincipal) {
            this.principal = (IPrincipal) principal;

            // // Logging principal information
            // System.out.println("Principal Details: " + this.principal);
            // System.out.println("Principal Details: " + this.principal.getName());


            return !this.principal.isAnonymous();
        } else {
            System.out.println("No valid principal found.");
            return false;
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSummary(String nestedCategoriesArticlesJson) {
        try {
            if (!authenticate()) {
                throw new ForbiddenClientException();
            }
            // JSONObject allArticles = new JSONObject(nestedCategoriesArticlesJson);
            // JSONArray allArticlesArray = allArticles.getJSONArray("articles");
            // JSONArray unreadArticlesArray = new JSONArray();

            // for (int i = 0; i < allArticlesArray.length(); i++) {
            //     JSONObject article = allArticlesArray.getJSONObject(i);

            //     // Check if "is_read" exists and is false
            //     if (article.has("is_read") && !article.getBoolean("is_read")) {
            //         unreadArticlesArray.put(article);
            //     }
            // }
            // add a prompt before adding the articles
            String unreadArticlesJson = "Generate a structured HTML report summarizing the latest updates from the user's subscriptions, focusing on overarching themes rather than individual articles.\n\n" +
            "Identify and summarize key insights, trends, and discussions within each category. Present a structured and readable output that captures the broader themes emerging from multiple articles.\n\n" +
            "Prioritize high-level categories with more detailed summaries while providing progressively shorter insights for deeper subcategories.\n\n" +
            "Generate summaries based on thematic importance:\n" +
            "- 5-line summary for broad themes in top-level categories.\n" +
            "- 4-line summary for second-level category themes.\n" +
            "- 3-line summary for third-level category themes.\n" +
            "- 2-line summary for fourth-level category themes.\n" +
            "- 1-line summary for fifth-level category themes.\n\n" +
            "Ensure the summary flows logically from major themes to sub-themes without explicitly mentioning category levels. Format the output using semantic HTML elements (`<p>`, `<a>`, `<br>`) for clarity and readability.\n\n" +
            "Use the following structured data as input:\n\n" + nestedCategoriesArticlesJson.toString();

            System.out.println("Unread Articles JSON: " + unreadArticlesJson);

            // 3. Pass the JSON *string* to getGeminiOutput
            String geminiResponse = getGeminiOutput(unreadArticlesJson);
            System.out.println("Gemini Response: " + geminiResponse);

            System.out.println("----------------------------------------------------------------------------------------------------Response : "+geminiResponse);

            JSONObject jsonObject = new JSONObject();
            jsonObject.put("summary", geminiResponse.toString());
            jsonObject.put("status", "success");

            return Response.ok(jsonObject.toString()).build();
        } catch (JSONException e) {
            return Response.serverError().entity("{\"error\": \"Failed to generate summary\"}").build();
        } catch (Exception e) {
            return Response.serverError().entity("{\"error\": \"Gemini API call failed\"}").build();
        }
    }

    public static String getGeminiOutput(String text) throws Exception {
        System.out.println(text);
    
        // Properly format the request body
        JSONObject requestBody = new JSONObject();
        JSONArray contentsArray = new JSONArray();
        
        JSONObject contentObject = new JSONObject();
        JSONArray partsArray = new JSONArray();
        
        JSONObject textObject = new JSONObject();
        textObject.put("text", text);
        
        partsArray.put(textObject);
        contentObject.put("parts", partsArray);
        
        contentsArray.put(contentObject);
        requestBody.put("contents", contentsArray);
    
        System.out.println("Request Body: " + requestBody.toString());
    
        System.out.println("URL: " + GEMINI_API_URL);

        // Use the correct API URL
        URL url = new URL(GEMINI_API_URL);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        System.out.println("Connection: " + connection);
        System.out.println("Connection is connected (more reliable): " + connection.usingProxy());
        // Send request
        try (OutputStream os = connection.getOutputStream()) {
            os.write(requestBody.toString().getBytes("UTF-8"));
        }
    
        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            throw new RuntimeException("Failed: HTTP error code: " + responseCode);
        }
        System.out.println("Response Code: " + responseCode);
    
        // Read response
        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
        }
        System.out.println("Response: " + response.toString());
    
        // Parse response JSON safely
        JSONObject jsonResponse = new JSONObject(response.toString());
    
        if (!jsonResponse.has("candidates")) {
            throw new RuntimeException("Invalid API response: 'candidates' field missing");
        }
    
        // Extract generated text
        return jsonResponse.getJSONArray("candidates")
                          .getJSONObject(0)
                          .getJSONObject("content")
                          .getJSONArray("parts")
                          .getJSONObject(0)
                          .getString("text");
    }
    

}
