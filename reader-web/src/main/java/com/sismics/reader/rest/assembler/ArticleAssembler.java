package com.sismics.reader.rest.assembler;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import com.sismics.reader.core.dao.jpa.dto.UserArticleDto;

/**
 * Article DTO / JSON assembler.
 *
 * @author jtremeaux 
 */
public class ArticleAssembler {

    /**
     * Returns a user article as JSON data.
     * 
     * @param userArticle User article
     * @return User article as JSON
     */
    public static JSONObject asJson(UserArticleDto userArticle) throws JSONException {
        JSONObject userArticleJson = userArticle.getArticleDtoAsJson();
        return userArticleJson;
    }
}
