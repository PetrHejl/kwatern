@import me.hejl.kwatern.view.Pages.PersonHero
@import me.hejl.kwatern.web.Ui
@param Ui ui
@param PersonHero hero
<div class="hero">
    <div class="hero-inner">
        @if(hero.portrait().isEmpty())
        <span class="avatar hero-avatar ${hero.living() ? "living" : hero.gender()}" aria-hidden="true">${hero.initials()}</span>
        @else
        <img class="avatar hero-avatar portrait" src="${hero.portrait()}" alt="" width="112" height="112">
        @endif
        <div class="hero-text">
            @if(hero.living())
            <h1 class="living">${hero.heading().before()}</h1>
            <p class="summary">${ui.t("living.notice")}</p>
            @else
            <h1>${hero.heading().before()} <b>${hero.heading().surname()}</b> ${hero.heading().after()}</h1>
            @if(!hero.summary().isEmpty())<p class="summary">${hero.summary()}</p>@endif
            @if(!hero.details().isEmpty() || !hero.surnameUrl().isEmpty())
            <p class="details">${hero.details()}@if(!hero.details().isEmpty() && !hero.surnameUrl().isEmpty()) · @endif@if(!hero.surnameUrl().isEmpty())<a href="${hero.surnameUrl()}">${hero.surnameLink()}</a>@endif</p>
            @endif
            @endif
        </div>
    </div>
    <nav class="tabs" aria-label="${ui.t("tab.label")}">
    @for(var t : hero.tabs())
        <a href="${t.url()}" class="${t.active() ? "active" : null}" aria-current="${t.active() ? "page" : null}">${t.label()}</a>
    @endfor
    </nav>
</div>
