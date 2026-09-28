@import me.hejl.kwatern.view.Pages.HomePage
@import me.hejl.kwatern.web.Ui
@param Ui ui
@param HomePage page
@template.layout(ui = ui, title = ui.t("home.title"), content = @`
<div class="hero">
    <div class="hero-inner">
        <div class="hero-text">
            <h1>${ui.t("site.title")}</h1>
            @if(page.exported() != null)<p class="summary">${ui.t("home.exported", page.exported())}</p>@endif
        </div>
    </div>
</div>
<div class="columns">
    <main>
        <section class="card">
            <h2>${ui.t("home.overview")}</h2>
            <dl class="stats">
                <div><dt>${ui.t("stats.people")}</dt><dd>${page.people()}</dd></div>
                <div><dt>${ui.t("stats.families")}</dt><dd>${page.families()}</dd></div>
                <div><dt>${ui.t("stats.events")}</dt><dd>${page.events()}</dd></div>
                <div><dt>${ui.t("stats.places")}</dt><dd>${page.places()}</dd></div>
                <div><dt>${ui.t("stats.sources")}</dt><dd>${page.sources()}</dd></div>
                <div><dt>${ui.t("stats.media")}</dt><dd>${page.media()}</dd></div>
            </dl>
            <p><a href="/surnames">${ui.t("home.surnames", String.valueOf(page.surnames()))}</a> · <a href="/places">${ui.t("home.places")}</a> · <a href="/sources">${ui.t("home.sources")}</a></p>
        </section>
    </main>
    <aside>
        @if(page.homePerson() != null)
        <section class="card">
            <h2>${ui.t("home.start")}</h2>
            <div class="rows-list">@template.part.row(link = page.homePerson())</div>
        </section>
        @endif
    </aside>
</div>
`)
