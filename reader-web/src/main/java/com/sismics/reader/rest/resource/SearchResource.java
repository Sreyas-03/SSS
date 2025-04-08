package com.sismics.reader.rest.resource;

import com.sismics.reader.core.dao.jpa.dto.UserArticleDto;
import com.sismics.reader.core.model.context.AppContext;
import com.sismics.reader.core.service.ArticleIndexingService;
import com.sismics.reader.core.util.jpa.PaginatedList;
import com.sismics.reader.rest.assembler.ArticleAssembler;
import com.sismics.reader.rest.constant.BaseFunction;
import com.sismics.rest.exception.ForbiddenClientException;
import com.sismics.rest.exception.ServerException;
import com.sismics.rest.util.ValidationUtil;
import com.sismics.security.IPrincipal;
import com.sismics.security.UserPrincipal;
import com.sismics.util.filter.SecurityFilter;

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
import java.util.Set;

/**
 * Search articles REST resources.
 * 
 * @author jtremeaux
 */
@Path("/search")
public class SearchResource {
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
     * Returns articles matching a search query.
     * 
     * @param query Search query
     * @param limit Page limit
     * @param offset Page offset
     * @return Response
     */
    @GET
    @Path("{query: .+}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response get(
            @PathParam("query") String query,
            @QueryParam("limit") Integer limit,
            @QueryParam("offset") Integer offset) throws JSONException {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        
        ValidationUtil.validateRequired(query, "query");
        
        // Search in index
        ArticleIndexingService articleindexingService = AppContext.getInstance().getArticleIndexingService();
        PaginatedList<UserArticleDto> paginatedList;
        try {
            paginatedList = articleindexingService.searchArticles(principal.getId(), query, offset, limit);
        } catch (Exception e) {
            throw new ServerException("SearchError", "Error searching articles", e);
        }
        
        // Build the response
        JSONObject response = new JSONObject();

        List<JSONObject> articles = new ArrayList<JSONObject>();
        for (UserArticleDto userArticle : paginatedList.getResultList()) {
            articles.add(ArticleAssembler.asJson(userArticle));
        }
        response.put("total", paginatedList.getResultCount());
        response.put("articles", articles);

        return Response.ok().entity(response).build();
    }
}
