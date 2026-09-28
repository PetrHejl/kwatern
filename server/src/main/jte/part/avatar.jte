@import me.hejl.kwatern.view.Pages.PersonLink
@param PersonLink link
@if(link == null || link.living())<span class="avatar living" aria-hidden="true"></span>@else<span class="avatar ${link.gender()}" aria-hidden="true">${link.initials()}</span>@endif
