import { Graphviz } from "https://cdn.jsdelivr.net/npm/@hpcc-js/wasm/dist/graphviz.js";

async function fetchJsonAndDisplayGraph(url) {
    const response = await fetch(url)
    const dot = await response.text()

    const graphviz = await Graphviz.load();
    const div = document.getElementById("placeholder");
    div.innerHTML = graphviz.layout(dot, "svg", "dot")
}

export default fetchJsonAndDisplayGraph
