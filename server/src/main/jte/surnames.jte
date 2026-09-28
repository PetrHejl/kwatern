@import me.hejl.kwatern.view.Pages.SurnameIndex
@import me.hejl.kwatern.web.Ui
@param Ui ui
@param SurnameIndex page
@template.layout(ui = ui, title = ui.t("surnames.title"), content = @`
<div class="hero">
    <div class="hero-inner">
        <div class="hero-text">
            <h1>${ui.t("surnames.title")}</h1>
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
        <ul class="surnames">
        @for(var s : letter.surnames())
            <li><a href="${s.url()}">${s.surname()}</a> <span class="muted">${s.count()}</span></li>
        @endfor
        </ul>
    </section>
@endfor
@if(page.withoutSurname() > 0)
    <p class="muted">${ui.t("surnames.without", String.valueOf(page.withoutSurname()))}</p>
@endif
</div>
`)
