/**
 * Summary module.
 */
r.summary = {
    /**
     * Cache for jQuery objects.
     */
    cache: {
      container: null,
      refreshButton: null,
      summaryTable: null,
    },
  
    /**
     * Initialize the summary module.
     */
    init: function () {
      // Populate the cache
      r.summary.cache.container = $('#summary-container');
      r.summary.cache.refreshButton = $('#refresh-summary-button');
      r.summary.cache.summaryTable = $('#summaryTable');
  
      // Attach event listener to the refresh button
      r.summary.cache.refreshButton.on('click', r.summary.fetchAndDisplaySummary);
    },
  
    /**
     * Fetches all articles and then requests the summary, displaying the result.
     */
    fetchAndDisplaySummary: function () {
      // Show loading indicator (optional)
      r.summary.cache.summaryTable.html('<img src="images/ajax-loader.gif" alt="Loading..." />');
  
  
          r.util.ajax({
              url: r.util.url.all + "/category",
              type: 'GET',
              data: {
                  limit: 1000,
                  unread: false // Consider whether you really need *all* articles, even read ones
              },
              done: function (allArticlesData) {
                  // allArticlesData is the response from /api/article/all, hopefully an object like {articles: [...]}
                  console.log("All articles data:", allArticlesData); // Log the response for debugging
                  // Now make the POST request to ../api/summary
                  r.util.ajax({
                      url: '../api/summary',
                      type: 'POST',
                      contentType: 'application/json', // Important: Set content type for JSON payload
                      data: JSON.stringify(allArticlesData), // Send the entire response from /all
                      done: function (summaryData) {
                          // summaryData is the response from /api/summary
                          r.summary.displaySummary(summaryData);
                      },
                      fail: function (jqXHR, textStatus, errorThrown) {
                          r.summary.cache.summaryTable.html('<p>Error fetching summary: ' + textStatus + '</p>');
                          console.error("Error in summary request:", textStatus, errorThrown); // Log detailed error
                      }
                  });
              },
              fail: function (jqXHR, textStatus, errorThrown) {
                  r.summary.cache.summaryTable.html('<p>Error fetching articles: ' + textStatus + '</p>');
                  console.error("Error in articles request:", textStatus, errorThrown); // Log detailed error
  
              }
          });
    },
  
    /**
     * Displays the summary data in the summaryTable div.
     * @param {object} summaryData The data returned from the /api/summary endpoint.
     */
    displaySummary: function (summaryData) {
      let summaryHTML = '';
  
      if (summaryData && summaryData.status === 'success') {
        summaryHTML += `<p>Summary: ${summaryData.summary}</p>`;
      } else {
        summaryHTML = '<p>No summary data available.</p>';
      }
  
      r.summary.cache.summaryTable.html(summaryHTML);
    },
  };
  
  // Call init when the document is ready.
  $(document).ready(function () {
    r.summary.init();
  });
