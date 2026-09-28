@import me.hejl.kwatern.view.Pages.PlacesPage
@import me.hejl.kwatern.web.Ui
@param Ui ui
@param PlacesPage page
@template.layout(ui = ui, title = ui.t("places.title"), content = @`
<div class="hero">
    <div class="hero-inner">
        <div class="hero-text">
            <h1>${ui.t("places.title")}</h1>
            <p class="summary">${ui.t("places.summary", page.total())}</p>
        </div>
    </div>
</div>
<div class="page">
    <section class="card">
        @template.part.placetree(ui = ui, nodes = page.roots(), depth = 0)
    </section>
</div>
`)
