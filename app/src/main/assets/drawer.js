function drawChart(spreadsheetId) {
    var queryString = encodeURIComponent('SELECT A, B OFFSET 1');

    var query = new google.visualization.Query(
        'https://docs.google.com/spreadsheets/d/' + spreadsheetId + '/gviz/tq?gid=0&headers=1&tq=' + queryString);
    query.send(handleQueryResponse);
}

function handleQueryResponse(response) {
    if (response.isError()) {
        alert('Error in query: ' + response.getMessage() + ' ' + response.getDetailedMessage());
        return;
    }

    var data = response.getDataTable();
    
    var view = new google.visualization.DataView(data);
    var days = 3;
    view.setColumns([0, 1, {
        type: 'number',
        label: 'Trend',
        calc: function (dt, row) {
            // calculate average of closing value for last x values,
            // if we are x or more values into the data set
            if (row >= days - 1) {
                var total = 0;
                for (var i = 0; i < days; i++) {
                    total += dt.getValue(row - i, 1);
                }
                var avg = total / days;
                return {v: avg, f: avg.toFixed(2)};
            }
            else {
                // return null for < x days
                return null;
            }
        }
    }]);
    var chart = new google.visualization.ScatterChart(document.getElementById('chart_div'));
    chart.draw(view, {
        height: 350, width: 600,
        title: 'Weight over time',
        hAxis: {title: 'Date'},
        vAxis: {title: 'Weight'},
        series: {
            0: { lineWidth: 0 },
            1: {
                pointSize: 0,
                lineWidth: 3,
                lineDashStyle: [6, 9]
            }
        }
    });
}