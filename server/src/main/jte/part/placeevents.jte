@import java.util.List
@import gg.jte.support.ForSupport
@import me.hejl.kwatern.view.Pages.PlaceEvent
@import me.hejl.kwatern.web.Ui
@param Ui ui
@param List<PlaceEvent> events
@for(var e : events)
    <div class="t-year">${e.year()}</div>
    <div class="t-body">
        <div><span class="t-type">${e.type()}</span>@if(!e.who().isEmpty()) ·@for(var w : ForSupport.of(e.who()))@if(!w.isFirst()) ${ui.t("and")}@endif @template.part.person(ui = ui, link = w.get())@endfor@endif</div>
        @if(!e.date().isEmpty() || !e.place().isEmpty())<div class="t-when">${e.date()}@if(!e.date().isEmpty() && !e.place().isEmpty()) · @endif@if(!e.place().isEmpty())<a href="${e.placeUrl()}">${e.place()}</a>@endif</div>@endif
    </div>
@endfor
