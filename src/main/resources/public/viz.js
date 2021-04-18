
function fetchJsonAndDisplayGraph(url) {
    fetch(url)
        .then(function (response) {
           return response.text()
        })
        .then(function (dot) {
            const graphviz = d3.select("body").graphviz().renderDot(dot)
        })
}