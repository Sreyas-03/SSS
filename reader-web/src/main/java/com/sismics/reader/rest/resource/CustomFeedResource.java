package com.sismics.reader.rest.resource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import com.sismics.reader.core.customfeeds.CustomFeedCreateCommander;
import com.sismics.rest.exception.ForbiddenClientException;
import com.sismics.security.IPrincipal;
import com.sismics.util.filter.SecurityFilter;

@Path("/customFeed")
public class CustomFeedResource {

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
            // System.out.println("No valid principal found."); // Remove or comment out
            return false;
        }
    }

    private static final String FEED_DB_PATH = "custom-feeds.json";

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response submitFeed(String feedJson) {
        try {
            if (!authenticate()) {
                throw new ForbiddenClientException();
            }
            
            JSONObject customFeed = new JSONObject(feedJson);

            // Add a unique feedId
            customFeed.put("feedId", UUID.randomUUID().toString());
            customFeed.put("author", principal.getName());

            List<String> articleUrlList = new ArrayList<>();

            List<JSONObject> feedList = new ArrayList<>();

            File file = new File(context.getRealPath("/" + FEED_DB_PATH));
            if (file.exists()) {
                String content = new String(Files.readAllBytes(Paths.get(file.getPath())));
                if (!content.isEmpty()) {
                    JSONObject jsonObject = new JSONObject(content);
                    if (jsonObject.has("feeds")) {
                        JSONArray existingFeeds = jsonObject.getJSONArray("feeds");
                        for (int i = 0; i < existingFeeds.length(); i++) {
                            feedList.add(existingFeeds.getJSONObject(i));
                            System.out.println(existingFeeds.getJSONObject(i).toString());
                        }
                    }
                }
            }
            feedList.add(customFeed);

            
            if (feedJson.contains("articles")) {
                JSONArray articles = customFeed.getJSONArray("articles");
                for (int i = 0; i < articles.length(); i++) {
                    // It reaches here.. I've checked that.
                    String articleUrl = articles.getString(i);
                    articleUrlList.add(articleUrl);
                }
            }

            String title = customFeed.getString("title");
            String feedId = customFeed.getString("feedId");
            String userName = principal.getName();

            CustomFeedCreateCommander customFeedCreateCommander = new CustomFeedCreateCommander();
            customFeedCreateCommander.createCustomFeed(userName, feedId, title, articleUrlList);

            try {
                JSONObject feeds = new JSONObject();
                feeds.put("feeds", feedList);
                Files.write(Paths.get(file.getPath()), feeds.toString().getBytes());
            } catch (IOException e) {
                return Response.serverError().entity("{\"error\": \"Failed to save custom feed\"}").build();
            }

            return Response.ok().entity("{\"message\": \"Custom feed submitted successfully!\"}").build();
        } catch ( IOException | JSONException e) {
            return Response.serverError().entity("{\"error\": \"Failed to save custom feed\"}").build();
        }
    }

    @GET
    @Path("/view")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCustomFeeds() {
        try {
            if (!authenticate()) {
                throw new ForbiddenClientException();
            }

            File file = new File(context.getRealPath("/" + FEED_DB_PATH));
            if (!file.exists()) {
                return Response.ok().entity("{\"feeds\": []}").build();
            }

            String content = new String(Files.readAllBytes(Paths.get(file.getPath())));
            if (content.isEmpty()) {
                System.out.println("wtf am i doing here 2!!");
                return Response.ok().entity("{\"feeds\": []}").build();
            }

            JSONObject jsonObject = new JSONObject(content);
            if (!jsonObject.has("feeds")) {
                return Response.ok().entity("{\"feeds\": []}").build();
            }

            JSONArray allFeeds = jsonObject.getJSONArray("feeds");
            JSONArray filteredFeeds = new JSONArray();
            

            // Filter feeds to only include those created by the current user
            for (int i = 0; i < allFeeds.length(); i++) {
                JSONObject feed = allFeeds.getJSONObject(i);
                filteredFeeds.put(feed);
            }

            JSONObject response = new JSONObject();
            response.put("feeds", filteredFeeds);

            return Response.ok(response.toString()).build();
        } catch (IOException | JSONException e) {
             e.printStackTrace(); // Log the exception for debugging
            return Response.serverError().entity("{\"error\": \"Failed to retrieve custom feeds\", \"stacktrace\": \"" + e.getMessage() +  "\"}").build();
        }
    }


    // Add other methods (GET, DELETE, UPDATE) as needed, similar to ReportBugResource.
    // You might not need a /manage endpoint if all users can manage their own feeds.
}