@import me.hejl.kwatern.view.Pages.PersonLink
@import me.hejl.kwatern.web.Ui
@param Ui ui
@param PersonLink link
@if(link == null)<span class="unknown">${ui.t("unknown")}</span>@else<a href="${link.url()}" class="${link.living() ? "living" : ""}">${link.name()}</a>@endif
