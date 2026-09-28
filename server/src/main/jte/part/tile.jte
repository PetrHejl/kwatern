@import me.hejl.kwatern.view.Pages.MediaTile
@import me.hejl.kwatern.web.Ui
@param Ui ui
@param MediaTile tile
<a class="tile" href="${tile.url()}" title="${tile.title()}">
    @if(tile.thumbnail().isEmpty())<span class="tile-image none">${ui.t("media.no_preview")}</span>@else<img class="tile-image" src="${tile.thumbnail()}" alt="" loading="lazy">@endif
    <span class="tile-title">${tile.title()}</span>
    @if(!tile.caption().isEmpty())<span class="tile-caption">${tile.caption()}</span>@endif
</a>
