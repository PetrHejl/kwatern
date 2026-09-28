@import java.util.List
@import me.hejl.kwatern.view.Pages.PlaceNode
@import me.hejl.kwatern.web.Ui
@param Ui ui
@param List<PlaceNode> nodes
@param int depth
<ul class="place-tree">
@for(var n : nodes)
    <li>
    @if(n.children().isEmpty())
        <a href="${n.url()}">${n.name()}</a> <span class="muted">${ui.join(n.type(), n.events() > 0 ? ui.t("place.events", n.events()) : "")}</span>
    @else
        <details open="${depth == 0}">
            <summary><a href="${n.url()}">${n.name()}</a> <span class="muted">${ui.join(n.type(), n.events() > 0 ? ui.t("place.events", n.events()) : "", ui.t("places.within", n.children().size()))}</span></summary>
            @template.part.placetree(ui = ui, nodes = n.children(), depth = depth + 1)
        </details>
    @endif
    </li>
@endfor
</ul>
