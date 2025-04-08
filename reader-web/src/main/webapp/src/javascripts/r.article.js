/**
 * Initializing article module.
 */
r.article.init = function() {
  // Delegate on mark as unread checkboxes
  r.feed.cache.container.on('change', '.feed-item .feed-item-unread input', function() {
    var checked = $(this).is(':checked');
    var item = $(this).closest('.feed-item');
    
    if (checked) {
      item.addClass('forceunread');
      r.article.read(item, false);
    } else {
      item.removeClass('forceunread');
      r.article.read(item, true);
    }
  });
  
// Delegate on star buttons
r.feed.cache.container.on('click', '.feed-item .feed-item-star', function(e) {
  var item = $(this).closest('.feed-item');
  var article = item.data('article');
  // IMPORTANT: Store the original state *before* toggling
  var wasStarred = article.is_starred;
  article.is_starred = !article.is_starred;

  // Update local star and item state
  if (article.is_starred) {
      item.find('.feed-item-star').addClass('starred');
      item.addClass('starred');
  } else {
      item.find('.feed-item-star').removeClass('starred');
      item.removeClass('starred');
  }

  // Prepare the data to send to the trending backend.
  const articleData = {
      id: article.title, // Make sure article.id is correctly set
      articleObject: article,
  };

  // Choose the correct endpoint based on the *original* starred state
  let apiUrl = wasStarred ? '../api/trending/destar' : '../api/trending/star';

  // --- First API Call (Trending) ---
  fetch(apiUrl, {
      method: 'POST',
      headers: {
          'Content-Type': 'application/json'
      },
      body: JSON.stringify(articleData)
  })
  .then(response => {
      if (!response.ok) {
          // IMPORTANT: DO NOT Revert UI changes here, only log/alert errors
          throw new Error(`Trending API request failed with status ${response.status}`);
      }
      return response.json();
  })
  .then(data => {
      console.log('Trending API Success:', data);
  })
  .catch(error => {
      console.error('Trending API Error:', error);
      // Display an error, but DON'T revert the UI.
      alert('An error occurred while updating the trending count.');
  });


  // --- Second API Call (Original) ---
  r.util.ajax({
      url: r.util.url.starred_star.replace('{id}', article.id),
      type: article.is_starred ? 'PUT' : 'DELETE', // Use the *current* is_starred
      success: function() {
          console.log('Original Star API Success');
      },
      error: function(xhr, textStatus, errorThrown) {
          console.error('Original Star API Error:', textStatus, errorThrown);
           // Revert the UI if the *original* API call fails
          article.is_starred = wasStarred;
          if (wasStarred) {
              item.find('.feed-item-star').addClass('starred');
              item.addClass('starred');
          } else {
              item.find('.feed-item-star').removeClass('starred');
              item.removeClass('starred');
          }
          alert("Failed to update starred status.");

      }
  });

  e.stopPropagation();
});
  
  // Delegate on article collapsed header click
  r.feed.cache.container.on('click', '.feed-item .collapsed .container', function() {
    var item = $(this).closest('.feed-item');
    var container = item.parent();
    
    // Add or remove unfolded mode for article
    if (item.hasClass('unfolded')) {
      item.removeClass('unfolded');
      
      // Delete article description
      item.find('.feed-item-description').html('');
    } else {
      container.find('.feed-item.unfolded').removeClass('unfolded');
      item.addClass('unfolded');
      
      // Fill article description
      var article = item.data('article');
      item.find('.feed-item-description').html(article.description);
      
      // Scroll to the beginning of this article
      r.feed.scrollTop(item.position().top + container.scrollTop() + 1);
    }
  });
  
  // Delegate on article content click
  r.feed.cache.container.on('click', '.feed-item .header, .feed-item .content', function() {
    var item = $(this).closest('.feed-item');
    var container = item.parent();
      
    // Scroll to the beginning of this article
    if (!item.hasClass('selected')) {
      r.feed.scrollTop(item.position().top + container.scrollTop() + 1, true);
    }
  });
  
  // Delegate on link click in content
  r.feed.cache.container.on('click', '.feed-item .content a', function() {
    $(this).target = '_blank';
    window.open($(this).prop('href'));
    return false;
  });
};

/**
 * Mark articles as read or unread.
 */
r.article.read = function(items, read) {
  // Update application counts when an article is read or unread
  var updateReadState = function(item) {
    // Update tree unread counts
    var count = read ? -1 : -2;
    var article = item.data('article');
    var subscriptionId = article.subscription.id;
    var subscription = $('#subscription-list').find('li.subscription[data-subscription-id="' + subscriptionId + '"]');
    r.subscription.updateUnreadCount(subscription, count); // Update article's subscription

    // Update parent categories (only 1)
    subscription.parents('li.category').each(function(i, category) {
      r.subscription.updateUnreadCount($(category), count);
    });

    // Update main unread item and title
    count = r.subscription.updateUnreadCount($('#unread-feed-button'), count);
    r.subscription.updateTitle(count);
  };

  var articleIdList = [];

  items.each(function() {
    var item = $(this);

    var current = item.hasClass('read');

    // Do nothing if read state has not changed
    if (current == read) {
      return;
    }

    // Update item state
    read ? item.addClass('read') : item.removeClass('read');

    articleIdList.push(item.attr('data-article-id'));
  });

  // Do nothing if no article to mark as read
  if (articleIdList.length == 0) {
    return;
  }
  
  // Calling API
  var url = read ? r.util.url.articles_read : r.util.url.article_unread;
  r.util.ajax({
    url: url.replace('{id}', articleIdList[0]), // Unread is not supported for multiple articles
    traditional: true,
    data: { id: articleIdList },
    type: 'POST',
    done: function() {
      items.each(function() {
        updateReadState($(this));
      });
    }
  });
};

/**
 * Build an article from server data.
 */
r.article.build = function(article, classes) {
  var item = $('#template').find('.feed-item').clone();
  var date = moment(article.date);
  
  if (!r.user.isDisplayTitle()) {
    // Remove collapsed container in full mode
    item.find('.collapsed').remove();
  } else {
    // Remove title header star button in list mode
    item.find('.header .feed-item-star').remove();
  }
  
  // Store server data in element
  item.data('article', article);
  
  // Article state
  item.attr('data-article-id', article.id);
  if (article.is_read) {
    item.addClass('read');
  }
  if (article.is_starred) {
    item.addClass('starred');
  }
  
  // Copy provided classes
  if (classes) {
    item.attr('class', classes);
  }
  
  // Articles fields
  var title = article.title;
  if (article.url) {
    title = '<a href="' + article.url + '" target="_blank">' + title + '</a>';
  }
  item.find('.feed-item-title').html(title);
  
  item.find('.feed-item-date')
    .html(date.fromNow())
    .attr('title', date.format('L LT'));
  
  if (!r.feed.context.subscriptionId) {
    if (article.subscription.id) {
      item.find('.feed-item-subscription').html($.t('article.subscription', { subscription: '<a href="#/feed/subscription/' + article.subscription.id + '">' + article.subscription.title + '</a>' }));
    } else {
      item.find('.feed-item-subscription').html($.t('article.subscription', { subscription: article.subscription.title }));
    }
  } else {
    item.find('.feed-item-subscription').remove();
  }
  
  if (article.creator) {
    item.find('.feed-item-creator').html($.t('article.creator', { creator: article.creator }));
  }
  
  // In list mode, don't fill the description now
  if (!r.user.isDisplayTitle()) {
    item.find('.feed-item-description').html(article.description);
  }
  
  if (article.comment_count > 0) {
    var html = article.comment_count + ' comments';
    if (article.comment_url) {
      html = '<a href="' + article.comment_url + '" target="_blank">' + html + '</a>';
    }
    item.find('.feed-item-comments').html(html);
  }
  
  if (article.is_starred) {
    item.find('.feed-item-star').addClass('starred');
  }
  
  // Enclosure
  if (article.enclosure) {
    var html = '<a href="' + article.enclosure.url + '" target="_blank">';
    if (article.enclosure.type) {
      var type = article.enclosure.type.split('/');
      html += '<img src="images/mime/' + type[0] + '.png" title="' + article.enclosure.type + '" />';
    } else {
      html += '<img src="images/mime/text.png" />';
    }
    var url = article.enclosure.url.split('/');
    html += ' ' + url[url.length - 1];
    if (article.enclosure.length) {
      html += ' (' + Math.round(article.enclosure.length / 104858) / 10 + 'MB)';
    }
    html += '</a>';
    item.find('.feed-item-enclosure').html(html);
  }
  
  // Collapsed fields
  item.find('.feed-item-collapsed-subscription').html(article.subscription.title);
  item.find('.feed-item-collapsed-title').html(article.title);
  if (article.url) {
    item.find('.feed-item-collapsed-link').html('<a href="' + article.url + '" target="_blank"><img src="images/external.png" /></a>');
  }
  item.find('.feed-item-collapsed-description').html(article.description.replace(/(<([^>]+)>)/ig, '').substring(0, 200));
  
  // Mark as unread state
  if (item.hasClass('forceunread')) {
    item.find('.feed-item-unread input').attr('checked', 'checked');
  }
  
  // Sharing links
  if (article.url) {
    item.find('.feed-item-share a').each(function(i, link) {
      var href = $(link).attr('href');
      href = href
        .replace('${title}', article.title)
        .replace('${url}', article.url);
      $(link).attr('href', href);
    });
  } else {
    item.find('.feed-item-share').remove();
  }

  // --- COMPARE CHECKBOX HANDLING ---
  const compareCheckbox = item.find('.feed-item-compare input[name="compare"]');
    if (compareCheckbox.length) { //Ensure checkbox is there.
      // Use the 'article.url' for checking if it's in comparedArticles.
        const articleUrl = r.ArticleCompare.extractUrlFromElement(item[0]); // Use item[0] to get the DOM element
        if (articleUrl && r.ArticleCompare.comparedArticles.some(a => a.url === articleUrl)) {
            compareCheckbox.prop('checked', true);
        }
    }
    // //add event listener to do compare and reset button.
    // item.find('#doCompareButton').on('click', function() {
    //     r.ArticleCompare.handleCompareClick();
    // });
    //   item.find('#resetSelectionButton').on('click', function() {
    //     r.ArticleCompare.resetComparison();
    // });
  
  return item;
};

/**
 * Returns article item top position.
 */
r.article.top = function(item, scroll) {
  var top = item[0].offsetTop - scroll;
  item.data('top', top);
  return top;
};