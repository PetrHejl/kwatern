@import me.hejl.kwatern.view.Pages.SurnamePage
@import me.hejl.kwatern.web.Ui
@param Ui ui
@param SurnamePage page
@template.layout(ui = ui, title = page.surname(), content = @`
<div class="hero">
    <div class="hero-inner">
        <div class="hero-text">
            <h1><b>${page.surname()}</b></h1>
            <p class="summary">${ui.t("surname.count", page.people().size())}</p>
        </div>
    </div>
</div>
<div class="page">
    <section class="card">
        <div class="rows-list columns-2">
        @for(var p : page.people())
            @template.part.row(link = p)
        @endfor
        </div>
    </section>
</div>
`)
