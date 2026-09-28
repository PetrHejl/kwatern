@import me.hejl.kwatern.view.Pages.DescendantNode
@import me.hejl.kwatern.web.Ui
@param Ui ui
@param DescendantNode node
@param boolean root = false
<li>
    @if(root)
    <span class="d-root">@template.part.avatar(link = node.person())<span class="row-name">${node.person().name()}</span>@if(!node.person().lifespan().isEmpty())<span class="row-sub">${node.person().lifespan()}</span>@endif</span>
    @else
    <span class="d-row"><a class="d-person${node.person().living() ? " living" : ""}" href="${node.person().url()}">@template.part.avatar(link = node.person())<span class="row-name">${node.person().name()}</span><span class="row-sub">${ui.join(node.person().lifespan(), node.relation())}</span></a>
    @if(!node.moreUrl().isEmpty())<a class="d-more" href="${node.moreUrl()}">${node.note()} ›</a>@endif</span>
    @endif
    @if(!node.families().isEmpty())
    <ul>
    @for(var f : node.families())
        <li class="d-marriage"><span class="d-symbol" aria-hidden="true">⚭</span> @if(!f.label().isEmpty())${f.label()} @endif @if(f.partner() != null)<a href="${f.partner().url()}" class="${f.partner().living() ? "living" : ""}">${f.partner().name()}</a> @if(!f.partner().lifespan().isEmpty())<span class="row-sub">${f.partner().lifespan()}</span> @endif@endif · <a href="${f.familyUrl()}">${ui.t("family.page")}</a>
            @if(!f.children().isEmpty())
            <ul>
            @for(var c : f.children())
                @template.part.descendant(ui = ui, node = c)
            @endfor
            </ul>
            @endif
        </li>
    @endfor
    </ul>
    @endif
</li>
