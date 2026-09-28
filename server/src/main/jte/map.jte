@import me.hejl.kwatern.view.Pages.TreeMapPage
@import me.hejl.kwatern.web.Ui
@param Ui ui
@param TreeMapPage page
@template.layout(ui = ui, title = ui.t("map.title"), map = true, content = @`
<div class="hero">
    <div class="hero-inner">
        <div class="hero-text">
            <h1>${ui.t("map.title")}</h1>
            <p class="summary">${ui.t("map.summary", page.mapped(), page.unmapped())}</p>
        </div>
    </div>
</div>
<div class="page">
    <section class="card map-card">
        <nav class="chart-options map-filters">
            <span class="muted">${ui.t("map.show")}</span>
            @for(var k : page.kinds())@if(k.active())<b>${k.label()}</b>@else<a href="${k.url()}">${k.label()}</a>@endif @endfor
            <span class="separator" aria-hidden="true">|</span>
            <span class="muted">${ui.t("map.period")}</span>
            @for(var p : page.periods())@if(p.active())<b>${p.label()}</b>@else<a href="${p.url()}">${p.label()}</a>@endif @endfor
        </nav>
        <div class="map full" data-tiles="${ui.map().tiles()}" data-attribution="${ui.map().attribution()}" data-kind="dots" data-markers="${page.markers()}" role="region" aria-label="${ui.t("map.title")}"></div>
        <p class="card-note">${ui.t("map.tree_note")}</p>
    </section>
</div>
`)
