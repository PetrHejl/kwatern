@import me.hejl.kwatern.view.Pages.PlaceLink
@param PlaceLink link
<a class="row" href="${link.url()}">
    <span class="row-text"><span class="row-name">${link.name()}</span>@if(!link.type().isEmpty())<span class="row-sub">${link.type()}</span>@endif</span>
    <span class="chevron" aria-hidden="true">›</span>
</a>
