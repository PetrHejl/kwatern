@import java.util.List
@import me.hejl.kwatern.view.Pages.WebLink
@import me.hejl.kwatern.web.Ui
@param Ui ui
@param List<WebLink> links
@if(!links.isEmpty())
<section class="card">
    <h2>${ui.t("links")}</h2>
    <ul class="web-links">
    @for(var l : links)
        <li><a class="external" href="${l.url()}" rel="noreferrer"><span class="web-link-text"><span class="web-link-label">${l.label()}</span>@if(!l.site().isEmpty())<span class="muted">${l.site()}</span>@endif</span></a></li>
    @endfor
    </ul>
</section>
@endif
