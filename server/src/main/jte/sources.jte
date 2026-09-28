@import me.hejl.kwatern.view.Pages.SourcesPage
@import me.hejl.kwatern.web.Ui
@param Ui ui
@param SourcesPage page
@template.layout(ui = ui, title = ui.t("sources.title"), content = @`
<div class="hero">
    <div class="hero-inner">
        <div class="hero-text">
            <h1>${ui.t("sources.title")}</h1>
            <p class="summary">${ui.t("sources.summary", page.total())}</p>
            <nav class="letters" aria-label="${ui.t("surnames.letters")}">
            @for(var letter : page.letters())
                <a href="#${letter.label()}">${letter.label()}</a>
            @endfor
            </nav>
        </div>
    </div>
</div>
<div class="page">
@for(var letter : page.letters())
    <section class="card" id="${letter.label()}">
        <h2>${letter.label()}</h2>
        <div class="rows-list columns-2">
        @for(var s : letter.sources())
            <a class="row" href="${s.url()}">
                <span class="row-text"><span class="row-name">${s.title()}</span><span class="row-sub">${ui.join(s.author(), ui.t("source.cited", s.citations()))}</span></span>
                <span class="chevron" aria-hidden="true">›</span>
            </a>
        @endfor
        </div>
    </section>
@endfor
</div>
`)
