/**
 * Initializing article comparison module.
 */
r.ArticleCompare = {
    comparedArticles: [], // Store compared articles directly in the object

    init: function() {
        // Event delegation for checkbox changes
        document.getElementById('feed-container').addEventListener('change', function(event) {
            if (event.target.matches('.feed-item-compare input[name="compare"]')) {
                const articleElement = event.target.closest('.feed-item');
                if (articleElement) {
                    r.ArticleCompare.updateComparedArticles(articleElement);
                }
            }
        });

        // Event delegation for the "Do Compare" button
       document.getElementById('feed-container').addEventListener('click', function(event) {
            if (event.target.matches('#doCompareButton')) {
                r.ArticleCompare.handleCompareClick();
            }
       });
        // Event delegation for reset button.
        document.getElementById('feed-container').addEventListener('click', function (event) {
            if (event.target.matches('#resetSelectionButton')) {
                r.ArticleCompare.resetComparison();
            }
        });

    },

    updateComparedArticles: function(articleElement) {
        const compareCheckbox = articleElement.querySelector('.feed-item-compare input[name="compare"]');
        if (!compareCheckbox) {
            console.error("Compare checkbox not found in:", articleElement);
            return;
        }

        const title = articleElement.querySelector('.feed-item-title')?.textContent.trim() || "";
        const description = articleElement.querySelector('.feed-item-description')?.textContent.trim() || "";
        const subscription = articleElement.querySelector('.feed-item-subscription')?.textContent.trim() || "";
        const creator = articleElement.querySelector('.feed-item-creator')?.textContent.trim() || "";
        const dateElement = articleElement.querySelector('.feed-item-date');
        const date = dateElement ? dateElement.textContent.trim() : "";
        const url = r.ArticleCompare.extractUrlFromElement(articleElement);

        if (!title || !url) {
            console.error("Missing title or URL in:", articleElement);
            return;
        }

        const articleData = {
            title: title,
            description: description,
            subscription: subscription,
            creator: creator,
            date: date,
            url: url
        };

        if (compareCheckbox.checked) {
            // Add article if not already present
            if (!this.comparedArticles.some(article => article.url === articleData.url)) {
                this.comparedArticles.push(articleData);
                console.log("Article added:", articleData);
                console.log("Current compared articles (after add):", this.comparedArticles); // Log after change
            }
        } else {
            // Remove article
            this.comparedArticles = this.comparedArticles.filter(article => article.url !== articleData.url);
            console.log("Article removed:", articleData);
            console.log("Current compared articles (after remove):", this.comparedArticles); // Log after change
        }
    },

     extractUrlFromElement: function(articleElement) {
        const collapsedLink = articleElement.querySelector('.feed-item-collapsed-link a');
        if (collapsedLink) {
            return collapsedLink.href;
        }
        const shareLinks = articleElement.querySelectorAll('.feed-item-share a');
        if (shareLinks.length > 0) {
            for (const link of shareLinks) {
                const href = link.href;
                if (href.startsWith('http')) {
                    const urlParam = new URL(href);
                    if (urlParam.searchParams.has('u')) {
                        return urlParam.searchParams.get('u');
                    } else if (urlParam.searchParams.has('url')) {
                        return urlParam.searchParams.get('url');
                    }
                }
            }
        }

        console.warn("Could not find URL for article:", articleElement);
        return null;
    },


   handleCompareClick: function() {
    if (this.comparedArticles.length < 2) {
        alert("Please select at least two articles to compare.");
        return;
    }

    fetch('../api/compareArticle', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(this.comparedArticles)
    })
    .then(response => {
        if (!response.ok) {
            // IMPORTANT: Get the response text *before* throwing the error
            return response.text().then(text => {
                // Try to parse the text as JSON, if it fails, just use the raw text
                let errorMessage = text;
                try {
                    const errorData = JSON.parse(text);
                    errorMessage = errorData.message || text; // Prefer the "message" property
                } catch (e) {
                    // If parsing fails, 'errorMessage' remains the raw text
                }
                 throw new Error(errorMessage); // Pass parsed message to the error
            });
        }
        return response.json();
    })
    .then(data => {
        console.log('Success:', data);
        // Show an alert with the comparison result
        alert(`Titles Match: ${data.titlesMatch}`);
        // REMOVE THIS LINE:
        // r.ArticleCompare.displayComparison(this.comparedArticles);
    })
    .catch(error => {
        console.error('Error:', error.message); // Log the *message* from the error
          alert(error.message); // Show the parsed/raw error message in the alert

    });
},
    // REMOVE THE ENTIRE displayComparison FUNCTION:
    // displayComparison: function(comparedArticles) { ... },

    // Added reset function.
    resetComparison: function () {
        // Clear the comparedArticles array
        this.comparedArticles = [];

        // Uncheck all "Compare" checkboxes
        const checkboxes = document.querySelectorAll('.feed-item-compare input[name="compare"]');
        checkboxes.forEach(checkbox => {
            checkbox.checked = false;
        });
        console.log("Current compared articles (after reset):", this.comparedArticles);
    }
};