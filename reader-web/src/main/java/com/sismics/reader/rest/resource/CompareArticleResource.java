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
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.Principal;

@Path("/compareArticle")
public class CompareArticleResource {

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
    private static final String GEMINI_API_KEY = "AIzaSyBqqT1xFUv4iMDViJ8dIJHlD_kM7_T0fE4";
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/embedding-001:embedContent?key=" + GEMINI_API_KEY;
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
            return !this.principal.isAnonymous();
        } else {
            System.out.println("No valid principal found.");
            return false;
        }
    }


    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response compareArticles(String articlesJson) {
        try {
            if (!authenticate()) {
                throw new ForbiddenClientException();
            }

            JSONArray articles = new JSONArray(articlesJson);

            if (articles.length() != 2) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("{\"message\": \"Requires 2 articles, but got " + articles.length() + " articles\"}")
                        .build();
            }

            JSONObject article1 = articles.getJSONObject(0);
            JSONObject article2 = articles.getJSONObject(1);

            String title1 = article1.optString("title");
            String title2 = article2.optString("title");

            if (title1 == null || title2 == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("{\"message\": \"One or both articles are missing the 'title' field.\"}")
                        .build();
            }

            // Get embeddings and calculate cosine similarity
            double[] embedding1 = getEmbedding(title1);
            double[] embedding2 = getEmbedding(title2);

            if (embedding1 == null || embedding2 == null) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity("{\"message\": \"Failed to get embeddings from Google Gemini API\"}")
                        .build();
            }

            double similarity = cosineSimilarity(embedding1, embedding2);
            boolean titlesMatch = similarity > 0.8;

            JSONObject result = new JSONObject();
            result.put("titlesMatch", titlesMatch);
            result.put("article1Title", title1);
            result.put("article2Title", title2);
            result.put("cosineSimilarity", similarity);

            return Response.ok().entity(result.toString()).build();

        } catch (JSONException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"message\": \"Invalid JSON format: " + e.getMessage() + "\"}")
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"message\": \"An error occurred: " + e.getMessage() + "\"}")
                    .build();
        }
    }


    private double[] getEmbedding(String text) throws Exception {
        URL url = new URL(GEMINI_API_URL);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        JSONObject jsonInput = new JSONObject();
        jsonInput.put("model", MODEL);
        JSONObject content = new JSONObject();
        JSONArray parts = new JSONArray();
        JSONObject textPart = new JSONObject();
        textPart.put("text", text);
        parts.put(textPart);
        content.put("parts", parts);
        jsonInput.put("content", content);

        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = jsonInput.toString().getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getErrorStream(), "utf-8"))) {
                StringBuilder errorResponse = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    errorResponse.append(responseLine.trim());
                }
                System.err.println("Google Gemini API Error: " + errorResponse.toString());
            }
            return null;
        }


        StringBuilder response;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), "utf-8"))) {
            response = new StringBuilder();
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
        }

        JSONObject jsonResponse = new JSONObject(response.toString());
        JSONArray embeddingArray = jsonResponse.getJSONObject("embedding").getJSONArray("values");  // Corrected path

        double[] embedding = new double[embeddingArray.length()];
        for (int i = 0; i < embeddingArray.length(); i++) {
            embedding[i] = embeddingArray.getDouble(i);
        }
        return embedding;
    }



    private double cosineSimilarity(double[] vectorA, double[] vectorB) {
        if (vectorA == null || vectorB == null || vectorA.length != vectorB.length) {
            return -1;
        }
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += Math.pow(vectorA[i], 2);
            normB += Math.pow(vectorB[i], 2);
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}