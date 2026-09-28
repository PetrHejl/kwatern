@import java.util.List
@import me.hejl.kwatern.view.Pages.SourcePage
@import me.hejl.kwatern.web.Ui
@param Ui ui
@param SourcePage page
@template.layout(ui = ui, title = page.title(), content = @`
<div class="hero">
    <div class="hero-inner">
        <div class="hero-text">
            <h1><b>${page.title()}</b></h1>
            <p class="summary">${ui.t("source.cited", page.citations().size())}</p>
        </div>
    </div>
</div>
<div class="columns">
    <main>
        @if(!page.citations().isEmpty())
        <section class="card">
            <h2>${ui.t("source.citations")}</h2>
            <div class="citations">
            @for(var c : page.citations())
                <div class="citation-entry${c.media().isEmpty() ? "" : " with-media"}">
                    @if(!c.media().isEmpty())
                    <div class="citation-media">
                    @for(var t : c.media())
                        @template.part.tile(ui = ui, tile = t)
                    @endfor
                    </div>
                    @endif
                    <div>
                    <div class="t-when">${ui.join(c.page(), c.date(), ui.t("source.confidence", c.confidence()))}</div>
                    <ul>
                    @for(var w : c.cited())
                        <li>@if(!w.url().isEmpty())<a href="${w.url()}">${w.label()}</a>@else${w.label()}@endif</li>
                    @endfor
                    </ul>
                    </div>
                </div>
            @endfor
            </div>
        </section>
        @endif
        @template.part.mediatiles(ui = ui, tiles = page.media(), title = ui.t("photos.count", page.media().size()), limit = 6)
        @template.part.details(ui = ui, attributes = List.of(), notes = page.notes(), sources = List.of())
    </main>
    <aside>
        @if(!page.details().isEmpty())
        <section class="card">
            <h2>${ui.t("source.details")}</h2>
            <dl class="rows">
            @for(var d : page.details())
                <dt>${d.label()}</dt><dd>${d.value()}</dd>
            @endfor
            </dl>
        </section>
        @endif
        @if(!page.repositories().isEmpty())
        <section class="card">
            <h2>${ui.t("source.repositories")}</h2>
            <dl class="rows">
            @for(var r : page.repositories())
                <dt>${r.label()}</dt><dd>${r.value()}</dd>
            @endfor
            </dl>
        </section>
        @endif
    </aside>
</div>
`)
