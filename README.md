# SheetPI
An example project for interacting with the Google Sheets API

The some are missing from the strings.xml file due to private information being stored there.
Missing strings are:

```
    debt_sheet_id
    weight_sheet_id
    
    option_one
    option_two
```

In addition to the strings, there is also a `chart.html` file missing from the assets folder.
It is used to display the weight chart. The file could look like this:
```
<!DOCTYPE html>
<html>
    <head>
        <title>Page Title</title>
        <script type="text/javascript" src="https://www.gstatic.com/charts/loader.js"></script>
        <script type="text/javascript" src="drawer.js"></script>
        <script type="text/javascript">
            // Load the Visualization API and the piechart package.
            google.charts.load('current', {'packages':['corechart']});

            // Set a callback to run when the Google Visualization API is loaded.
            google.charts.setOnLoadCallback(function() { drawChart('1TEgFTtgfARG6sKAeeozXw7BHQKh47KkFYlSMHHsyrIs'); });
        </script>
    </head>
    
    <body>
        <div id="chart_div" style="width:600; height:350">
            <div id="loading">Loading... Please wait.</div>
        </div>
    </body>
</html>
```

This project is meant to append certain values to a spreadsheet list. The weight spreadsheet has column A for date and column B for weight.
By appending the current measurement on the bottom of that list, the chart in the spreadsheet is automatically updated.

Similarly for the debt sheet: column A is a description, column B the amount that the "Option One"-Person ows and column C the amount of debt of the "Option Two"-Person.
New values must also be added at the bottom. Range E5:D6 is a summary of total debt and who ows who that is printed out after the submission.
