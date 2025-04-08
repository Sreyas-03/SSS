package com.sismics.reader.rest.resource;

import com.sismics.reader.core.dao.jpa.FeedSubscriptionDao;
import com.sismics.reader.core.dao.jpa.UserArticleDao;
import com.sismics.reader.core.dao.jpa.CategoryDao;
import com.sismics.reader.core.model.jpa.Category;
import com.sismics.reader.core.dao.jpa.criteria.FeedSubscriptionCriteria;
import com.sismics.reader.core.dao.jpa.criteria.UserArticleCriteria;
import com.sismics.reader.core.dao.jpa.dto.FeedSubscriptionDto;
import com.sismics.reader.core.dao.jpa.dto.UserArticleDto;
import com.sismics.reader.core.util.jpa.PaginatedList;
import com.sismics.reader.core.util.jpa.PaginatedLists;
import com.sismics.reader.rest.assembler.ArticleAssembler;
import com.sismics.reader.rest.constant.BaseFunction;
import com.sismics.rest.exception.ClientException;
import com.sismics.rest.exception.ForbiddenClientException;
import com.sismics.security.IPrincipal;
import com.sismics.security.UserPrincipal;
import com.sismics.util.filter.SecurityFilter;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.codehaus.jettison.json.JSONArray;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.security.Principal;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

/**
 * All articles REST resources.
 * 
 * @author jtremeaux
 */
@Path("/all")
public class AllResource {
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

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response get(
            @QueryParam("unread") boolean unread,
            @QueryParam("limit") Integer limit,
            @QueryParam("after_article") String afterArticle) throws JSONException {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        // Get the articles
        UserArticleDao userArticleDao = new UserArticleDao();
        UserArticleCriteria userArticleCriteria = new UserArticleCriteria()
                .setUnread(unread)
                .setUserId(principal.getId())
                .setSubscribed(true)
                .setVisible(true);
        if (afterArticle != null) {
            // Paginate after this user article
            UserArticleCriteria afterArticleCriteria = new UserArticleCriteria()
                    .setUserArticleId(afterArticle)
                    .setUserId(principal.getId());
            List<UserArticleDto> userArticleDtoList = userArticleDao.findByCriteria(afterArticleCriteria);
            if (userArticleDtoList.isEmpty()) {
                throw new ClientException("ArticleNotFound",
                        MessageFormat.format("Can't find user article {0}", afterArticle));
            }
            UserArticleDto userArticleDto = userArticleDtoList.iterator().next();
            userArticleCriteria.setArticlePublicationDateMax(new Date(userArticleDto.getArticlePublicationTimestamp()));
            userArticleCriteria.setArticleIdMax(userArticleDto.getArticleId());
        }
        PaginatedList<UserArticleDto> paginatedList = PaginatedLists.create(limit, null);
        userArticleDao.findByCriteria(paginatedList, userArticleCriteria, null, null);
        // Build the response
        JSONObject response = new JSONObject();
        List<JSONObject> articles = new ArrayList<JSONObject>();
        for (UserArticleDto userArticle : paginatedList.getResultList()) {
            articles.add(ArticleAssembler.asJson(userArticle));
        }
        response.put("articles", articles);
        return Response.ok().entity(response).build();
    }

    /**
     * Returns all articles.
     * 
     * @param unread       Returns only unread articles
     * @param limit        Page limit
     * @param afterArticle Start the list after this user article
     * @return Response
     */
    @GET
    @Path("/category")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCategoryLevelArticles(
            @QueryParam("unread") boolean unread,
            @QueryParam("maxDepth") Integer maxDepth,
            @QueryParam("limit") Integer limit,
            @QueryParam("after_article") String afterArticle) throws JSONException {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }

        // Get the root category
        CategoryDao categoryDao = new CategoryDao();
        Category rootCategory = categoryDao.getRootCategory(principal.getId());

        // Build response object
        JSONObject response = new JSONObject();
        Map<Integer, JSONArray> levelArticlesMap = new HashMap<>();
        Map<Integer, JSONArray> levelCategoriesMap = new HashMap<>();

        // Recursively traverse categories and collect articles by levels
        processCategoryLevel(rootCategory, 1, maxDepth, unread, limit, afterArticle, levelArticlesMap,
                levelCategoriesMap);

        // Convert collected levels into JSON response
        for (Map.Entry<Integer, JSONArray> entry : levelArticlesMap.entrySet()) {
            JSONObject levelData = new JSONObject();
            levelData.put("categories", levelCategoriesMap.get(entry.getKey()));
            levelData.put("articles", entry.getValue());
            response.put("level " + entry.getKey(), levelData);
        }

        return Response.ok().entity(response).build();
    }

    /**
     * Recursively processes categories and collects articles grouped by levels,
     * including category names.
     */
    private void processCategoryLevel(Category category, int level, Integer maxDepth, boolean unread,
            Integer limit, String afterArticle, Map<Integer, JSONArray> levelArticlesMap,
            Map<Integer, JSONArray> levelCategoriesMap) throws JSONException {
        if (maxDepth != null && level > maxDepth) {
            return; // Stop if max depth is reached
        }

        // Fetch articles for this category level
        JSONArray articlesArray = levelArticlesMap.computeIfAbsent(level, k -> new JSONArray());
        JSONArray categoriesArray = levelCategoriesMap.computeIfAbsent(level, k -> new JSONArray());

        // Add category name to level context
        categoriesArray.put(category.getName());

        FeedSubscriptionDao feedSubscriptionDao = new FeedSubscriptionDao();
        FeedSubscriptionCriteria criteria = new FeedSubscriptionCriteria()
                .setCategoryId(category.getId())
                .setUserId(principal.getId());

        List<FeedSubscriptionDto> subscriptions = feedSubscriptionDao.findByCriteria(criteria);
        UserArticleDao userArticleDao = new UserArticleDao();

        for (FeedSubscriptionDto subscription : subscriptions) {
            UserArticleCriteria articleCriteria = new UserArticleCriteria()
                    .setUnread(unread)
                    .setUserId(principal.getId())
                    .setSubscribed(true)
                    .setVisible(true)
                    .setFeedId(subscription.getFeedId());

            // Handle pagination
            if (afterArticle != null) {
                UserArticleCriteria afterArticleCriteria = new UserArticleCriteria()
                        .setUserArticleId(afterArticle)
                        .setUserId(principal.getId());
                List<UserArticleDto> userArticleDtoList = userArticleDao.findByCriteria(afterArticleCriteria);
                if (!userArticleDtoList.isEmpty()) {
                    UserArticleDto userArticleDto = userArticleDtoList.iterator().next();
                    articleCriteria
                            .setArticlePublicationDateMax(new Date(userArticleDto.getArticlePublicationTimestamp()));
                    articleCriteria.setArticleIdMax(userArticleDto.getArticleId());
                }
            }

            PaginatedList<UserArticleDto> paginatedList = PaginatedLists.create(limit, null);
            userArticleDao.findByCriteria(paginatedList, articleCriteria, null, null);

            for (UserArticleDto article : paginatedList.getResultList()) {
                articlesArray.put(ArticleAssembler.asJson(article));
            }
        }

        // Recursively process subcategories
        CategoryDao categoryDao = new CategoryDao();
        List<Category> subCategories = categoryDao.findSubCategory(category.getId(), principal.getId());
        for (Category subCategory : subCategories) {
            processCategoryLevel(subCategory, level + 1, maxDepth, unread, limit, afterArticle, levelArticlesMap,
                    levelCategoriesMap);
        }
    }

    /**
     * Marks all articles as read.
     * 
     * @return Response
     */
    @POST
    @Path("/read")
    @Produces(MediaType.APPLICATION_JSON)
    public Response read() throws JSONException {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }

        // Marks all articles of this user as read
        UserArticleDao userArticleDao = new UserArticleDao();
        userArticleDao.markAsRead(new UserArticleCriteria()
                .setUserId(principal.getId())
                .setSubscribed(true));

        FeedSubscriptionDao feedSubscriptionDao = new FeedSubscriptionDao();
        for (FeedSubscriptionDto feedSubscrition : feedSubscriptionDao.findByCriteria(new FeedSubscriptionCriteria()
                .setUserId(principal.getId()))) {
            feedSubscriptionDao.updateUnreadCount(feedSubscrition.getId(), 0);
        }

        // Always return ok
        JSONObject response = new JSONObject();
        response.put("status", "ok");
        return Response.ok().entity(response).build();
    }

}
