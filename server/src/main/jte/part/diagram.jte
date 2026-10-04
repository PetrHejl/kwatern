@import me.hejl.kwatern.view.Pages.Diagram
@import me.hejl.kwatern.web.Ui
@param Ui ui
@param Diagram diagram
<section class="card">
    <h2>${ui.t("family")}</h2>
    <div class="diagram">
        @if(diagram.parents() != null)
        @template.part.parents(ui = ui, parents = diagram.parents())
        @endif
        @for(var p : diagram.otherParents())
        <div class="d-other-parents">
            <div class="d-parents-label">${p.label()} · <a href="${p.url()}">${ui.t("family.page")}</a></div>
            @template.part.parents(ui = ui, parents = p)
        </div>
        @endfor
        @if(diagram.parents() != null || !diagram.otherParents().isEmpty())
        <div class="d-line" aria-hidden="true"></div>
        @endif
        <div class="box self">
            @template.part.avatar(link = diagram.self())
            <span class="box-text"><span class="box-name">${diagram.self().name()}</span>@if(!diagram.self().lifespan().isEmpty())<span class="box-sub">${diagram.self().lifespan()}</span>@endif</span>
        </div>
        @for(var f : diagram.families())
        <div class="d-family">
            <div class="d-label">${f.label()} · <a href="${f.url()}">${ui.t("family.page")}</a></div>
            @template.part.box(ui = ui, link = f.partner(), role = f.partnerRole(), relation = "")
            @if(f.childCount() > 0)
            <div class="d-children-label">${ui.t("children.count", f.childCount())}</div>
            <div class="d-children">
                @for(var c : f.children())
                @template.part.box(ui = ui, link = c.person(), role = "", relation = c.relation())
                @endfor
            </div>
            @if(f.moreChildren() > 0)<a class="more" href="${f.url()}">${ui.t("children.more", f.moreChildren())}</a>@endif
            @endif
        </div>
        @endfor
    </div>
</section>
