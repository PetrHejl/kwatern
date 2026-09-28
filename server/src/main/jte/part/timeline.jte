@import java.util.List
@import me.hejl.kwatern.view.Pages.EventRow
@import me.hejl.kwatern.web.Ui
@param Ui ui
@param List<EventRow> events
<div class="timeline">
@for(var e : events)
    @if(e.relative() != null)
    <div class="t-year family">${e.year()}</div>
    <div class="t-body family"><span class="t-dot" aria-hidden="true"></span> ${e.type()} @template.part.person(ui = ui, link = e.relative())</div>
    @else
    <div class="t-year">${e.year()}</div>
    <div class="t-body">
        <div><span class="t-type">${e.type()}</span>@if(e.partner() != null) ${ui.t("timeline.with")} @template.part.person(ui = ui, link = e.partner())@endif@if(!e.role().isEmpty()) <span class="muted">(${e.role()})</span>@endif</div>
        @if(!e.date().isEmpty() || !e.place().isEmpty())<div class="t-when">${e.date()}@if(!e.date().isEmpty() && !e.place().isEmpty()) · @endif@if(!e.placeUrl().isEmpty())<a href="${e.placeUrl()}">${e.place()}</a>@else${e.place()}@endif</div>@endif
        @if(!e.description().isEmpty())<div class="t-desc">${e.description()}</div>@endif
        @if(!e.sources().isEmpty())
        <details class="sources">
            <summary>${ui.t("sources.count", e.sources().size())}</summary>
            @for(var c : e.sources())
            <dl class="citation">
                <dt>${ui.t("source")}</dt><dd>@if(!c.sourceUrl().isEmpty())<a href="${c.sourceUrl()}">${c.source()}</a>@else${c.source()}@endif</dd>
                @if(!c.page().isEmpty())<dt>${ui.t("page")}</dt><dd>${c.page()}</dd>@endif
                @if(!c.date().isEmpty())<dt>${ui.t("date")}</dt><dd>${c.date()}</dd>@endif
                <dt>${ui.t("confidence")}</dt><dd>${c.confidence()}</dd>
            </dl>
            @endfor
        </details>
        @endif
    </div>
    @endif
@endfor
</div>
