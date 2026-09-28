@import java.util.List
@import me.hejl.kwatern.view.Pages.MediaTile
@import me.hejl.kwatern.web.Ui
@param Ui ui
@param List<MediaTile> tiles
@param String title
@param int limit = 0
@param boolean compact = false
@if(!tiles.isEmpty())
<section class="card${compact ? " photos-compact" : ""}">
    @if(!title.isEmpty())<h2>${title}</h2>@endif
    !{int shown = limit > 0 && tiles.size() > limit ? limit : tiles.size();}
    <div class="tiles">
    @for(var t : tiles.subList(0, shown))
        @template.part.tile(ui = ui, tile = t)
    @endfor
    </div>
    @if(shown < tiles.size())
    <details class="more-tiles">
        <summary>${ui.t("photos.show_all", tiles.size())}</summary>
        <div class="tiles">
        @for(var t : tiles.subList(shown, tiles.size()))
            @template.part.tile(ui = ui, tile = t)
        @endfor
        </div>
    </details>
    @endif
</section>
@endif
