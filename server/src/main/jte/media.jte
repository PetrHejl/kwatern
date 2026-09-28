@import java.util.List
@import me.hejl.kwatern.view.Pages.MediaPage
@import me.hejl.kwatern.web.Ui
@param Ui ui
@param MediaPage page
@template.layout(ui = ui, title = page.title(), content = @`
<div class="hero">
    <div class="hero-inner">
        <div class="hero-text">
            <nav class="breadcrumb"><a href="/photos">${ui.t("photos.title")}</a> › <a href="${page.groupUrl()}">${page.group()}</a></nav>
            <h1 class="media-title">${page.title()}</h1>
            @if(!page.summary().isEmpty())<p class="summary">${page.summary()}</p>@endif
        </div>
    </div>
</div>
<div class="columns">
    <main>
        <section class="card photo-card">
            @if(page.image().isEmpty())
            <p class="muted">${ui.t("media.no_image")}</p>
            @else
            <figure class="photo">
                <img src="${page.image()}" alt="${page.title()}" width="${page.width()}" height="${page.height()}">
                @if(!page.marked().isEmpty())
                <svg class="regions" viewBox="0 0 ${page.width()} ${page.height()}" aria-hidden="true">
                    @for(var m : page.marked())
                    <g class="region r${m.number()}">
                        <rect x="${m.x()}" y="${m.y()}" width="${m.width()}" height="${m.height()}" rx="${page.marker() / 3}"></rect>
                        <circle cx="${m.x()}" cy="${m.y()}" r="${page.marker()}"></circle>
                        <text x="${m.x()}" y="${m.y()}" font-size="${page.marker() * 6 / 5}">${m.number()}</text>
                    </g>
                    @endfor
                </svg>
                @endif
            </figure>
            @endif
            @if(!page.marked().isEmpty() || !page.original().isEmpty())
            <p class="photo-foot">
                <span class="muted">@if(!page.marked().isEmpty())${ui.t("media.hint")}@endif</span>
                @if(!page.original().isEmpty())<a href="${page.original()}">${page.image().isEmpty() ? ui.t("media.open_file") : ui.t("media.view_full")}</a>@endif
            </p>
            @endif
            @if(!page.marked().isEmpty())
            <h2>${ui.t("media.in_photo", page.marked().size())}</h2>
            <div class="rows-list">
            @for(var m : page.marked())
                <a class="row marked m${m.number()}" href="${m.person().url()}">
                    <span class="badge-number" aria-hidden="true">${m.number()}</span>
                    @template.part.avatar(link = m.person())
                    <span class="row-text"><span class="row-name">${m.person().name()}</span>@if(!m.person().lifespan().isEmpty())<span class="row-sub">${m.person().lifespan()}</span>@endif</span>
                    <span class="chevron" aria-hidden="true">›</span>
                </a>
            @endfor
            </div>
            @endif
        </section>
        @template.part.details(ui = ui, attributes = List.of(), notes = page.notes(), sources = page.sources())
    </main>
    <aside>
        @if(!page.details().isEmpty())
        <section class="card">
            <h2>${ui.t("media.details")}</h2>
            <dl class="rows">
            @for(var d : page.details())
                <dt>${d.label()}</dt><dd>${d.value()}</dd>
            @endfor
            </dl>
        </section>
        @endif
        @if(!page.linkedFrom().isEmpty())
        <section class="card">
            <h2>${ui.t("media.linked_from")}</h2>
            <ul class="source-list">
            @for(var l : page.linkedFrom())
                <li>@if(!l.url().isEmpty())<a href="${l.url()}">${l.label()}</a>@else${l.label()}@endif</li>
            @endfor
            </ul>
        </section>
        @endif
    </aside>
</div>
`)
