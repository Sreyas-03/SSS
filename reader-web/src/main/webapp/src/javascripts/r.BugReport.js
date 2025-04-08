/**
 * Initializing bug report module.
 */
r.BugReport = {};

/**
 * Initialize bug reporting feature.
 */
r.BugReport.init = function() {
    // Attach event to submit button
    $('#bug-submit-button').click(function() {
        r.BugReport.submitBug();
    });

    // Do NOT load bugs when the page loads for view bugs, initialize with empty table
    r.BugReport.initializeViewBugsTable();

    // Attach event to refresh button for view bugs
    $('#refresh-bugs-button').click(function() {
        r.BugReport.loadBugs();
    });

    // Initialize manage bugs with an empty table
    r.BugReport.initializeManageBugsTable();

    // Attach event to refresh button for manage bugs
    $('#refresh-manage-bugs-button').click(function() {
        r.BugReport.loadManageBugs();
    });
};

/**
 * Initialize the bugTable for view bugs with empty content.
 */
r.BugReport.initializeViewBugsTable = function() {
    $('#bugTable tbody').empty(); // Clear existing content
    // Optionally add a placeholder row or message
    $('#bugTable tbody').append($('<tr><td colspan="5">Press "Refresh Bugs" to load your bugs.</td></tr>'));
};

/**
 * Initialize the manageBugTable for manage bugs with empty content.
 */
r.BugReport.initializeManageBugsTable = function() {
    $('#manageBugTable tbody').empty();
    // Optionally add a placeholder row or message
    $('#manageBugTable tbody').append($('<tr><td colspan="5">Press "Refresh Bugs" to load manage bugs.</td></tr>'));
};

/**
 * Function to submit a bug report to the server.
 */
r.BugReport.submitBug = function() {
    var bugDescription = $('#bugDescription').val().trim();

    // Validate input
    if (!bugDescription) {
        alert('Please enter a bug description');
        return;
    }

    var newBug = {
        User: "Anonymous",
        Description: bugDescription,
        Status: "New",
        Timestamp: new Date().toISOString()
    };

    // Disable button during request
    $('#bug-submit-button').attr('disabled', 'disabled');

    // Send bug report to the server
    r.util.ajax({
        url: '../api/reportBug',
        type: 'POST',
        contentType: 'application/json',
        data: JSON.stringify(newBug),
        done: function(response) {
            alert('Bug report submitted successfully!');
            $('#bugDescription').val(''); // Clear form
        },
        fail: function(error) {
            alert('Error submitting bug report: ' + (error.responseJSON?.error || 'Unknown error'));
        },
        always: function() {
            // Enable button
            $('#bug-submit-button').removeAttr('disabled');
        }
    });

    // Prevent default form submission
    return false;
};

/**
 * Function to load bug reports from the server and display them in the table.
 */
r.BugReport.loadBugs = function() {
    // Clear existing table rows
    $('#bugTable tbody').empty();

    r.util.ajax({
        url: '../api/reportBug/view', // Use the new endpoint
        type: 'GET',
        dataType: 'json', // Expect JSON response
        done: function(response) {
            if (response && response.bugs && Array.isArray(response.bugs)) { // Check for valid response structure
                var bugs = response.bugs;
                $.each(bugs, function(index, bug) {
                    var row = $('<tr>');
                    row.append($('<td>').text(bug.User || ''));
                    row.append($('<td>').text(bug.Description || ''));
                    row.append($('<td>').text(bug.Status || ''));
                    row.append($('<td>').text(bug.Timestamp || ''));

                    // Actions (Delete)
                    var actionsCell = $('<td>');
                    var deleteButton = $('<button>').text('Delete').addClass('delete-bug-button').attr('data-index', index).attr('data-user', bug.User);
                    deleteButton.click(function() {
                        var bugIndex = $(this).data('index');
                        var bugUser = $(this).data('user');
                        if (confirm('Are you sure you want to delete this bug?')) {
                            r.BugReport.deleteBugFromView(bugIndex, bugUser); // Pass index and user
                        }
                    });
                    actionsCell.append(deleteButton);
                    row.append(actionsCell);

                    $('#bugTable tbody').append(row);
                });
            } else {
                // Handle case where no bugs are found or response is invalid
                $('#bugTable tbody').append($('<tr><td colspan="5">No bugs found.</td></tr>'));
            }
        },
        fail: function(error) {
            alert('Error loading bug reports: ' + (error.responseJSON?.error || 'Unknown error'));
            $('#bugTable tbody').append($('<tr><td colspan="5">Error loading bugs.</td></tr>')); // Show error in table
        }
    });
};

/**
 * Function to load bug reports from the server and display them in the manage bug table.
 */
r.BugReport.loadManageBugs = function() {
    // Clear existing table rows
    $('#manageBugTable tbody').empty();

    r.util.ajax({
        url: '../api/reportBug/manage', // Use the new endpoint
        type: 'GET',
        dataType: 'json', // Expect JSON response
        done: function(response) {
            if (response && response.bugs && Array.isArray(response.bugs)) { // Check for valid response structure
                var bugs = response.bugs;
                $.each(bugs, function(index, bug) {
                    var row = $('<tr>');
                    row.append($('<td>').text(bug.User || ''));
                    row.append($('<td>').text(bug.Description || ''));
                    // Status dropdown
                    var statusSelect = $('<select>').addClass('bug-status-select').attr('data-index', index);
                    var statuses = ["New", "In Progress", "Resolved", "Closed"]; // Define possible statuses
                    $.each(statuses, function(i, status) {
                        var option = $('<option>').val(status).text(status);
                        if (bug.Status === status) {
                            option.attr('selected', 'selected');
                        }
                        statusSelect.append(option);
                    });
                    statusSelect.change(function() {
                        var newStatus = $(this).val();
                        var bugIndex = $(this).data('index');
                        r.BugReport.updateBugStatus(bugIndex, newStatus);
                    });
                    row.append($('<td>').append(statusSelect));
                    row.append($('<td>').text(bug.Timestamp || ''));
                    // Actions (Edit, Delete)
                    var actionsCell = $('<td>');
                    var deleteButton = $('<button>').text('Delete').addClass('delete-bug-button').attr('data-index', index);
                    deleteButton.click(function() {
                        var bugIndex = $(this).data('index');
                        if (confirm('Are you sure you want to delete this bug?')) {
                            r.BugReport.deleteBug(bugIndex);
                        }
                    });
                    actionsCell.append(deleteButton);
                    row.append(actionsCell);
                    $('#manageBugTable tbody').append(row);
                });
            } else {
                // Handle case where no bugs are found or response is invalid
                $('#manageBugTable tbody').append($('<tr><td colspan="5">No bugs found.</td></tr>'));
            }
        },
        fail: function(error) {
            alert('Error loading bug reports: ' + (error.responseJSON?.error || 'Unknown error'));
            $('#manageBugTable tbody').append($('<tr><td colspan="5">Error loading bugs.</td></tr>')); // Show error in table
        }
    });
};

/**
 * Function to update the status of a bug.
 * @param {number} index The index of the bug to update.
 * @param {string} newStatus The new status for the bug.
 */
r.BugReport.updateBugStatus = function(index, newStatus) {
    // Retrieve all the bugs from the table
    var bugs = [];
    $('#manageBugTable tbody tr').each(function(i, row) {
        var user = $(row).find('td:eq(0)').text();
        var description = $(row).find('td:eq(1)').text();
        var status = $(row).find('td:eq(2) select').val(); // Get the selected value from the dropdown
        var timestamp = $(row).find('td:eq(3)').text();
        bugs.push({ User: user, Description: description, Status: status, Timestamp: timestamp });
    });

    // Update the bug status
    if (index >= 0 && index < bugs.length) {
        bugs[index].Status = newStatus;

        // Send updated bug data to the server (e.g., using PUT or POST)
        r.util.ajax({
            url: '../api/reportBug/update',  // New endpoint for updating a bug
            type: 'POST',   // Or PUT, depending on your API design
            contentType: 'application/json',
            data: JSON.stringify({ index: index, status: newStatus, bugs: bugs }), // Send the bug data
            done: function(response) {
                alert('Bug status updated successfully!');
                // Optionally, reload the bugs to reflect the change immediately
                r.BugReport.loadManageBugs();
            },
            fail: function(error) {
                alert('Error updating bug status: ' + (error.responseJSON?.error || 'Unknown error'));
            }
        });
    } else {
        alert('Invalid bug index.');
    }
};

/**
 * Function to delete a bug.  (For manage bugs - admin view)
 * @param {number} index The index of the bug to delete.
 */
r.BugReport.deleteBug = function(index) {
    // Retrieve all the bugs from the table
    var bugs = [];
    $('#manageBugTable tbody tr').each(function(i, row) {
        var user = $(row).find('td:eq(0)').text();
        var description = $(row).find('td:eq(1)').text();
        var status = $(row).find('td:eq(2) select').val(); // Get the selected value from the dropdown
        var timestamp = $(row).find('td:eq(3)').text();
        bugs.push({ User: user, Description: description, Status: status, Timestamp: timestamp });
    });

    if (index >= 0 && index < bugs.length) {
        bugs.splice(index, 1);  // Remove the bug at the given index

        // Send the updated list of bugs to the server
        r.util.ajax({
            url: '../api/reportBug/delete', // New endpoint for deleting a bug
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify({ bugs: bugs }),
            done: function(response) {
                alert('Bug deleted successfully!');
                r.BugReport.loadManageBugs();  // Reload the bug list
            },
            fail: function(error) {
                alert('Error deleting bug: ' + (error.responseJSON?.error || 'Unknown error'));
            }
        });
    } else {
        alert('Invalid bug index.');
    }
};

/**
 * Function to delete a bug from the view bugs table. (User view)
 * @param {number} index The index of the bug to delete *within the user's view*.
 * @param {string} user The username of the bug's creator.
 */
r.BugReport.deleteBugFromView = function(index, user) {
    if (!user) {
        alert('Error: Cannot determine the user to delete the bug');
        return;
    }
    // 1. Get all bugs from the server.  We can't rely on the *displayed*
    //    bugs because the index is only valid within the *filtered* set.
    r.util.ajax({
        url: '../api/reportBug', // Get ALL bugs, not just the user's
        type: 'GET',
        dataType: 'json',
        done: function(response) {
            if (response && response.bugs && Array.isArray(response.bugs)) {
                let allBugs = response.bugs;
                let bugToDeleteIndex = -1;

                // 2.  Find the index of the bug to delete in the *entire* database
                for (let i = 0; i < allBugs.length; i++) {
                    if (allBugs[i].User === user) {
                       //  Since the index is passed from the display we need to match the index against the displayed bugs
                        if (index === 0) { //This should be the bug to delete
                          bugToDeleteIndex = i;
                          break;
                        }
                        index--; //Decrement the index of the display to match the index of the db bugs
                    }
                }

                if (bugToDeleteIndex === -1) {
                    alert('Error: Bug not found in database.');
                    return;
                }

                // 3.  Delete the bug from the *allBugs* array
                allBugs.splice(bugToDeleteIndex, 1);

                // 4.  Send the updated list to the server.
                r.util.ajax({
                    url: '../api/reportBug/delete',
                    type: 'POST',
                    contentType: 'application/json',
                    data: JSON.stringify({ bugs: allBugs }),
                    done: function(response) {
                        alert('Bug deleted successfully!');
                        r.BugReport.loadBugs(); // Reload the *filtered* bugs
                    },
                    fail: function(error) {
                        alert('Error deleting bug: ' + (error.responseJSON?.error || 'Unknown error'));
                    }
                });

            } else {
                alert('Error: Could not retrieve bugs from the server.');
            }
        },
        fail: function(error) {
            alert('Error: Could not retrieve bugs from the server.');
        }
    });
};