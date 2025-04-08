/**
 * Initializes trending articles module.
 */
r.Trending = {
    init: function() {
        // Add a click handler for the "View Trending Articles" link
        $('#qtip-settings').on('click', 'a[href="#/trendingarticles/"]', function(event) {
            event.preventDefault(); // Prevent default link behavior
            r.Trending.showTrendingArticles();
        });

        // Initialize the container and event handlers *only once*.
        r.Trending.setupContainer();
    },

    // Sets up the container and event handlers.  This is now separate from creating the HTML.
    setupContainer: function() {
        // Add event listener for refresh button (only needs to be done once)
        $('#refresh-trending-articles').on('click', function() {
            r.Trending.fetchAndDisplayTrendingArticles();
        });

        // Show "Click Refresh" message by default
        r.Trending.showClickRefreshMessage();
    },

     showClickRefreshMessage: function() {
        const tbody = $('#trending-articles-table tbody');
        tbody.empty();
        tbody.append('<tr><td colspan="4">Click Refresh to view trending articles.</td></tr>');
    },

    showTrendingArticles: function() {
        r.main.reset(); // Clear other content
        $('#toolbar > .about').removeClass('hidden'); // Show toolbar button (if you have one)

        $('#trending-articles-container').show(); // Show the container

        // Don't fetch on initial show, just show the "Click Refresh" message
        // r.Trending.fetchAndDisplayTrendingArticles(); // REMOVE THIS
    },

    fetchAndDisplayTrendingArticles: function() {
        fetch('../api/trending/top')
            .then(response => {
                if (!response.ok) {
                    throw new Error(`Network response was not ok: ${response.status}`);
                }
                return response.json();
            })
            .then(data => {
                r.Trending.displayTrendingArticles(data.topArticles);
            })
            .catch(error => {
                console.error('Error fetching trending articles:', error);
                const tbody = $('#trending-articles-table tbody');
                tbody.empty(); // Clear previous content
                tbody.append('<tr><td colspan="4">Error loading trending articles.</td></tr>');
            });
    },

   displayTrendingArticles: function (articles) {
        const tbody = $('#trending-articles-table tbody'); // Target the tbody directly
        tbody.empty(); // Clear previous content

        if (articles.length === 0) {
            tbody.append('<tr><td colspan="4">No trending articles found.</td></tr>'); // Span all columns
            return;
        }

        articles.forEach(articleWrapper => {
            // Access the article object correctly
            const article = articleWrapper.articleObject;
            const starCount = articleWrapper.starCount;
            const articleId = article.title; // Use article.title as ID

            const row = $('<tr>').appendTo(tbody);
            $('<td>').text(article.title).appendTo(row);
            $('<td>').text(starCount).appendTo(row); // Use starCount from wrapper
            $('<td>').text(article.description || '').appendTo(row); // Show Description

            // Create a clickable link for the URL (assuming the URL is in article.url)
            const urlCell = $('<td>').appendTo(row);
            if (article.url) {
              $('<a>').attr('href', article.url).attr('target', '_blank').text(article.url.substring(0, 50) + (article.url.length > 50 ? "..." : "")).appendTo(urlCell); // prevent overflow and use ...
            } else {
                urlCell.text("No URL"); // Handle cases where URL might be missing
            }
        });
    },

    handleStarClick: function(articleElement, starButton) {
        const articleData = r.Trending.getArticleData(articleElement); // Get *all* article data

        fetch('../api/trending/star', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(articleData) // Send the complete object
        })
        .then(response => response.json())
        .then(data => {
            console.log('Star Success:', data);
            starButton.classList.add('starred');
            // r.Trending.updateStarCountDisplay(starButton, data.newStarCount);  // No longer needed
        })
        .catch(error => console.error('Error:', error));
    },

    handleDestarClick: function(articleElement, starButton) {
        const articleData = r.Trending.getArticleData(articleElement);  // Get *all* article data

        fetch('../api/trending/destar', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(articleData) // Send the complete object
        })
        .then(response => response.json())
        .then(data => {
            console.log('Destar Success:', data);
            starButton.classList.remove('starred');
            // r.Trending.updateStarCountDisplay(starButton, data.newStarCount); // No longer needed

        })
        .catch(error => console.error('Error:', error));
    },

    getArticleData: function (articleElement) {
        const title = articleElement.querySelector('.feed-item-title')?.textContent.trim() || "";
        const url = r.ArticleCompare.extractUrlFromElement(articleElement); // Reuse
        const description = articleElement.querySelector('.feed-item-description')?.textContent.trim() || "";
        const subscription = articleElement.querySelector('.feed-item-subscription')?.textContent.trim() || "";
        const creator = articleElement.querySelector('.feed-item-creator')?.textContent.trim() || "";
        const dateElement = articleElement.querySelector('.feed-item-date');
        const date = dateElement ? dateElement.textContent.trim() : "";


        // Construct the *full* article object, mimicking what r.article.js might provide
        const article = {
            id: title,       // Use the title as the ID
            title: title,
            url: url,
            description: description,
            subscription: subscription,
            creator: creator,
            date: date,
            // Add other fields from the articleElement as needed, e.g.,
            // is_read: ...,
            // is_starred: ...,
        };

        return {
            id: article.title,  // The ID (title)
            articleObject: article, // The complete article object
        };
    },
};

// Initialize when the document is ready
$(document).ready(function() {
    r.Trending.init();
});