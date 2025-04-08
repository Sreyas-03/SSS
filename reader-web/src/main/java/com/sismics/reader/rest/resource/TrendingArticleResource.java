package com.sismics.reader.rest.resource;

import com.sismics.rest.exception.ForbiddenClientException;
import com.sismics.security.IPrincipal;
import com.sismics.util.filter.SecurityFilter;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Path("/trending")
public class TrendingArticleResource {

    @Context
    private ServletContext context;

    @Context
    protected HttpServletRequest request;

    protected IPrincipal principal;

    // Store article details (id -> article object)
    private static final ConcurrentHashMap<String, JSONObject> articleDetailsMap = new ConcurrentHashMap<>();

    // Store star counts (id -> count)
    private static final ConcurrentHashMap<String, Integer> starCountsMap = new ConcurrentHashMap<>();

    // Cache for top 5 articles (sorted by star count, then by ID for tie-breaking)
    private static volatile List<Map.Entry<String, Integer>> top5ArticlesCache = new ArrayList<>();

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

    // Endpoint to update the star count of an article (increment)
    @POST
    @Path("/star")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response starArticle(String articleJson) {
        try {
            if (!authenticate()) {
                throw new ForbiddenClientException();
            }

            JSONObject requestData = new JSONObject(articleJson); // Parse the entire request
            String articleId = requestData.getString("id"); // Get the ID (which is the title)
            JSONObject article = requestData.getJSONObject("articleObject"); // Get the article object

            if (articleId == null || articleId.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("{\"message\": \"Article ID is missing or empty.\"}")
                        .build();
            }

            // Update article details (store or overwrite)
            articleDetailsMap.put(articleId, article);

            // Update star count (increment)
            int newCount = starCountsMap.merge(articleId, 1, Integer::sum);  // Atomic increment

            // Update top 5 cache (synchronized for thread safety)
            updateTop5Cache(articleId, newCount);

            JSONObject response = new JSONObject();
            response.put("status", "success");
            response.put("articleId", articleId);
            response.put("newStarCount", newCount);
            return Response.ok().entity(response.toString()).build();

        } catch (Exception e) {
             return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"message\": \"An error occurred: " + e.getMessage() + "\"}")
                    .build();
        }
    }


    // Endpoint to decrease the star count of an article (decrement)
    @POST
    @Path("/destar")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response destarArticle(String articleJson) {
        try {
            if (!authenticate()) {
                throw new ForbiddenClientException();
            }

            JSONObject requestData = new JSONObject(articleJson); // Parse the entire request
            String articleId = requestData.getString("id");     // Get the ID (which is the title)
            JSONObject article = requestData.getJSONObject("articleObject"); // Get article object

            if (articleId == null || articleId.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("{\"message\": \"Article ID is missing or empty.\"}")
                        .build();
            }

            //  Don't update the article details here, only the count.
            // articleDetailsMap.put(articleId, article); // No longer needed here

            // Update star count (decrement), but don't go below 0
            int newCount = starCountsMap.compute(articleId, (key, oldValue) -> {
                if (oldValue == null || oldValue <= 0) {
                    return 0; // Or null if you want to remove entries with 0 stars
                }
                return oldValue - 1;
            });

            // Update top 5 cache
             updateTop5Cache(articleId, newCount);

            JSONObject response = new JSONObject();
            response.put("status", "success");
            response.put("articleId", articleId);
            response.put("newStarCount", newCount);
            return Response.ok().entity(response.toString()).build();

        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"message\": \"An error occurred: " + e.getMessage() + "\"}")
                    .build();
        }
    }


    // Endpoint to get the top 5 trending articles
    @GET
    @Path("/top")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTop5Articles() {
         try {
            if (!authenticate()) {
                throw new ForbiddenClientException();
            }

            JSONArray topArticlesJson = new JSONArray();
            for (Map.Entry<String, Integer> entry : top5ArticlesCache) {
                String articleId = entry.getKey();
                JSONObject articleDetail = articleDetailsMap.get(articleId);
                if (articleDetail != null) {
                    // Add star count to the article detail
                    JSONObject articleWrapper = new JSONObject(); // Create a wrapper object
                    articleWrapper.put("articleObject", articleDetail); // Include full object
                    articleWrapper.put("starCount", entry.getValue());   // And star count
                    topArticlesJson.put(articleWrapper);

                }
            }

            JSONObject response = new JSONObject();
            response.put("topArticles", topArticlesJson);
            return Response.ok().entity(response.toString()).build();

        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"message\": \"An error occurred: " + e.getMessage() + "\"}")
                    .build();
        }
    }

    //  Synchronized method to update the top 5 cache
    private synchronized void updateTop5Cache(String articleId, int newCount) {
        // Create a copy to avoid modifying the original while iterating
        List<Map.Entry<String, Integer>> currentTop5 = new ArrayList<>(top5ArticlesCache);

        // 1. Check if the article is already in the top 5
        Optional<Map.Entry<String, Integer>> existingEntry = currentTop5.stream()
                .filter(entry -> entry.getKey().equals(articleId))
                .findFirst();

        if (existingEntry.isPresent()) {
            // Update the count in the existing entry
            existingEntry.get().setValue(newCount);
        } else {
            // 2. If not in top 5, check if it should be added
            if (currentTop5.size() < 5 || newCount > currentTop5.get(currentTop5.size() - 1).getValue()) {
                currentTop5.add(new AbstractMap.SimpleEntry<>(articleId, newCount));
            }
        }

        // 3. Sort the list (descending by count, then ascending by ID)
          currentTop5 = currentTop5.stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(5)  // Keep only top 5
                .collect(Collectors.toList());

        // 4. Update the cache
        top5ArticlesCache = currentTop5;
    }
}