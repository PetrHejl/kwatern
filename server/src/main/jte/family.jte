@import me.hejl.kwatern.view.Pages.FamilyPage
@import me.hejl.kwatern.web.Ui
@param Ui ui
@param FamilyPage page
@template.layout(ui = ui, title = page.title(), content = @`
<div class="hero">
    <div class="hero-inner">
        <div class="hero-text">
            <h1>${page.title()}</h1>
            @if(!page.relationship().isEmpty())<p class="summary">${page.relationship()}</p>@endif
        </div>
    </div>
</div>
<div class="columns">
    <main>
        @template.part.mediatiles(ui = ui, tiles = page.media(), title = ui.t("photos.count", page.media().size()), limit = 4, compact = true)
        @if(!page.timeline().isEmpty())
        <section class="card">
            <h2>${ui.t("timeline")}</h2>
            @template.part.timeline(ui = ui, events = page.timeline())
        </section>
        @endif
        @template.part.details(ui = ui, attributes = page.attributes(), notes = page.notes(), sources = page.sources())
    </main>
    <aside>
        <section class="card">
            <h2>${ui.t("parents")}</h2>
            <div class="d-parents">
                @template.part.box(ui = ui, link = page.father(), role = ui.t("father"), relation = "")
                @template.part.box(ui = ui, link = page.mother(), role = ui.t("mother"), relation = "")
            </div>
        </section>
        @if(!page.children().isEmpty())
        <section class="card">
            <h2>${ui.t("children.count", page.children().size())}</h2>
            <div class="d-children">
            @for(var c : page.children())
                @template.part.box(ui = ui, link = c.person(), role = "", relation = c.relation())
            @endfor
            </div>
        </section>
        @endif
    </aside>
</div>
`)
