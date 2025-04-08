package com.sismics.reader.rest.resource;

import com.sismics.reader.core.dao.jpa.ArticleDao;
import com.sismics.reader.core.dao.jpa.FeedSubscriptionDao;
import com.sismics.reader.core.dao.jpa.UserArticleDao;
import com.sismics.reader.core.dao.jpa.criteria.ArticleCriteria;
import com.sismics.reader.core.dao.jpa.criteria.FeedSubscriptionCriteria;
import com.sismics.reader.core.dao.jpa.dto.ArticleDto;
import com.sismics.reader.core.dao.jpa.dto.FeedSubscriptionDto;
import com.sismics.reader.core.model.jpa.UserArticle;
import com.sismics.reader.rest.constant.BaseFunction;
import com.sismics.rest.exception.ClientException;
import com.sismics.rest.exception.ForbiddenClientException;
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
import java.text.MessageFormat;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Article REST resources.
 * 
 * @author jtremeaux
 */
@Path("/article")
public class ArticleResource {
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
     * Marks an article as read.
     * 
     * @param id Article ID
     * @return Response
     */
    @POST
    @Path("{id: [a-z0-9\\-]+}/read")
    @Produces(MediaType.APPLICATION_JSON)
    public Response read(
            @PathParam("id") String id) throws JSONException {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        
        // Get the article
        UserArticleDao userArticleDao = new UserArticleDao();
        UserArticle userArticle = userArticleDao.getUserArticle(id, principal.getId());
        if (userArticle == null) {
            throw new ClientException("ArticleNotFound", MessageFormat.format("Article not found: {0}", id));
        }
        
        if (userArticle.getReadDate() == null) {
            // Update the article
            userArticle.setReadDate(new Date());
            userArticleDao.update(userArticle);
    
            // Update the subscriptions
            ArticleDto article = new ArticleDao().findFirstByCriteria(
                    new ArticleCriteria().setId(userArticle.getArticleId()));
    
            FeedSubscriptionDao feedSubscriptionDao = new FeedSubscriptionDao();
            for (FeedSubscriptionDto feedSubscription : feedSubscriptionDao.findByCriteria(new FeedSubscriptionCriteria()
                    .setFeedId(article.getFeedId())
                    .setUserId(principal.getId()))) {
                feedSubscriptionDao.updateUnreadCount(feedSubscription.getId(), feedSubscription.getUnreadUserArticleCount() - 1);
            }
        }
        
        // Always return ok
        JSONObject response = new JSONObject();
        response.put("status", "ok");
        return Response.ok().entity(response).build();
    }

    /**
     * Marks multiple articles as read.
     * 
     * @param idList List of article ID
     * @return Response
     */
    @POST
    @Path("read")
    @Produces(MediaType.APPLICATION_JSON)
    public Response readMultiple(
            @FormParam("id") List<String> idList) throws JSONException {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        
        for (String id : idList) {
            // Get the article
            UserArticleDao userArticleDao = new UserArticleDao();
            UserArticle userArticle = userArticleDao.getUserArticle(id, principal.getId());
            if (userArticle == null) {
                throw new ClientException("ArticleNotFound", MessageFormat.format("Article not found: {0}", id));
            }
            
            if (userArticle.getReadDate() == null) {
                // Update the article
                userArticle.setReadDate(new Date());
                userArticleDao.update(userArticle);
    
                // Update the subscriptions
                ArticleDto article = new ArticleDao().findFirstByCriteria(
                        new ArticleCriteria().setId(userArticle.getArticleId()));
    
                FeedSubscriptionDao feedSubscriptionDao = new FeedSubscriptionDao();
                for (FeedSubscriptionDto feedSubscription : feedSubscriptionDao.findByCriteria(new FeedSubscriptionCriteria()
                        .setFeedId(article.getFeedId())
                        .setUserId(principal.getId()))) {
                    feedSubscriptionDao.updateUnreadCount(feedSubscription.getId(), feedSubscription.getUnreadUserArticleCount() - 1);
                }
            }
        }
        
        // Always return ok
        JSONObject response = new JSONObject();
        response.put("status", "ok");
        return Response.ok().entity(response).build();
    }
    
    /**
     * Marks an article as unread.
     * 
     * @param id Article ID
     * @return Response
     */
    @POST
    @Path("{id: [a-z0-9\\-]+}/unread")
    @Produces(MediaType.APPLICATION_JSON)
    public Response unread(
            @PathParam("id") String id) throws JSONException {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        
        // Get the article
        UserArticleDao userArticleDao = new UserArticleDao();
        UserArticle userArticle = userArticleDao.getUserArticle(id, principal.getId());
        if (userArticle == null) {
            throw new ClientException("ArticleNotFound", MessageFormat.format("Article not found: {0}", id));
        }
        
        if (userArticle.getReadDate() != null) {
            // Update the article
            userArticle.setReadDate(null);
            userArticleDao.update(userArticle);
    
            // Update the subscriptions
            ArticleDto article = new ArticleDao().findFirstByCriteria(
                    new ArticleCriteria().setId(userArticle.getArticleId()));
    
            FeedSubscriptionDao feedSubscriptionDao = new FeedSubscriptionDao();
            for (FeedSubscriptionDto feedSubscription : feedSubscriptionDao.findByCriteria(new FeedSubscriptionCriteria()
                    .setFeedId(article.getFeedId())
                    .setUserId(principal.getId()))) {
                feedSubscriptionDao.updateUnreadCount(feedSubscription.getId(), feedSubscription.getUnreadUserArticleCount() + 1);
            }
        }
        
        // Always return ok
        JSONObject response = new JSONObject();
        response.put("status", "ok");
        return Response.ok().entity(response).build();
    }
    
    /**
     * Marks multiple articles as unread.
     * 
     * @param idList List of article ID
     * @return Response
     */
    @POST
    @Path("unread")
    @Produces(MediaType.APPLICATION_JSON)
    public Response unreadMultiple(
            @FormParam("id") List<String> idList) throws JSONException {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        
        for (String id : idList) {
            // Get the article
            UserArticleDao userArticleDao = new UserArticleDao();
            UserArticle userArticle = userArticleDao.getUserArticle(id, principal.getId());
            if (userArticle == null) {
                throw new ClientException("ArticleNotFound", MessageFormat.format("Article not found: {0}", id));
            }
            
            if (userArticle.getReadDate() != null) {
                // Update the article
                userArticle.setReadDate(null);
                userArticleDao.update(userArticle);
    
                // Update the subscriptions
                ArticleDto article = new ArticleDao().findFirstByCriteria(
                        new ArticleCriteria().setId(userArticle.getArticleId()));
    
                FeedSubscriptionDao feedSubscriptionDao = new FeedSubscriptionDao();
                for (FeedSubscriptionDto feedSubscription : feedSubscriptionDao.findByCriteria(new FeedSubscriptionCriteria()
                        .setFeedId(article.getFeedId())
                        .setUserId(principal.getId()))) {
                    feedSubscriptionDao.updateUnreadCount(feedSubscription.getId(), feedSubscription.getUnreadUserArticleCount() + 1);
                }
            }
        }
        
        // Always return ok
        JSONObject response = new JSONObject();
        response.put("status", "ok");
        return Response.ok().entity(response).build();
    }
}
