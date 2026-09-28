@import java.util.List
@import me.hejl.kwatern.view.Pages.PlacePage
@import me.hejl.kwatern.web.Ui
@param Ui ui
@param PlacePage page
@template.layout(ui = ui, title = page.title(), map = !page.markers().isEmpty(), content = @`
<div class="hero">
    <div class="hero-inner">
        <div class="hero-text">
            @if(!page.breadcrumb().isEmpty())
            <nav class="breadcrumb" aria-label="${ui.t("place.hierarchy")}">
            @for(var p : page.breadcrumb())<a href="${p.url()}">${p.name()}</a> › @endfor
            </nav>
            @endif
            <h1><b>${page.title()}</b></h1>
            @if(!page.summary().isEmpty())<p class="summary">${page.summary()}</p>@endif
        </div>
    </div>
</div>
<div class="columns">
    <main>
        @if(!page.within().isEmpty())
        <section class="card">
            <h2>${ui.t("place.contains", page.within().size())}</h2>
            <div class="areas">
            @for(var a : page.within())
                <a class="area" href="${a.url()}">
                    <span class="area-head"><span class="row-name">${a.name()}</span><span class="row-sub">${ui.join(a.type(), a.events() > 0 ? ui.t("place.events", a.events()) : "")}</span></span>
                    <progress class="bar" max="100" value="${a.share()}" aria-hidden="true"></progress>
                </a>
            @endfor
            </div>
        </section>
        @endif
        @if(!page.events().isEmpty())
        <section class="card">
            <h2>${page.within().isEmpty() ? ui.t("place.happened") : ui.t("place.happened_within")}</h2>
            <div class="timeline">
                @template.part.placeevents(ui = ui, events = page.events().subList(0, Math.min(15, page.events().size())))
            </div>
            @if(page.events().size() > 15)
            <details class="more-rows">
                <summary>${ui.t("place.more_events", page.events().size() - 15)}</summary>
                <div class="timeline">
                    @template.part.placeevents(ui = ui, events = page.events().subList(15, page.events().size()))
                </div>
                @if(page.moreEvents() > 0)<p class="muted">${ui.t("place.not_listed", page.moreEvents())}</p>@endif
            </details>
            @endif
        </section>
        @endif
        @template.part.mediatiles(ui = ui, tiles = page.media(), title = ui.t("photos.count", page.media().size()), limit = 6)
        @template.part.details(ui = ui, attributes = List.of(), notes = page.notes(), sources = page.sources())
    </main>
    <aside>
        @if(!page.markers().isEmpty())
        <section class="card map-card">
            <div class="map small" data-tiles="${ui.map().tiles()}" data-attribution="${ui.map().attribution()}" data-kind="single" data-markers="${page.markers()}" role="region" aria-label="${ui.t("map.title")}"></div>
            <p class="map-foot"><a href="/map">${ui.t("map.larger")}</a></p>
        </section>
        @endif
        @if(!page.surnames().isEmpty())
        <section class="card">
            <h2>${ui.t("place.surnames")}</h2>
            <p class="muted card-note">${ui.t("place.surnames_note", page.title())}</p>
            <ul class="surname-counts">
            @for(var s : page.surnames().subList(0, Math.min(12, page.surnames().size())))
                <li><a href="${s.url()}">${s.surname()}</a><span class="muted">${s.count()}</span></li>
            @endfor
            </ul>
            @if(page.surnames().size() > 12)
            <details class="more-rows">
                <summary>${ui.t("show_more", page.surnames().size() - 12)}</summary>
                <ul class="surname-counts">
                @for(var s : page.surnames().subList(12, page.surnames().size()))
                    <li><a href="${s.url()}">${s.surname()}</a><span class="muted">${s.count()}</span></li>
                @endfor
                </ul>
            </details>
            @endif
        </section>
        @endif
        @if(!page.partOf().isEmpty() || !page.coordinates().isEmpty())
        <section class="card">
            <h2>${ui.t("place.part_of")}</h2>
            <div class="rows-list">
            @for(var p : page.partOf())
                @template.part.placerow(link = p)
            @endfor
            </div>
            @if(!page.coordinates().isEmpty())<p class="muted">${ui.t("place.coordinates", page.coordinates())}</p>@endif
        </section>
        @endif
        @if(!page.otherNames().isEmpty())
        <section class="card">
            <h2>${ui.t("place.names")}</h2>
            <dl class="rows">
            @for(var n : page.otherNames())
                <dt>${n.label()}</dt><dd>${n.value()}</dd>
            @endfor
            </dl>
        </section>
        @endif
    </aside>
</div>
`)
