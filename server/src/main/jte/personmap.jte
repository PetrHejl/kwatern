@import me.hejl.kwatern.view.Pages.PersonMapPage
@import me.hejl.kwatern.web.Ui
@param Ui ui
@param PersonMapPage page
@template.layout(ui = ui, title = page.hero().title() + " · " + ui.t("tab.map"), map = true, content = @`
@template.part.personhero(ui = ui, hero = page.hero())
<div class="columns">
    <main>
        <section class="card map-card">
            <div class="map large" data-tiles="${ui.map().tiles()}" data-attribution="${ui.map().attribution()}" data-kind="numbered" data-markers="${page.markers()}" role="region" aria-label="${ui.t("map.title")}"></div>
            <p class="map-foot"><a href="${page.familyToggle()}">${page.family() ? ui.t("map.family.hide") : ui.t("map.family.show")}</a></p>
        </section>
    </main>
    <aside>
        <section class="card">
            <h2>${ui.t("map.places", page.stops().size())}</h2>
            @if(page.stops().isEmpty())<p class="muted">${ui.t("map.no_places")}</p>@endif
            <ol class="stops">
            @for(var s : page.stops())
                <li>
                    <span class="badge-number${s.mapped() ? "" : " unmapped"}" aria-hidden="true">${s.number()}</span>
                    <span class="stop-text"><a href="${s.url()}">${s.name()}</a>
                    <span class="row-sub">${s.events()}@if(!s.mapped()) · ${ui.t("map.no_coordinates")}@elseif(!s.shownAt().isEmpty()) · ${ui.t("map.shown_at", s.shownAt())}@endif</span></span>
                </li>
            @endfor
            </ol>
        </section>
    </aside>
</div>
`)
