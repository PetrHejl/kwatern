@import me.hejl.kwatern.view.Pages.DescendantsPage
@import me.hejl.kwatern.web.Ui
@param Ui ui
@param DescendantsPage page
@template.layout(ui = ui, title = page.hero().title() + " · " + ui.t("tab.descendants"), content = @`
@template.part.personhero(ui = ui, hero = page.hero())
<div class="page">
    <section class="card chart-card">
        <div class="chart-head">
            <h2>${page.heading()}</h2>
            <nav class="chart-options">
                <span class="muted">${ui.t("ancestors.generations")}</span>
                @for(var g : page.generations())
                    @if(g.active())<b>${g.label()}</b>@else<a href="${g.url()}">${g.label()}</a>@endif
                @endfor
            </nav>
        </div>
        <ul class="descendants">
            @template.part.descendant(ui = ui, node = page.root(), root = true)
        </ul>
        <p class="card-note">${ui.t("descendants.note")}</p>
    </section>
</div>
`)
