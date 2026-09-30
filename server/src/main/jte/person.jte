@import me.hejl.kwatern.view.Pages.PersonPage
@import me.hejl.kwatern.web.Ui
@param Ui ui
@param PersonPage page
@template.layout(ui = ui, title = page.hero().title(), content = @`
@template.part.personhero(ui = ui, hero = page.hero())
<div class="columns">
    <main>
        @template.part.mediatiles(ui = ui, tiles = page.media(), title = ui.t("photos.count", page.media().size()), limit = 4, compact = true)
        @if(!page.timeline().isEmpty())
        <section class="card">
            <div class="chart-head">
                <h2>${ui.t("timeline")}</h2>
                @if(!page.hero().living())
                <nav class="chart-options">
                    <span class="muted">${ui.t("timeline.family_events")}</span>
                    @if(page.familyEvents())<b>${ui.t("timeline.shown")}</b> <a href="${page.familyToggle()}">${ui.t("timeline.hide")}</a>@else<a href="${page.familyToggle()}">${ui.t("timeline.show")}</a> <b>${ui.t("timeline.hidden")}</b>@endif
                </nav>
                @endif
            </div>
            @template.part.timeline(ui = ui, events = page.timeline())
        </section>
        @endif
        @if(page.lifespans() != null)
        <section class="card lifespans-card">
            <h2>${ui.t("lifespans.title")}</h2>
            <svg class="lifespans" viewBox="0 0 ${page.lifespans().width()} ${page.lifespans().height()}" role="img" aria-label="${ui.t("lifespans.title")}">
                <rect class="band" x="${page.lifespans().bandX()}" y="24" width="${page.lifespans().bandWidth()}" height="${page.lifespans().height() - 24}"></rect>
                @for(var t : page.lifespans().ticks())
                <text class="tick" x="${t.x()}" y="15">${t.label()}</text>
                <line class="grid" x1="${t.x()}" y1="24" x2="${t.x()}" y2="${page.lifespans().height()}"></line>
                @endfor
                @for(var r : page.lifespans().rows())
                @if(!r.heading().isEmpty())
                <text class="group" x="0" y="${r.y() + 12}">${r.heading()}</text>
                @else
                @if(r.fade() > 0)
                <defs>
                    <linearGradient id="lifespan-fade-${r.y()}-gradient" gradientUnits="userSpaceOnUse" x1="${r.fade()}" x2="${r.x() + r.width()}"><stop offset="0" stop-color="white"></stop><stop offset="1" stop-color="black"></stop></linearGradient>
                    <mask id="lifespan-fade-${r.y()}" maskUnits="userSpaceOnUse"><rect x="${r.x() - 1}" y="${r.y() + 5}" width="${r.width() + 2}" height="22" fill="url(#lifespan-fade-${r.y()}-gradient)"></rect></mask>
                </defs>
                @endif
                <a href="${r.person().url()}"><g class="life ${r.person().gender()}${r.self() ? " self" : ""}"><title>${r.person().name()}</title>
                    <text class="name" x="0" y="${r.y() + 21}">${r.name()}</text>
                    <path class="bar" d="${r.bar()}" mask="${r.fade() > 0 ? "url(#lifespan-fade-" + r.y() + ")" : null}"></path>
                    <text class="years" x="${r.after() ? r.x() + r.width() + 6 : r.x() - 6}" y="${r.y() + 21}" text-anchor="${r.after() ? "start" : "end"}">${r.years()}</text>
                </g></a>
                @endif
                @endfor
            </svg>
            <p class="card-note">${page.lifespans().note()}</p>
        </section>
        @endif
        @template.part.details(ui = ui, attributes = page.attributes(), notes = page.notes(), sources = page.sources())
    </main>
    <aside>
        @template.part.diagram(ui = ui, diagram = page.diagram())
        @if(!page.siblings().isEmpty())
        <section class="card">
            <h2>${ui.t("siblings.count", page.siblings().size())}</h2>
            <div class="rows-list">
            @for(var s : page.siblings().subList(0, Math.min(5, page.siblings().size())))
                @template.part.row(link = s)
            @endfor
            @if(page.siblings().size() > 5)
            <details class="more-rows">
                <summary>${ui.t("show_more", page.siblings().size() - 5)}</summary>
                @for(var s : page.siblings().subList(5, page.siblings().size()))
                    @template.part.row(link = s)
                @endfor
            </details>
            @endif
            </div>
        </section>
        @endif
    </aside>
</div>
`)
