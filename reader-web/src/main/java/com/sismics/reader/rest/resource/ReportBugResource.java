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
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import java.security.Principal;

@Path("/reportBug")
public class ReportBugResource {
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

            // // Logging principal information
            // System.out.println("Principal Details: " + this.principal);
            // System.out.println("Principal Details: " + this.principal.getName());


            return !this.principal.isAnonymous();
        } else {
            System.out.println("No valid principal found.");
            return false;
        }
    }

    private static final String BUG_DB_PATH = "bug-db.json";

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response submitBug(String bugJson) {
        try {
            if (!authenticate()) {
                throw new ForbiddenClientException();
            }
            JSONObject bugReport = new JSONObject(bugJson);

            // Add username of the current user
            if (principal != null) {
                bugReport.put("User", principal.getName());
            } else {
                bugReport.put("User", "Anonymous"); // Or some other default if there's no authenticated user
            }

            List<JSONObject> bugList = new ArrayList<>();

            File file = new File(context.getRealPath("/" + BUG_DB_PATH));
            if (file.exists()) {
                String content = new String(Files.readAllBytes(Paths.get(file.getPath())));
                if (!content.isEmpty()) {
                    JSONObject jsonObject = new JSONObject(content);
                    if (jsonObject.has("bugs")) {
                        JSONArray existingBugs = jsonObject.getJSONArray("bugs");
                        for (int i = 0; i < existingBugs.length(); i++) {
                            bugList.add(existingBugs.getJSONObject(i));
                        }
                    }
                }
            }

            bugList.add(bugReport);

            JSONObject newBugData = new JSONObject();
            newBugData.put("bugs", new JSONArray(bugList));

            try (FileWriter writer = new FileWriter(file)) {
                writer.write(newBugData.toString(2));
            }

            return Response.ok().entity("{\"message\": \"Bug report submitted successfully!\"}").build();
        } catch (IOException | JSONException e) {
            return Response.serverError().entity("{\"error\": \"Failed to save bug report\"}").build();
        }
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getBugs() {
        try {
            File file = new File(context.getRealPath("/" + BUG_DB_PATH));
            if (!file.exists()) {
                return Response.ok().entity("{\"bugs\": []}").build(); // Return empty array if file doesn't exist
            }

            String content = new String(Files.readAllBytes(Paths.get(file.getPath())));
            if (content.isEmpty()) {
                return Response.ok().entity("{\"bugs\": []}").build(); // Return empty array if file is empty
            }

            JSONObject jsonObject = new JSONObject(content);
            if (!jsonObject.has("bugs")) {
                return Response.ok().entity("{\"bugs\": []}").build(); // Return empty array if "bugs" key doesn't exist
            }

            // Add index to each bug in the response
            JSONArray bugs = jsonObject.getJSONArray("bugs");
            for (int i = 0; i < bugs.length(); i++) {
                JSONObject bug = bugs.getJSONObject(i);
                bug.put("index", i);  // Add index to each bug object
            }
            jsonObject.put("bugs", bugs);

            return Response.ok(jsonObject.toString()).build(); // Return the entire JSON
        } catch (IOException | JSONException e) {
            return Response.serverError().entity("{\"error\": \"Failed to retrieve bug reports\"}").build();
        }
    }

    @GET
    @Path("/view")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getViewBugs() {
        try {
            if (!authenticate()) {
                throw new ForbiddenClientException();
            }

            File file = new File(context.getRealPath("/" + BUG_DB_PATH));
            if (!file.exists()) {
                return Response.ok().entity("{\"bugs\": []}").build();
            }

            String content = new String(Files.readAllBytes(Paths.get(file.getPath())));
            if (content.isEmpty()) {
                return Response.ok().entity("{\"bugs\": []}").build();
            }

            JSONObject jsonObject = new JSONObject(content);
            if (!jsonObject.has("bugs")) {
                return Response.ok().entity("{\"bugs\": []}").build();
            }

            JSONArray allBugs = jsonObject.getJSONArray("bugs");
            JSONArray filteredBugs = new JSONArray();
            String currentUser = principal.getName(); // Get current user's name

            for (int i = 0; i < allBugs.length(); i++) {
                JSONObject bug = allBugs.getJSONObject(i);
                String bugUser = bug.getString("User");
                if (currentUser.equals(bugUser)) {
                    filteredBugs.put(bug);
                }
            }

            // Create a new JSON object with the filtered bugs
            JSONObject response = new JSONObject();
            response.put("bugs", filteredBugs);

            return Response.ok(response.toString()).build();
        } catch (IOException | JSONException e) {
            return Response.serverError().entity("{\"error\": \"Failed to retrieve bug reports\"}").build();
        }
    }


    @POST
    @Path("/update")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateBug(String updateDataJson) {
        try {
            if (!authenticate()) {
                throw new ForbiddenClientException();
            }
            JSONObject updateData = new JSONObject(updateDataJson);
            int index = updateData.getInt("index");
            String newStatus = updateData.getString("status");
            JSONArray updatedBugs = updateData.getJSONArray("bugs");

            File file = new File(context.getRealPath("/" + BUG_DB_PATH));
            if (!file.exists()) {
                return Response.status(Response.Status.NOT_FOUND).entity("{\"error\": \"Bug database not found\"}").build();
            }

            String content = new String(Files.readAllBytes(Paths.get(file.getPath())));
            if (content.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND).entity("{\"error\": \"Bug database is empty\"}").build();
            }

            JSONObject jsonObject = new JSONObject(content);
            if (!jsonObject.has("bugs")) {
                return Response.status(Response.Status.NOT_FOUND).entity("{\"error\": \"Bugs array not found\"}").build();
            }

            JSONArray bugs = jsonObject.getJSONArray("bugs");
            if (index >= 0 && index < bugs.length()) {
                JSONObject bugToUpdate = bugs.getJSONObject(index);
                bugToUpdate.put("Status", newStatus);
                bugs.put(index, bugToUpdate); // Update the bug in the array
                jsonObject.put("bugs", bugs);

                try (FileWriter writer = new FileWriter(file)) {
                    writer.write(jsonObject.toString(2));
                }

                return Response.ok().entity("{\"message\": \"Bug status updated successfully\"}").build();
            } else {
                return Response.status(Response.Status.NOT_FOUND).entity("{\"error\": \"Bug not found\"}").build();
            }

        } catch (IOException | JSONException e) {
            return Response.serverError().entity("{\"error\": \"Failed to update bug status\"}").build();
        }
    }


    @POST
    @Path("/delete")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteBug(String deleteDataJson) {
        try {
            if (!authenticate()) {
                throw new ForbiddenClientException();
            }
            JSONObject deleteData = new JSONObject(deleteDataJson);
            JSONArray updatedBugs = deleteData.getJSONArray("bugs");

            File file = new File(context.getRealPath("/" + BUG_DB_PATH));
            if (!file.exists()) {
                return Response.status(Response.Status.NOT_FOUND).entity("{\"error\": \"Bug database not found\"}").build();
            }

            // Write the updated bug list back to the file
            JSONObject newBugData = new JSONObject();
            newBugData.put("bugs", updatedBugs);

            try (FileWriter writer = new FileWriter(file)) {
                writer.write(newBugData.toString(2));
            }

            return Response.ok().entity("{\"message\": \"Bug deleted successfully\"}").build();

        } catch (IOException | JSONException e) {
            return Response.serverError().entity("{\"error\": \"Failed to delete bug\"}").build();
        }
    }

    @GET
    @Path("/manage")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getManageBugs() {
        try {
            if (!authenticate()) {
                throw new ForbiddenClientException();
            }

            String currentUser = principal.getName();
            if (!"admin".equals(currentUser)) {
                return Response.ok().entity("{\"bugs\": []}").build(); // Return empty if not admin
            }

            File file = new File(context.getRealPath("/" + BUG_DB_PATH));
            if (!file.exists()) {
                return Response.ok().entity("{\"bugs\": []}").build();
            }

            String content = new String(Files.readAllBytes(Paths.get(file.getPath())));
            if (content.isEmpty()) {
                return Response.ok().entity("{\"bugs\": []}").build();
            }

            JSONObject jsonObject = new JSONObject(content);
            if (!jsonObject.has("bugs")) {
                return Response.ok().entity("{\"bugs\": []}").build();
            }

            // Add index to each bug in the response
            JSONArray bugs = jsonObject.getJSONArray("bugs");
            for (int i = 0; i < bugs.length(); i++) {
                JSONObject bug = bugs.getJSONObject(i);
                bug.put("index", i);  // Add index to each bug object
            }
            jsonObject.put("bugs", bugs);

            return Response.ok(jsonObject.toString()).build(); // Return the entire JSON
        } catch (IOException | JSONException e) {
            return Response.serverError().entity("{\"error\": \"Failed to retrieve bug reports\"}").build();
        }
    }
}