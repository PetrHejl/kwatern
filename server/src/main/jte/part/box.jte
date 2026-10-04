@import me.hejl.kwatern.view.Pages.PersonLink
@import me.hejl.kwatern.web.Ui
@param Ui ui
@param PersonLink link
@param String role
@param String relation
@if(link == null)
<div class="box missing">
    @template.part.avatar(link = null)
    <span class="box-text"><span class="box-name">${ui.t("unknown")}</span>@if(!role.isEmpty())<span class="box-sub">${role}</span>@endif</span>
</div>
@else
<a class="box ${link.living() ? "living" : ""}" href="${link.url()}">
    @template.part.avatar(link = link)
    <span class="box-text"><span class="box-name">${link.name()}</span>@if(!ui.parts(role, link.lifespan(), relation).isEmpty())<span class="box-sub">@for(var p : ui.parts(role, link.lifespan(), relation))@if(p != ui.parts(role, link.lifespan(), relation).getFirst()) · @endif<span class="nowrap${p == relation ? " relation" : ""}">${p}</span>@endfor</span>@endif</span>
    @if(!link.genderLabel().isEmpty())<span class="sr">${link.genderLabel()}</span>@endif
</a>
@endif
