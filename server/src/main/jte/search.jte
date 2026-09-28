@import me.hejl.kwatern.view.Pages.SearchPage
@import me.hejl.kwatern.web.Ui
@param Ui ui
@param SearchPage page
@template.layout(ui = ui, title = ui.t("search.title"), content = @`
<div class="hero">
    <div class="hero-inner">
        <div class="hero-text">
            <h1>${ui.t("search.title")}</h1>
            <form class="search-big" action="/search" method="get" role="search">
                <label class="sr" for="q-big">${ui.t("search.label")}</label>
                <input id="q-big" type="search" name="q" value="${page.query()}" placeholder="${ui.t("search.placeholder")}">
                <button type="submit">${ui.t("search.button")}</button>
            </form>
            @if(!page.query().isBlank())
            <p class="summary">${ui.t("search.found", page.people().size(), page.places().size())}@if(page.more()) ${ui.t("search.more")}@endif</p>
            @endif
        </div>
    </div>
</div>
@if(!page.people().isEmpty() || !page.places().isEmpty())
<div class="page">
        @if(!page.people().isEmpty())
        <section class="card">
            <h2>${ui.t("search.people")}</h2>
            <div class="rows-list columns-2">
            @for(var p : page.people())
                @template.part.row(link = p)
            @endfor
            </div>
        </section>
        @endif
        @if(!page.places().isEmpty())
        <section class="card">
            <h2>${ui.t("search.places")}</h2>
            <div class="rows-list columns-2">
            @for(var p : page.places())
                @template.part.placerow(link = p)
            @endfor
            </div>
        </section>
        @endif
</div>
@endif
`)
