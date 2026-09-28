// Maps of the site, drawn with Leaflet (bundled in /static/leaflet). Each element with class "map" says what to show
// in data attributes, as the Content Security Policy allows no inline scripts:
//   data-tiles, data-attribution  the tile server and its credit
//   data-markers                  JSON: [{lat, lon, label, title, url, size, secondary}]
//   data-kind                     "numbered" (pins with numbers), "dots" (circles by size) or "single"
// Text from the data is only ever set as text, never as HTML.
(function () {
    "use strict";

    function link(marker) {
        var box = document.createElement("div");
        var a = document.createElement("a");
        a.href = marker.url;
        a.textContent = marker.title;
        box.appendChild(a);
        if (marker.note) {
            var note = document.createElement("div");
            note.className = "map-note";
            note.textContent = marker.note;
            box.appendChild(note);
        }
        return box;
    }

    function pin(marker) {
        var span = document.createElement("span");
        span.className = "map-pin" + (marker.secondary ? " secondary" : "");
        span.textContent = marker.label || "";
        return L.divIcon({ className: "map-pin-holder", html: span, iconSize: null, iconAnchor: [15, 15] });
    }

    function show(element) {
        var markers = JSON.parse(element.getAttribute("data-markers") || "[]");
        var kind = element.getAttribute("data-kind");
        var map = L.map(element, { scrollWheelZoom: false });
        L.tileLayer(element.getAttribute("data-tiles"), {
            maxZoom: 18,
            attribution: element.getAttribute("data-attribution")
        }).addTo(map);
        var points = [];
        markers.forEach(function (marker) {
            var point = [marker.lat, marker.lon];
            points.push(point);
            var layer = kind === "dots"
                ? L.circleMarker(point, {
                    radius: marker.size || 6,
                    className: "map-dot",
                    weight: 2
                })
                : L.marker(point, { icon: pin(marker), title: marker.title, keyboard: true });
            layer.bindPopup(link(marker));
            layer.addTo(map);
        });
        if (points.length === 0) {
            map.setView([30, 0], 2);
        } else if (points.length === 1 || kind === "single") {
            map.setView(points[0], 11);
        } else {
            map.fitBounds(points, { padding: [30, 30], maxZoom: 12 });
        }
    }

    document.querySelectorAll(".map[data-tiles]").forEach(show);
})();
