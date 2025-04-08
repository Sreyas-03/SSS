package com.sismics.reader.rest.resource;

import com.sismics.reader.core.model.context.AppContext;
import com.sismics.reader.core.util.ConfigUtil;
import com.sismics.reader.core.util.jpa.PaginatedList;
import com.sismics.reader.core.util.jpa.PaginatedLists;
import com.sismics.reader.rest.constant.BaseFunction;
import com.sismics.rest.exception.ForbiddenClientException;
import com.sismics.rest.exception.ServerException;
import com.sismics.security.IPrincipal;
import com.sismics.security.UserPrincipal;
import com.sismics.util.NetworkUtil;
import com.sismics.util.filter.SecurityFilter;
import com.sismics.util.log4j.LogCriteria;
import com.sismics.util.log4j.LogEntry;
import com.sismics.util.log4j.MemoryAppender;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Appender;
import org.apache.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * General app REST resource.
 * 
 * @author jtremeaux
 */
@Path("/app")
public class AppResource {
    /**
     * Injects the HTTP request.
     */
    @Context
    protected HttpServletRequest request;
    
    /**
     * Application key.
     */
    @QueryParam("app_key")
    protected String appKey;
    
    /**
     * Principal of the authenticated user.
     */
    protected IPrincipal principal;

    /**
     * Checks if the user has a base function.
     * 
     * @param baseFunction Base function to check
     * @return True if the user has the base function
     */
    protected boolean hasBaseFunction(BaseFunction baseFunction) throws JSONException {
        if (principal == null || !(principal instanceof UserPrincipal)) {
            return false;
        }
        Set<String> baseFunctionSet = ((UserPrincipal) principal).getBaseFunctionSet();
        return baseFunctionSet != null && baseFunctionSet.contains(baseFunction.name());
    }
    
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
            return false;
        }
    }
    
    /**
     * Return the information about the application.
     * 
     * @return Response
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response version() throws JSONException {
        ResourceBundle configBundle = ConfigUtil.getConfigBundle();
        String currentVersion = configBundle.getString("api.current_version");
        String minVersion = configBundle.getString("api.min_version");

        JSONObject response = new JSONObject();
        response.put("current_version", currentVersion.replace("-SNAPSHOT", ""));
        response.put("min_version", minVersion);
        response.put("total_memory", Runtime.getRuntime().totalMemory());
        response.put("free_memory", Runtime.getRuntime().freeMemory());
        return Response.ok().entity(response).build();
    }
    
    /**
     * Retrieve the application logs.
     * 
     * @param level Filter on logging level
     * @param tag Filter on logger name / tag
     * @param message Filter on message
     * @param limit Page limit
     * @param offset Page offset
     * @return Response
     */
    @GET
    @Path("log")
    @Produces(MediaType.APPLICATION_JSON)
    public Response log(
            @QueryParam("level") String level,
            @QueryParam("tag") String tag,
            @QueryParam("message") String message,
            @QueryParam("limit") Integer limit,
            @QueryParam("offset") Integer offset) throws JSONException {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        // checkBaseFunction(BaseFunction.ADMIN);

        if (!hasBaseFunction(BaseFunction.ADMIN)) {
            throw new ForbiddenClientException();
        }

        // Get the memory appender
        Logger logger = Logger.getRootLogger();
        Appender appender = logger.getAppender("MEMORY");
        if (appender == null || !(appender instanceof MemoryAppender)) {
            throw new ServerException("ServerError", "MEMORY appender not configured");
        }
        MemoryAppender memoryAppender = (MemoryAppender) appender;
        
        // Find the logs
        LogCriteria logCriteria = new LogCriteria()
                .setLevel(StringUtils.stripToNull(level))
                .setTag(StringUtils.stripToNull(tag))
                .setMessage(StringUtils.stripToNull(message));
        
        PaginatedList<LogEntry> paginatedList = PaginatedLists.create(limit, offset);
        memoryAppender.find(logCriteria, paginatedList);
        JSONObject response = new JSONObject();
        List<JSONObject> logs = new ArrayList<JSONObject>();
        for (LogEntry logEntry : paginatedList.getResultList()) {
            JSONObject log = new JSONObject();
            log.put("date", logEntry.getTimestamp());
            log.put("level", logEntry.getLevel());
            log.put("tag", logEntry.getTag());
            log.put("message", logEntry.getMessage());
            logs.add(log);
        }
        response.put("total", paginatedList.getResultCount());
        response.put("logs", logs);
        
        return Response.ok().entity(response).build();
    }
    
    /**
     * Destroy and rebuild articles index.
     * 
     * @return Response
     */
    @POST
    @Path("batch/reindex")
    @Produces(MediaType.APPLICATION_JSON)
    public Response batchReindex() throws JSONException {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        // checkBaseFunction(BaseFunction.ADMIN);
        
        if (!hasBaseFunction(BaseFunction.ADMIN)) {
            throw new ForbiddenClientException();
        }
        JSONObject response = new JSONObject();
        try {
            AppContext.getInstance().getIndexingService().rebuildIndex();
        } catch (Exception e) {
            throw new ServerException("IndexingError", "Error rebuilding index", e);
        }
        response.put("status", "ok");
        return Response.ok().entity(response).build();
    }
    
    /**
     * Attempt to map a port to the gateway.
     * 
     * @return Response
     */
    @POST
    @Path("map_port")
    @Produces(MediaType.APPLICATION_JSON)
    public Response mapPort() throws JSONException {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        // checkBaseFunction(BaseFunction.ADMIN);
        
        if (!hasBaseFunction(BaseFunction.ADMIN)) {
            throw new ForbiddenClientException();
        }

        JSONObject response = new JSONObject();
        if (!NetworkUtil.mapTcpPort(request.getServerPort())) {
            throw new ServerException("NetworkError", "Error mapping port using UPnP");
        }
        
        response.put("status", "ok");
        return Response.ok().entity(response).build();
    }
}
