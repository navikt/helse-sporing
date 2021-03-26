const graph = Viva.Graph.graph(),
    graphics = Viva.Graph.View.svgGraphics(),
    nodeSize = 24,
    layout = Viva.Graph.Layout.forceDirected(graph, {
        springLength: 1000,
        springCoeff: 0.00001,
        dragCoeff: 0.1,
        gravity: 0.1
    }),
    renderer = Viva.Graph.View.renderer(graph, {
        graphics: graphics,
        layout: layout
    });

graphics.node(function(node) {
    var ui = Viva.Graph.svg('g'),
        svgText = Viva.Graph.svg('text').attr('y', '-4px').text(node.id),
        rect =  Viva.Graph.svg('rect')
            .attr('stroke-width', 10)
            .attr('stroke', 'red')
            .attr('width', 24)
            .attr('height', 24);
    ui.append(svgText);
    ui.append(rect);
    return ui;
}).placeNode(function(nodeUI, pos) {
    nodeUI.attr('transform', 'translate(' + (pos.x - nodeSize/2) + ',' + (pos.y - nodeSize/2) + ')');
});

graphics.link(function(link){
    return Viva.Graph.svg('path')
        .attr('stroke', 'red')
        .attr('stroke-dasharray', '5, 5');
}).placeLink(function(linkUI, fromPos, toPos) {
    const data = 'M' + fromPos.x + ',' + fromPos.y +
        'L' + toPos.x + ',' + toPos.y;
    linkUI.attr("d", data);
});

function fetchJsonAndDisplayGraph(url) {
    const states = new Map();
    fetch(url)
        .then(function (response) {
            return response.json();
        })
        .then(function (response) {
            response.tilstandsendringer.forEach(transition => {
                if (!states.has(transition.fraTilstand)) graph.addNode(transition.fraTilstand)
                if (!states.has(transition.tilTilstand)) graph.addNode(transition.tilTilstand)
                graph.addLink(transition.fraTilstand, transition.tilTilstand)
                console.log(`Adding ${transition.fraTilstand} -> ${transition.tilTilstand}`)
            });

            renderer.run();
        });
}