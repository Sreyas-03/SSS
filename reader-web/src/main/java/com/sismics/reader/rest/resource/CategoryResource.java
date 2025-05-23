package com.sismics.reader.rest.resource;

import com.sismics.reader.core.dao.jpa.CategoryDao;
import com.sismics.reader.core.dao.jpa.FeedSubscriptionDao;
import com.sismics.reader.core.dao.jpa.UserArticleDao;
import com.sismics.reader.core.dao.jpa.criteria.FeedSubscriptionCriteria;
import com.sismics.reader.core.dao.jpa.criteria.UserArticleCriteria;
import com.sismics.reader.core.dao.jpa.dto.FeedSubscriptionDto;
import com.sismics.reader.core.dao.jpa.dto.UserArticleDto;
import com.sismics.reader.core.model.jpa.Category;
import com.sismics.reader.core.model.jpa.FeedSubscription;
import com.sismics.reader.core.util.jpa.PaginatedList;
import com.sismics.reader.core.util.jpa.PaginatedLists;
import com.sismics.reader.rest.assembler.ArticleAssembler;
import com.sismics.reader.rest.constant.BaseFunction;
import com.sismics.rest.exception.ClientException;
import com.sismics.rest.exception.ForbiddenClientException;
import com.sismics.rest.util.ValidationUtil;
import com.sismics.security.IPrincipal;
import com.sismics.security.UserPrincipal;
import com.sismics.util.filter.SecurityFilter;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import javax.persistence.NoResultException;
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

/**
 * Category REST resources.
 * 
 * @author jtremeaux
 */
@Path("/category")
public class CategoryResource {
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
     * Returns all categories.
     * 
     * @return Response
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response list() throws JSONException {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        
        // Get the root category
        CategoryDao categoryDao = new CategoryDao();
        Category rootCategory = categoryDao.getRootCategory(principal.getId());
        
        // Get the subcategories
        List<Category> categoryList = categoryDao.findAllCategory(principal.getId());
        
        // Build the response
        List<JSONObject> rootCategories = new ArrayList<JSONObject>();

        JSONObject rootCategoryJson = new JSONObject();
        rootCategoryJson.put("id", rootCategory.getId());
        rootCategories.add(rootCategoryJson);

        List<JSONObject> categoriesJson = new ArrayList<JSONObject>();
        for (Category category : categoryList) {
            JSONObject categoryJson = new JSONObject();
            categoryJson.put("id", category.getId());
            categoryJson.put("name", category.getName());
            categoriesJson.add(categoryJson);
        }
        rootCategoryJson.put("categories", categoriesJson);
        
        JSONObject response = new JSONObject();
        response.put("categories", rootCategories);
        return Response.ok().entity(response).build();
    }
    
    /**
     * Returns all articles in a category.
     * 
     * @param id Category ID
     * @param unread Returns only unread articles
     * @param limit Page limit
     * @param afterArticle Start the list after this article
     * @return Response
     */
    @GET
    @Path("{id: [a-z0-9\\-]+}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response get(
            @PathParam("id") String id,
            @QueryParam("unread") boolean unread,
            @QueryParam("limit") Integer limit,
            @QueryParam("after_article") String afterArticle) throws JSONException {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        
        // Get the category
        CategoryDao categoryDao = new CategoryDao();
        Category category;
        try {
            category = categoryDao.getCategory(id, principal.getId());
        } catch (NoResultException e) {
            throw new ClientException("CategoryNotFound", MessageFormat.format("Category not found: {0}", id));
        }

        // Get the articles
        UserArticleDao userArticleDao = new UserArticleDao();
        UserArticleCriteria userArticleCriteria = new UserArticleCriteria()
                .setUnread(unread)
                .setUserId(principal.getId())
                .setSubscribed(true)
                .setVisible(true);
        if (category.getParentId() != null) {
            userArticleCriteria.setCategoryId(id);
        }
        if (afterArticle != null) {
            // Paginate after this user article
            UserArticleCriteria afterArticleCriteria = new UserArticleCriteria()
                    .setUserArticleId(afterArticle)
                    .setUserId(principal.getId());
            List<UserArticleDto> userArticleDtoList = userArticleDao.findByCriteria(afterArticleCriteria);
            if (userArticleDtoList.isEmpty()) {
                throw new ClientException("ArticleNotFound", MessageFormat.format("Can't find user article {0}", afterArticle));
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
     * Creates a new category.
     * 
     * @param name Category name
     * @return Response
     */
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    public Response add(
            @FormParam("name") String name) throws JSONException {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        
        // Validate input data
        name = ValidationUtil.validateLength(name, "name", 1, 100, false);
        
        // Get the root category
        CategoryDao categoryDao = new CategoryDao();
        Category rootCategory = categoryDao.getRootCategory(principal.getId());
        
        // Get the display order
        int displayOrder = categoryDao.getCategoryCount(rootCategory.getId(), principal.getId());
        
        // Create the category
        Category category = new Category();
        category.setUserId(principal.getId());
        category.setParentId(rootCategory.getId());
        category.setName(name);
        category.setOrder(displayOrder);
        String categoryId = categoryDao.create(category);
        
        JSONObject response = new JSONObject();
        response.put("id", categoryId);
        return Response.ok().entity(response).build();
    }

    /**
     * Deletes a category.
     * 
     * @param id Category ID
     * @return Response
     */
    @DELETE
    @Path("{id: [a-z0-9\\-]+}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response delete(
            @PathParam("id") String id) throws JSONException {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }

        // Get the category
        CategoryDao categoryDao = new CategoryDao();
        try {
            categoryDao.getCategory(id, principal.getId());
        } catch (NoResultException e) {
            throw new ClientException("CategoryNotFound", MessageFormat.format("Category not found: {0}", id));
        }
        
        // Move subscriptions in this category to root
        FeedSubscriptionDao feedSubscriptionDao = new FeedSubscriptionDao();
        List<FeedSubscription> feedSubscriptionList = feedSubscriptionDao.findByCategory(id);
        Category rootCategory = categoryDao.getRootCategory(principal.getId());
        for (FeedSubscription feedSubscription : feedSubscriptionList) {
            feedSubscription.setCategoryId(rootCategory.getId());
            feedSubscriptionDao.update(feedSubscription);
            feedSubscriptionDao.reorder(feedSubscription, 0);
        }
        
        // Delete the category
        categoryDao.delete(id);
        
        // Always return ok
        JSONObject response = new JSONObject();
        response.put("status", "ok");
        return Response.ok().entity(response).build();
    }

    /**
     * Marks all articles in this category as read.
     * 
     * @param id Category ID
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
        
        // Get the category
        Category category;
        try {
            category = new CategoryDao().getCategory(id, principal.getId());
        } catch (NoResultException e) {
            throw new ClientException("CategoryNotFound", MessageFormat.format("Category not found: {0}", id));
        }

        // Marks all articles as read in this category
        UserArticleDao userArticleDao = new UserArticleDao();
        userArticleDao.markAsRead(new UserArticleCriteria()
                .setUserId(principal.getId())
                .setSubscribed(true)
                .setCategoryId(id));

        FeedSubscriptionDao feedSubscriptionDao = new FeedSubscriptionDao();
        for (FeedSubscriptionDto feedSubscrition : feedSubscriptionDao.findByCriteria(new FeedSubscriptionCriteria()
                .setCategoryId(category.getId())
                .setUserId(principal.getId()))) {
            feedSubscriptionDao.updateUnreadCount(feedSubscrition.getId(), 0);
        }
        
        // Always return ok
        JSONObject response = new JSONObject();
        response.put("status", "ok");
        return Response.ok().entity(response).build();
    }

    /**
     * Updates the category.
     * 
     * @param id Category ID
     * @param name Category name
     * @param parentId Parent category ID
     * @param order Display order
     * @param folded True if the category is folded
     * @return Response
     */
    @POST
    @Path("{id: [a-z0-9\\-]+}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response update(
            @PathParam("id") String id,
            @FormParam("name") String name,
            @FormParam("parent_id") String parentId,
            @FormParam("order") Integer order,
            @FormParam("folded") Boolean folded) throws JSONException {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        
        // Validate input data
        name = ValidationUtil.validateLength(name, "name", 1, 100, true);
        
        // Get the category
        CategoryDao categoryDao = new CategoryDao();
        Category category;
        try {
            category = categoryDao.getCategory(id, principal.getId());
        } catch (NoResultException e) {
            throw new ClientException("CategoryNotFound", MessageFormat.format("Category not found: {0}", id));
        }
        
        // Update the category
        if (name != null) {
            category.setName(name);
        }
        if (folded != null) {
            category.setFolded(folded);
        }

        // Handle parent category update
        if (parentId != null) {
            if (!parentId.equals(category.getParentId())) {
                Category parentCategory = categoryDao.getCategory(parentId, principal.getId());
                if (parentCategory == null) {
                    throw new ClientException("ParentCategoryNotFound", "Parent category not found");
                }

                String currentParentId = parentId;
                while (currentParentId != null) {
                    if (currentParentId.equals(category.getId())) {
                        throw new ClientException("CircularReference", "A category cannot be its own ancestor");
                    }
                    Category currentParent = categoryDao.getCategory(currentParentId, principal.getId());
                    if (currentParent == null) break;
                    currentParentId = currentParent.getParentId();
                }

                int nestingLevel = 0;
                currentParentId = parentId;
                while (currentParentId != null) {
                    nestingLevel++;
                    if (nestingLevel > 5) {
                        throw new ClientException("CategoryTooDeep", "Categories cannot be nested more than 5 levels deep");
                    }
                    Category currentParent = categoryDao.getCategory(currentParentId, principal.getId());
                    if (currentParent == null) break;
                    currentParentId = currentParent.getParentId();
                }

                category.setParentId(parentId);
            }
        }

        categoryDao.update(category);
        
        // Reorder categories
        if (order != null) {
            categoryDao.reorder(category, order);
        }
        
        // Always return ok
        JSONObject response = new JSONObject();
        response.put("status", "ok");
        return Response.ok().entity(response).build();
    }
}
