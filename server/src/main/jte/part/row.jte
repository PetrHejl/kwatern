@import me.hejl.kwatern.view.Pages.PersonLink
@param PersonLink link
@if(link != null)
<a class="row ${link.living() ? "living" : ""}" href="${link.url()}">
    @template.part.avatar(link = link)
    <span class="row-text"><span class="row-name">${link.name()}</span>@if(!link.lifespan().isEmpty())<span class="row-sub">${link.lifespan()}</span>@endif</span>
    @if(!link.genderLabel().isEmpty())<span class="sr">${link.genderLabel()}</span>@endif
    <span class="chevron" aria-hidden="true">›</span>
</a>
@endif
