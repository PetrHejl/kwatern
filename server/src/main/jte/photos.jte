@import me.hejl.kwatern.view.Pages.PhotosPage
@import me.hejl.kwatern.web.Ui
@param Ui ui
@param PhotosPage page
@template.layout(ui = ui, title = ui.t("photos.title"), content = @`
<div class="hero">
    <div class="hero-inner">
        <div class="hero-text">
            <h1>${ui.t("photos.title")}</h1>
            <p class="summary">${ui.t("photos.total", page.total())}</p>
        </div>
    </div>
</div>
<div class="page">
    @if(page.groups().size() > 1)
    <nav class="letters">
    @for(var g : page.groups())
        <a href="#${g.id()}">${g.label()}</a>
    @endfor
    </nav>
    @endif
    @for(var g : page.groups())
    <div id="${g.id()}">
        @template.part.mediatiles(ui = ui, tiles = g.tiles(), title = page.groups().size() > 1 ? g.label() + " · " + g.tiles().size() : "")
    </div>
    @endfor
</div>
`)
