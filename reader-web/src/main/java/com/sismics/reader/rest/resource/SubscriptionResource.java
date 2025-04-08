package com.sismics.reader.rest.resource;

import com.google.common.io.ByteStreams;
import com.sismics.reader.core.dao.jpa.*;
import com.sismics.reader.core.dao.jpa.criteria.FeedSubscriptionCriteria;
import com.sismics.reader.core.dao.jpa.criteria.UserArticleCriteria;
import com.sismics.reader.core.dao.jpa.dto.FeedSubscriptionDto;
import com.sismics.reader.core.dao.jpa.dto.UserArticleDto;
import com.sismics.reader.core.event.SubscriptionImportedEvent;
import com.sismics.reader.core.model.context.AppContext;
import com.sismics.reader.core.model.jpa.*;
import com.sismics.reader.core.service.*;
import com.sismics.reader.core.util.DirectoryUtil;
import com.sismics.reader.core.util.EntityManagerUtil;
import com.sismics.reader.core.util.jpa.PaginatedList;
import com.sismics.reader.core.util.jpa.PaginatedLists;
import com.sismics.reader.rest.assembler.ArticleAssembler;
import com.sismics.reader.rest.constant.BaseFunction;
import com.sismics.rest.exception.ClientException;
import com.sismics.rest.exception.ForbiddenClientException;
import com.sismics.rest.exception.ServerException;
import com.sismics.rest.util.JsonUtil;
import com.sismics.rest.util.ValidationUtil;
import com.sismics.security.IPrincipal;
import com.sismics.security.UserPrincipal;
import com.sismics.util.MessageUtil;
import com.sismics.util.filter.SecurityFilter;
import com.sun.jersey.multipart.FormDataBodyPart;
import com.sun.jersey.multipart.FormDataParam;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.persistence.NoResultException;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.StreamingOutput;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.dom.DOMSource;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.Principal;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Feed subscriptions REST resources.
 * 
 * @author jtremeaux
 */
@Path("/subscription")
public class SubscriptionResource {
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
     * 
     * 
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response list(
            @QueryParam("unread") boolean unread) throws JSONException {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        
        // Get the root category
        CategoryDao categoryDao = new CategoryDao();
        Category rootCategory = categoryDao.getRootCategory(principal.getId());

        // Build the JSON response using the recursive function
        JSONObject response = buildCategoryJsonRecursive(rootCategory, unread, 0);

        return Response.ok().entity(response).build();
    }

    /**
     * Builds the category JSON object recursively, including subcategories and subscriptions.
     *
     * @param category The category to build the JSON for
     * @param unread   Whether to include only unread items
     * @param level    Current nesting level of the category
     * @return JSONObject for the category, including nested items
     * @throws JSONException if JSON object creation fails
     */
    private JSONObject buildCategoryJsonRecursive(Category category, boolean unread, int level) throws JSONException {
        JSONObject categoryJson = createCategoryJsonObject(category, level);
        JSONArray subscriptionsJson = new JSONArray();
        JSONArray categoriesJson = new JSONArray();
        int categoryUnreadCount = 0;
        int categoryTotalCount = 0;
        int maxSubcategoryDepth = 0;

        FeedSubscriptionDao feedSubscriptionDao = new FeedSubscriptionDao();

        FeedSubscriptionCriteria feedSubscriptionCriteria = new FeedSubscriptionCriteria();
        feedSubscriptionCriteria.setCategoryId(category.getId()); // Filter by category ID
        feedSubscriptionCriteria.setUserId(principal.getId()); // Ensure user context
        List<FeedSubscriptionDto> feedSubscriptionList = feedSubscriptionDao.findByCriteria(feedSubscriptionCriteria);

        for (FeedSubscriptionDto feedSubscriptionDto : feedSubscriptionList) {
            if (unread && feedSubscriptionDto.getUnreadUserArticleCount() <= 0) {
                continue; // Skip if unread-only and no unread items
            }
            JSONObject subscriptionJson = createSubscriptionJsonObject(feedSubscriptionDto);
            if (subscriptionJson != null) { // Check for null subscriptionJson
                subscriptionsJson.put(subscriptionJson);
                categoryUnreadCount += subscriptionJson.optInt("unread_count", 0); // Use optInt with default 0
                categoryTotalCount += subscriptionJson.optInt("total_count", 0);   // Use optInt with default 0
            }
        }
        // Recursively process subcategories
        CategoryDao categoryDao = new CategoryDao();
        List<Category> subCategoryList = categoryDao.findSubCategory(category.getId(), principal.getId());
        for (Category subCategory : subCategoryList) {
            JSONObject subCategoryJson = buildCategoryJsonRecursive(subCategory, unread, level + 1);
            if (subCategoryJson != null) { // Check for null subCategoryJson
                categoriesJson.put(subCategoryJson);
                categoryUnreadCount += subCategoryJson.optInt("unread_count", 0); // Use optInt with default 0
                categoryTotalCount += subCategoryJson.optInt("total_count", 0);     // Use optInt with default 0
                maxSubcategoryDepth = Math.max(maxSubcategoryDepth, subCategoryJson.optInt("max_subcategory_depth", 0)); // Use optInt with default 0
            }
        }

        categoryJson.put("subscriptions", subscriptionsJson);
        categoryJson.put("categories", categoriesJson);
        categoryJson.put("unread_count", categoryUnreadCount);
        categoryJson.put("total_count", categoryTotalCount);
        categoryJson.put("max_subcategory_depth", maxSubcategoryDepth + 1);

        return categoryJson;
    }

    private JSONObject createCategoryJsonObject(Category category, int level) throws JSONException {
        JSONObject categoryJson = new JSONObject();
        categoryJson.put("id", category.getId());
        categoryJson.put("name", category.getName());
        categoryJson.put("nesting_level", level);
        categoryJson.put("max_subcategory_depth", 0); // Default max depth to 0, will be updated in recursion
        categoryJson.put("folded", category.isFolded());
        categoryJson.put("subscriptions", new JSONArray());
        categoryJson.put("categories", new JSONArray());
        categoryJson.put("unread_count", 0);
        categoryJson.put("total_count", 0);
        return categoryJson;
    }

    /**
     * Creates a JSON object for a feed subscription.
     * @param feedSubscription FeedSubscription providing subscription data
     * @return JSONObject for the subscription
     * @throws JSONException if JSON object creation fails
     */
    private JSONObject createSubscriptionJsonObject(FeedSubscriptionDto feedSubscriptionDto) throws JSONException {
        if (feedSubscriptionDto == null) {
            System.err.println("Error: createSubscriptionJsonObject called with null feedSubscription.");
            return null; // Return null to indicate an error
        }
    
        JSONObject subscription = new JSONObject();
        subscription.put("id", feedSubscriptionDto.getId());
        subscription.put("title", feedSubscriptionDto.getFeedSubscriptionTitle());
        subscription.put("url", feedSubscriptionDto.getFeedRssUrl());
        subscription.put("unread_count", feedSubscriptionDto.getUnreadUserArticleCount());
        subscription.put("sync_fail_count", feedSubscriptionDto.getSynchronizationFailCount());

        // Create count criteria
        UserArticleCriteria countCriteria = new UserArticleCriteria()
        .setUserId(principal.getId())
        .setFeedId(feedSubscriptionDto.getFeedId())
        .setSubscribed(true)
        .setVisible(true);

        // Get total count directly using DAO
        int totalArticleCount = new UserArticleDao().findByCriteria(countCriteria).size();
        subscription.put("total_count", totalArticleCount);
    
        return subscription;
    }
    
    /**
     * Returns the subscription informations and paginated articles.
     * 
     * @param id Subscription ID
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
        
        // Get the subscription
        FeedSubscriptionCriteria feedSubscriptionCriteria = new FeedSubscriptionCriteria()
                .setId(id)
                .setUserId(principal.getId());
        
        FeedSubscriptionDao feedSubscriptionDao = new FeedSubscriptionDao();
        List<FeedSubscriptionDto> feedSubscriptionList = feedSubscriptionDao.findByCriteria(feedSubscriptionCriteria);
        if (feedSubscriptionList.isEmpty()) {
            throw new ClientException("SubscriptionNotFound", MessageFormat.format("Subscription not found: {0}", id));
        }
        FeedSubscriptionDto feedSubscription = feedSubscriptionList.iterator().next();

        // Get the articles
        UserArticleDao userArticleDao = new UserArticleDao();
        UserArticleCriteria userArticleCriteria = new UserArticleCriteria()
                .setUnread(unread)
                .setUserId(principal.getId())
                .setSubscribed(true)
                .setVisible(true)
                .setFeedId(feedSubscription.getFeedId());
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

        JSONObject subscription = new JSONObject();
        subscription.put("title", feedSubscription.getFeedSubscriptionTitle());
        subscription.put("feed_title", feedSubscription.getFeedTitle());
        subscription.put("url", feedSubscription.getFeedUrl());
        subscription.put("rss_url", feedSubscription.getFeedRssUrl());
        subscription.put("description", feedSubscription.getFeedDescription());
        subscription.put("category_id", feedSubscription.getCategoryId());
        subscription.put("category_name", feedSubscription.getCategoryName());
        subscription.put("create_date", feedSubscription.getCreateDate().getTime());
        response.put("subscription", subscription);
        
        List<JSONObject> articles = new ArrayList<JSONObject>();
        for (UserArticleDto userArticle : paginatedList.getResultList()) {
            articles.add(ArticleAssembler.asJson(userArticle));
        }
        response.put("articles", articles);

        return Response.ok().entity(response).build();
    }
    
    /**
     * Returns the subscription synchronizations.
     * 
     * @param id Subscription ID
     * @return Response
     */
    @GET
    @Path("{id: [a-z0-9\\-]+}/sync")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSynchronization(
            @PathParam("id") String id) throws JSONException {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        
        // Get the subscription
        FeedSubscriptionCriteria feedSubscriptionCriteria = new FeedSubscriptionCriteria()
                .setId(id)
                .setUserId(principal.getId());
        
        FeedSubscriptionDao feedSubscriptionDao = new FeedSubscriptionDao();
        List<FeedSubscriptionDto> feedSubscriptionList = feedSubscriptionDao.findByCriteria(feedSubscriptionCriteria);
        if (feedSubscriptionList.isEmpty()) {
            throw new ClientException("SubscriptionNotFound", MessageFormat.format("Subscription not found: {0}", id));
        }
        FeedSubscriptionDto feedSubscription = feedSubscriptionList.iterator().next();

        // Get the feed synchronization
        FeedSynchronizationDao feedSynchronizationDao = new FeedSynchronizationDao();
        List<FeedSynchronization> feedSynchronizationList = feedSynchronizationDao.findByFeedId(feedSubscription.getFeedId());

        // Build the response
        JSONObject response = new JSONObject();

        List<JSONObject> synchronizationsJson = new ArrayList<JSONObject>();
        for (FeedSynchronization feedSynchronization : feedSynchronizationList) {
            JSONObject synchronizationJson = new JSONObject();
            synchronizationJson.put("success", feedSynchronization.isSuccess());
            synchronizationJson.put("message", feedSynchronization.getMessage());
            synchronizationJson.put("duration", feedSynchronization.getDuration());
            synchronizationJson.put("create_date", feedSynchronization.getCreateDate().getTime());
            synchronizationsJson.add(synchronizationJson);
        }
        response.put("synchronizations", synchronizationsJson);

        return Response.ok().entity(response).build();
    }
    
    /**
     * Adds a subscription to a feed.
     * 
     * @param url URL of a feed, or a web page referencing a feed 
     * @param title Feed title
     * @return Response
     */
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    public Response add(
            @FormParam("url") String url,
            @FormParam("title") String title) throws JSONException {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        
        // Validate input data
        ValidationUtil.validateRequired(url, "url");
        // url = ValidationUtil.validateHttpUrl(url, "url");
        title = ValidationUtil.validateLength(title, "title", null, 100, true);
        
        // Check if the user is already subscribed to this feed
        FeedSubscriptionCriteria feedSubscriptionCriteria = new FeedSubscriptionCriteria()
                .setUserId(principal.getId())
                .setFeedUrl(url);
        FeedSubscriptionDao feedSubscriptionDao = new FeedSubscriptionDao();
        List<FeedSubscriptionDto> feedSubscriptionList = feedSubscriptionDao.findByCriteria(feedSubscriptionCriteria);
        if (!feedSubscriptionList.isEmpty()) {
            throw new ClientException("AlreadySubscribed", "You are already subscribed to this URL");
        }
        
        // Get feed and articles
        Feed feed;
        final FeedService feedService = AppContext.getInstance().getFeedService();
        ArticleFeedService articleFeedService = new ArticleFeedService();
        FeedDao feedDao = new FeedDao();
        try {
            feed = feedService.synchronize(url);
        } catch (Exception e) {
            try {
                System.out.println("hi");
                System.out.println("hi");
                System.out.println("hi");
                System.out.println("hi");
                System.out.println(url);
                System.out.println(url);
                System.out.println(url);
                System.out.println(url);
                System.out.println(url);
                feed = feedDao.getByRssUrl(url);
                System.out.println("h2i");
                System.out.println("h2i");
                System.out.println("h2i");
                System.out.println("h2i");
            } catch (Exception e1) {
                throw new ServerException("FeedError", MessageFormat.format("Error retrieving feed at {0}", url), e);
                // TODO NoFeedFound if it isn't a feed or a page referencing a feed
            }
        }
        
        // Check again that we are not subscribed, in case the page URL was replaced by the feed URL
        feedSubscriptionCriteria = new FeedSubscriptionCriteria()
                .setUserId(principal.getId())
                .setFeedUrl(feed.getRssUrl());
        feedSubscriptionList = feedSubscriptionDao.findByCriteria(feedSubscriptionCriteria);
        if (!feedSubscriptionList.isEmpty()) {
            throw new ClientException("AlreadySubscribed", "You are already subscribed to this URL");
        }

        // Get the root category
        CategoryDao categoryDao = new CategoryDao();
        Category category = categoryDao.getRootCategory(principal.getId());
        
        // Get the display order
        Integer displayOrder = feedSubscriptionDao.getCategoryCount(category.getId(), principal.getId());
        
        // Create the subscription
        FeedSubscription feedSubscription = new FeedSubscription();
        feedSubscription.setUserId(principal.getId());
        feedSubscription.setFeedId(feed.getId());
        feedSubscription.setCategoryId(category.getId());
        feedSubscription.setOrder(displayOrder);
        feedSubscription.setUnreadCount(0);
        feedSubscription.setTitle(title);
        String feedSubscriptionId = feedSubscriptionDao.create(feedSubscription);
        
        // Create the initial article subscriptions for this user
        EntityManagerUtil.flush();
        articleFeedService.createInitialUserArticle(principal.getId(), feedSubscription);

        JSONObject response = new JSONObject();
        response.put("id", feedSubscriptionId);
        return Response.ok().entity(response).build();
    }

    /**
     * Updates the subscription.
     * 
     * @param id Subscription ID
     * @param title Subscription title (overrides the title set in the RSS feed)
     * @param categoryId Category ID
     * @param order Display order of this subscription in its category
     * @return Response
     */
    @POST
    @Path("{id: [a-z0-9\\-]+}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response update(
            @PathParam("id") String id,
            @FormParam("title") String title,
            @FormParam("category") String categoryId,
            @FormParam("order") Integer order) throws JSONException {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        
        // Validate input data
        title = ValidationUtil.validateLength(title, "name", 1, 100, true);
        
        // Get the subscription
        FeedSubscriptionDao feedSubscriptionDao = new FeedSubscriptionDao();
        FeedSubscription feedSubscription = feedSubscriptionDao.getFeedSubscription(id, principal.getId());
        if (feedSubscription == null) {
            throw new ClientException("SubscriptionNotFound", MessageFormat.format("Subscription not found: {0}", id));
        }
        
        // Update the subscription
        if (StringUtils.isNotBlank(title)) {
            feedSubscription.setTitle(title);
        }
        if (StringUtils.isNotBlank(categoryId)) {
            CategoryDao categoryDao = new CategoryDao();
            try {
                categoryDao.getCategory(categoryId, principal.getId());
            } catch (NoResultException e) {
                throw new ClientException("CategoryNotFound", MessageFormat.format("Category not found: {0}", categoryId));
            }

            feedSubscription.setCategoryId(categoryId);
        }
        feedSubscriptionDao.update(feedSubscription);
        
        // Reorder categories
        if (order != null) {
            feedSubscriptionDao.reorder(feedSubscription, order);
        }
        

        // Always return ok
        JSONObject response = new JSONObject();
        response.put("status", "ok");
        return Response.ok().entity(response).build();
    }

    /**
     * Returns the favicon of this subscription, or the default favicon.
     * 
     * @param id Subscription ID
     * @return Response
     */
    @GET
    @Path("{id: [a-z0-9\\-]+}/favicon")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response favicon(@PathParam("id") String id) {
        try {
            if (!authenticate()) {
                throw new ForbiddenClientException();
            }
    
            // Get the subscription
            FeedSubscriptionDao feedSubscriptionDao = new FeedSubscriptionDao();
            final FeedSubscription feedSubscription = feedSubscriptionDao.getFeedSubscription(id, principal.getId());
            if (feedSubscription == null) {
                throw new ClientException("SubscriptionNotFound", 
                    MessageFormat.format("Subscription not found: {0}", id));
            }
    
            // Get the favicon
            File faviconDirectory = DirectoryUtil.getFaviconDirectory();
            File[] matchingFiles = faviconDirectory.listFiles((dir, name) -> name.startsWith(feedSubscription.getFeedId()));
    
            File faviconFile;
            if (matchingFiles != null && matchingFiles.length > 0) {
                faviconFile = matchingFiles[0];
            } else {
                faviconFile = new File(getClass().getResource("/image/subscription.png").toURI());
            }
    
            FileInputStream fis = new FileInputStream(faviconFile);
            StreamingOutput stream = os -> {
                try {
                    ByteStreams.copy(fis, os);
                } finally {
                    fis.close();
                }
            };
    
            return Response.ok(stream)
                    .header("Expires", new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z")
                            .format(new Date().getTime() + 3600000 * 24 * 7))
                    .header("Content-Disposition", 
                            MessageFormat.format("attachment; filename=\"{0}\"", faviconFile.getName()))
                    .build();
        } catch (ForbiddenClientException e) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("Access Denied: " + e.getMessage())
                    .build();
        } catch (ClientException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("Error: " + e.getMessage())
                    .build();
        } catch (Exception e) {
            e.printStackTrace(); // Log error details
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("An unexpected error occurred: " + e.getMessage())
                    .build();
        }
    }
    
    /**
     * Marks all articles in this subscription as read.
     * 
     * @param id Subscription ID
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
        
        // Get the subscription
        FeedSubscriptionDao feedSubscriptionDao = new FeedSubscriptionDao();
        FeedSubscription feedSubscription = feedSubscriptionDao.getFeedSubscription(id, principal.getId());
        if (feedSubscription == null) {
            throw new ClientException("SubscriptionNotFound", MessageFormat.format("Subscription not found: {0}", id));
        }
        
        // Marks all articles as read in this subscription
        UserArticleDao userArticleDao = new UserArticleDao();
        userArticleDao.markAsRead(new UserArticleCriteria()
                .setUserId(principal.getId())
                .setSubscribed(true)
                .setFeedSubscriptionId(id));

        feedSubscriptionDao.updateUnreadCount(feedSubscription.getId(), 0);

        // Always return ok
        JSONObject response = new JSONObject();
        response.put("status", "ok");
        return Response.ok().entity(response).build();
    }

    /**
     * Deletes a subscription.
     * 
     * @param id Subscription ID
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

        // Get the subscription
        FeedSubscriptionDao feedSubscriptionDao = new FeedSubscriptionDao();
        FeedSubscription feedSubscription = feedSubscriptionDao.getFeedSubscription(id, principal.getId());
        if (feedSubscription == null) {
            throw new ClientException("SubscriptionNotFound", MessageFormat.format("Subscription not found: {0}", id));
        }
        
        // Delete the subscription
        feedSubscriptionDao.delete(id);
        
        // Always return ok
        JSONObject response = new JSONObject();
        response.put("status", "ok");
        return Response.ok().entity(response).build();
    }

    /**
     * Imports some data into the user's account.
     * The content of the file to import must be PUT as multipart/form-data.
     * The file can be either a OPML file, or a ZIP containing an OPML file and some Google Takeout data.
     * 
     * @param fileBodyPart File to import
     * @return Response
     */
    @PUT
    @Consumes("multipart/form-data") 
    @Path("import")
    public Response importFile(
            @FormDataParam("file") FormDataBodyPart fileBodyPart) throws JSONException {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        // checkBaseFunction(BaseFunction.IMPORT);

        if (!hasBaseFunction(BaseFunction.IMPORT)) {
            throw new ForbiddenClientException();
        }
        
        // Validate input data
        ValidationUtil.validateRequired(fileBodyPart, "file");

        UserDao userDao = new UserDao();
        User user = userDao.getById(principal.getId());
        
        InputStream in = fileBodyPart.getValueAs(InputStream.class);
        File importFile = null;
        try {
            // Copy the incoming stream content into a temporary file
            importFile = File.createTempFile("reader_opml_import", null);
            IOUtils.copy(in, new FileOutputStream(importFile));
             
            SubscriptionImportedEvent event = new SubscriptionImportedEvent();
            event.setUser(user);
            event.setImportFile(importFile);
            AppContext.getInstance().getImportEventBus().post(event);

            // Always return ok
            JSONObject response = new JSONObject();
            response.put("status", "ok");
            return Response.ok().entity(response).build();
        } catch (Exception e) {
            if (importFile != null) {
                try {
                    importFile.delete();
                } catch (SecurityException e2) {
                    // NOP
                }
            }
            throw new ServerException("ImportError", "Error importing OPML file", e);
        }
    }

    /**
     * Exports all the user's feeds to an OPML file.
     * 
     * @return Response
     */
    @GET
    @Path("export")
    @Produces(MediaType.APPLICATION_XML)
    public Response export() throws JSONException {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        
        // Create the XML document
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder;
        try {
            builder = factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new ServerException("UnknownError", "Error building export file", e);
        }
        DOMImplementation impl = builder.getDOMImplementation();
        Document opmlDocument = impl.createDocument(null, null, null);
        opmlDocument.setXmlStandalone(true);
        Element opmlElement = opmlDocument.createElement("opml");
        opmlElement.setAttribute("version", "1.0");
        opmlDocument.appendChild(opmlElement);

        // Add head element
        Element headElement = opmlDocument.createElement("head");
        opmlElement.appendChild(headElement);

        // Add title element
        Element titleElement = opmlDocument.createElement("title");
        titleElement.setTextContent(MessageUtil.getMessage(principal.getLocale(), "reader.export.title", principal.getName()));
        headElement.appendChild(titleElement);

        // Add body element
        Element bodyElement = opmlDocument.createElement("body");
        opmlElement.appendChild(bodyElement);

        // Search this user's subscriptions
        FeedSubscriptionCriteria feedSubscriptionCriteria = new FeedSubscriptionCriteria()
                .setUserId(principal.getId());
        FeedSubscriptionDao feedSubscriptionDao = new FeedSubscriptionDao();
        List<FeedSubscriptionDto> feedSubscriptionList = feedSubscriptionDao.findByCriteria(feedSubscriptionCriteria);
        
        // Add the categories
        String oldCategoryId = null;
        Element categoryOutlineElement = bodyElement;
        for (FeedSubscriptionDto feedSubscription : feedSubscriptionList) {
            String categoryId = feedSubscription.getCategoryId();
            
            if (!categoryId.equals(oldCategoryId)) {
                if (feedSubscription.getCategoryParentId() != null) {
                    categoryOutlineElement = opmlDocument.createElement("outline");
                    categoryOutlineElement.setAttribute("title", feedSubscription.getCategoryName());
                    categoryOutlineElement.setAttribute("text", feedSubscription.getCategoryName());
                    bodyElement.appendChild(categoryOutlineElement);
                } else {
                    categoryOutlineElement = bodyElement;
                }
            }
            Element subscriptionOutlineElement = opmlDocument.createElement("outline");
            subscriptionOutlineElement.setAttribute("type", "rss");
            subscriptionOutlineElement.setAttribute("title", feedSubscription.getFeedSubscriptionTitle());
            subscriptionOutlineElement.setAttribute("text", feedSubscription.getFeedSubscriptionTitle());
            subscriptionOutlineElement.setAttribute("xmlUrl", feedSubscription.getFeedRssUrl());
            subscriptionOutlineElement.setAttribute("htmlUrl", feedSubscription.getFeedUrl());
            categoryOutlineElement.appendChild(subscriptionOutlineElement);
            
            oldCategoryId = categoryId;
        }

        ResponseBuilder response = Response.ok();
        final String fileName = "subscriptions.xml";
        response.header("Content-Disposition", MessageFormat.format("attachment; filename=\"{0}\"", fileName));

        DOMSource domSource = new DOMSource(opmlDocument);
        return response.entity(domSource).build();
    }
}
