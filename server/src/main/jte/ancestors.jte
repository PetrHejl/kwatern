@import me.hejl.kwatern.view.Pages.AncestorsPage
@import me.hejl.kwatern.web.Ui
@param Ui ui
@param AncestorsPage page
@template.layout(ui = ui, title = page.hero().title() + " · " + ui.t("tab.ancestors"), content = @`
@template.part.personhero(ui = ui, hero = page.hero())
<div class="page">
    <section class="card chart-card${page.fan() ? " fan-view" : " tree-view"}">
        <div class="chart-head">
            <h2>${page.heading()}</h2>
            <nav class="chart-options">
                <span class="muted">${ui.t("ancestors.generations")}</span>
                @for(var g : page.generations())
                    @if(g.active())<b>${g.label()}</b>@else<a href="${g.url()}">${g.label()}</a>@endif
                @endfor
                <span class="separator" aria-hidden="true">|</span>
                <a href="${page.otherView().url()}">${page.otherView().label()}</a>
            </nav>
        </div>
        @if(page.fan())
        <svg class="fan" viewBox="0 0 ${page.width()} ${page.height()}" role="img" aria-label="${page.heading()}">
        @for(var s : page.segments())
            @if(s.person() == null)
            <g class="fan-segment unknown"><path d="${s.path()}"></path><text transform="${s.transform()}" font-size="${s.fontSize()}"><tspan x="0">${ui.t("unknown")}</tspan></text></g>
            @else
            <a href="${s.person().url()}"><g class="fan-segment ${s.person().living() ? "living" : s.person().gender()}${s == page.segments().getFirst() ? " root" : ""}"><title>${s.person().name()}</title><path d="${s.path()}"></path><text transform="${s.transform()}" font-size="${s.fontSize()}"><tspan class="name" x="0" dy="-2">${s.name()}</tspan>@if(!s.years().isEmpty())<tspan class="years" x="0" dy="${s.fontSize() + 3}">${s.years()}</tspan>@endif</text></g></a>
            @endif
        @endfor
        </svg>
        <p class="card-note">${ui.t("ancestors.fan_note")}</p>
        @else
        <div class="tree-scroll">
            <div class="tree-columns${page.columns().size() >= 5 ? " compact" : ""}">
            @for(var c : page.columns())<span>${c}</span>@endfor
            </div>
            <svg class="tree" width="${page.width()}" height="${page.height()}" viewBox="-1 -1 ${page.width()} ${page.height()}" role="img" aria-label="${page.heading()}">
            @for(var l : page.lines())
                <line x1="${l.x1()}" y1="${l.y1()}" x2="${l.x2()}" y2="${l.y2()}"></line>
            @endfor
            @for(var b : page.boxes())
                @if(b.person() == null)
                <g class="tree-box unknown"><rect x="${b.x()}" y="${b.y()}" width="${b.width()}" height="64" rx="8"></rect><text x="${b.x() + 16}" y="${b.y() + 38}">${ui.t("unknown")}</text></g>
                @else
                <a href="${b.person().url()}"><g class="tree-box ${b.root() ? "root" : ""} ${b.person().living() ? "living" : b.person().gender()}"><title>${b.person().name()}</title>
                    <rect x="${b.x()}" y="${b.y()}" width="${b.width()}" height="64" rx="8"></rect>
                    @if(!b.person().living() && !b.compact())<circle class="initials" cx="${b.x() + 27}" cy="${b.y() + 32}" r="14"></circle><text class="initials" x="${b.x() + 27}" y="${b.y() + 36}">${b.person().initials()}</text>@endif
                    <text class="name" x="${b.x() + (b.compact() ? 14 : 52)}" y="${b.y() + 28}">${b.name()}</text>
                    @if(!b.person().lifespan().isEmpty())<text class="years" x="${b.x() + (b.compact() ? 14 : 52)}" y="${b.y() + 48}">${b.person().lifespan()}</text>@endif
                </g></a>
                @if(!b.earlier().isEmpty())<a href="${b.earlier()}" aria-label="${ui.t("ancestors.earlier", b.person().name())}"><g class="earlier"><circle cx="${b.x() + b.width() - 28}" cy="${b.y() + 32}" r="14"></circle><text x="${b.x() + b.width() - 28}" y="${b.y() + 37}">›</text></g></a>@endif
                @endif
            @endfor
            </svg>
        </div>
        <p class="card-note">${ui.t("ancestors.tree_note")}</p>
        @endif
        <div class="ancestor-list">
        @for(var g : page.list())
            <h3>${g.label()}</h3>
            @if(g.sides())<p class="side-label">${ui.t("ancestors.father_side")}</p>@endif
            <div class="rows-list">
            @for(var p : g.fatherSide())@template.part.row(link = p)@endfor
            @if(g.fatherUnknown() > 0)<p class="muted unknown-count">${ui.t("ancestors.unknown", g.fatherUnknown())}</p>@endif
            </div>
            @if(g.sides())<p class="side-label">${ui.t("ancestors.mother_side")}</p>@endif
            <div class="rows-list">
            @for(var p : g.motherSide())@template.part.row(link = p)@endfor
            @if(g.motherUnknown() > 0)<p class="muted unknown-count">${ui.t("ancestors.unknown", g.motherUnknown())}</p>@endif
            </div>
        @endfor
        </div>
    </section>
</div>
`)
