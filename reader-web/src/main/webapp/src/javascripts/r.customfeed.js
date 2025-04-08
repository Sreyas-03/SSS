r.CustomFeed = {};
r.CustomFeed.init = function () {
  console.log("r.CustomFeed.init() started"); // Add this

  $("#feed-submit-button").click(function () {
    console.log("Submit Feed button clicked!");
    r.CustomFeed.submitFeed();
  });

  console.log("Calling r.CustomFeed.addArticleFields()"); // Add this
  r.CustomFeed.addArticleFields();
  console.log("r.CustomFeed.addArticleFields() finished"); // Add this

  console.log("Calling r.CustomFeed.initializeViewCustomFeedsTable()"); // Add this
  r.CustomFeed.initializeViewCustomFeedsTable();
  console.log("r.CustomFeed.initializeViewCustomFeedsTable() finished"); //Add this

  $("#refresh-custom-feeds-button").click(function () {
    console.log("Refresh Custom Feeds button clicked!");
    r.CustomFeed.loadCustomFeeds();
  });
};

r.CustomFeed.addArticleFields = function () {
    const container = $('#customfeed-container');
    let articleCount = 2; // Start at 2, since we already have article_url1 and article_url2

    const maxArticles = 10;  // Maximum number of articles allowed


    function addField() {
        if (articleCount < maxArticles) {
            articleCount++;
            const newField = $(`<textarea id="article_url${articleCount}" rows="1" cols="50" placeholder="Enter article URL"></textarea>`);
            container.find('#feed-submit-button').before(newField);

            // Attach event listener *immediately* after creation
            newField.on('input', checkAndAddField);
        }
    }


    function checkAndAddField() {
        let allFilled = true;
        for (let i = 1; i <= articleCount; i++) {
            if ($(`#article_url${i}`).val().trim() === "") {
                allFilled = false;
                break;
            }
        }
        if (allFilled && articleCount < maxArticles) {
            addField();
        }
    }


    // Initial event listeners
    for (let i = 1; i <= articleCount; i++) {
        $(`#article_url${i}`).on('input', checkAndAddField);
    }
};

r.CustomFeed.submitFeed = function () {
  const feedTitle = $("#feed_title").val().trim();
  const articleUrls = [];

  if (!feedTitle) {
    alert("Please enter a feed description.");
    return;
  }

  // Collect URLs
  for (let i = 1; i <= 10; i++) {
    const url = $(`#article_url${i}`).val();
    if (url && url.trim() !== "") {
      const trimmedUrl = url.trim();
      if (!isValidUrl(trimmedUrl)) {
        alert("Please enter a valid URL for article " + i);
        return;
      }
      articleUrls.push(trimmedUrl);
    }
  }

  if (articleUrls.length === 0) {
    alert("Please enter at least one article URL.");
    return;
  }

  const newFeed = {
    title: feedTitle,
    articles: articleUrls,
  };

  $("#feed-submit-button").prop("disabled", true); // Use .prop() for boolean attributes

  r.util.ajax({
    url: "../api/customFeed",
    type: "POST",
    contentType: "application/json",
    data: JSON.stringify(newFeed),
    done: function (response) {
      console.log("Submit success:", response);
      alert("Custom feed submitted successfully!");
      $("#feed_title").val("");

      // Clear all article URL fields and re-add the initial fields
      // Iterate backwards to avoid issues with removing elements while looping
      for (let i = 10; i >= 1; i--) {
        $(`#article_url${i}`).remove(); // Remove the elements
      }

      // Re-add the initial article URL fields
      const container = $("#customfeed-container");
      container
        .find("#feed-submit-button")
        .before(
          '<textarea id="article_url1" rows="1" cols="50" placeholder="Enter article URL"></textarea>'
        );
      container
        .find("#feed-submit-button")
        .before(
          '<textarea id="article_url2" rows="1" cols="50" placeholder="Enter article URL"></textarea>'
        );

      r.CustomFeed.addArticleFields(); // Re-initialize the dynamic fields.
    },
    fail: function (error) {
      console.error("Submit error:", error);
      alert(
        "Error submitting custom feed: " +
          (error.responseJSON?.error || "Unknown error")
      );
    },
    always: function () {
      $("#feed-submit-button").prop("disabled", false); // Re-enable the button
    },
  });
};

r.CustomFeed.initializeViewCustomFeedsTable = function () {
  $("#customfeedstable tbody").empty();
  $("#customfeedstable tbody").append(
    '<tr><td colspan="3">Press "Refresh Custom Feeds" to load your custom feeds.</td></tr>'
  );
};

function isValidUrl(url) {
  try {
    new URL(url);
    return true;
  } catch (_) {
    return false;
  }
}

r.CustomFeed.loadCustomFeeds = function () {
  $("#customfeedstable tbody").empty();

  r.util.ajax({
    url: "../api/customFeed/view",
    type: "GET",
    dataType: "json",
    done: function (response) {
      console.log("Load success:", response); // DEBUG
      if (response && response.feeds && Array.isArray(response.feeds)) {
        var feeds = response.feeds;
        var tableBody = $("#customfeedstable tbody");

        $.each(feeds, function (index, feed) {
          var row = $("<tr>");
          row.append($("<td>").text(feed.author || ""));
          row.append($("<td>").text(feed.title || ""));
          row.append($("<td>").text(feed.feedId || ""));
          tableBody.append(row);
        });
      } else {
        $("#customfeedstable tbody").append(
          '<tr><td colspan="3">No custom feeds found.</td></tr>'
        );
      }
    },
    fail: function (error) {
      console.error("Load error:", error); // DEBUG
      alert(
        "Error loading custom feeds: " +
          (error.responseJSON?.error || "Unknown error")
      );
      $("#customfeedstable tbody").append(
        '<tr><td colspan="3">Error loading custom feeds.</td></tr>'
      );
    },
  });
};
